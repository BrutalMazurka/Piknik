package pik.domain.duk.sam;

import epis5.duk.bck.core.sam.Subject;
import epis5.duk.bck.core.sam.UnlockStatus;
import epis5.duk.bck.core.sam.apdu.ApduRequestFactory;
import epis5.duk.bck.core.sam.apdu.ApduResponse;
import epis5.duk.bck.core.sam.apdu.Pin3DataBuilder;
import epis5.ingenico.transit.prot.*;
import epis5.pos.processing.opt.OptBase;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import pik.common.ELogger;
import pik.domain.ingenico.IngenicoReaderDevice;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SamVerifyPinOpt extends OptBase {
    private static final int RESPONSE_TIMEOUT_MILLS = 5_000;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter outputter;
    private SamUnlockOrder order;

    @Inject
    public SamVerifyPinOpt(IngenicoReaderDevice reader, ITransitProtMsgOutputter outputter) {
        this.reader = reader;
        this.outputter = outputter;

        this.order = null;
    }

    public void setOrder(SamUnlockOrder order) {
        this.order = order;
    }

    @Override
    protected void executeTask() {
        if (order == null) {
            setResultError("SamVerifyPinOpt: order is null");
            return;
        }

        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        byte[] data = new Pin3DataBuilder()
                .cardUid(order.getCardDetectedData().getUidBytes())
                .pin3(order.getPin().getBytes())
                .build();

        byte[] dataEncrypted;

        try {
            dataEncrypted = reader.getSamDuk().getSessionCipher().encrypt(data);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }

        byte[] apdu = ApduRequestFactory.verifyPin3(dataEncrypted).getCommandBytes();

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT_EX)
                .samSlot(reader.getSamDuk().getSlotIndex())
                .apduRequest(apdu)
                .subject(Subject.CUSTOMER_CARD.getCode())
                .turnNumber(128)
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, createResponseCallback(deliveredSignal));
        outputter.outputMsg(transitProtMsg);

        boolean deliverSignalOk;
        try {
            deliverSignalOk = deliveredSignal.await(RESPONSE_TIMEOUT_MILLS + 300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            getLogger().warn("SamVerifyPinOpt", e);
            setResultError("SamVerifyPinOpt: " + e.getMessage());
            return;
        }

        if (!deliverSignalOk) {
            setResultError("Chyba při odemčení SAM modulu: čtečka nebo SAM neodpovidá na povel");
            return;
        }

        if (isResultDescriptionNotApplicable()) {
            setResultOk();
        }
    }

    private ITransitProtServiceProcessor createResponseCallback(final CountDownLatch deliveredSignal) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(verify PIN)", getTransitLogger());
                setResultError("SamVerifyPinOpt: invalid response");
                return;
            }

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        System.out.println("=== SamVerifyPinOpt> PIN verification SUCCESS - calling queryUnlockStatusFromCard() ===");

                        // Query the card to get actual unlock status and trigger observable
                        // This prevents the 2-3 minute delay waiting for MasterLoop to poll
                        // IMPORTANT: Don't call setUnlockStatus here - let the query callback do it
                        // to ensure the observable fires when the value changes from the card query
                        queryUnlockStatusFromCard();
                        System.out.println("=== SamVerifyPinOpt> queryUnlockStatusFromCard() returned ===");
                    } else {
                        getTransitLogger().error("SamVerifyPinOpt> ApduResponse status word error!");
                        setResultError("SamVerifyPinOpt: ApduResponse status word error!");
                    }
                } else {
                    getTransitLogger().error("SamVerifyPinOpt> missing APDU_RESPONSE tag!");
                    setResultError("SamVerifyPinOpt: missing APDU_RESPONSE tag!");
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(verify PIN)", getTransitLogger());
                setResultError("SamVerifyPinOpt: invalid response code");
            }

            deliveredSignal.countDown();
        };
    }

    /**
     * Query unlock status directly from SAM card to trigger the observable immediately.
     * This ensures SSE clients receive the updated status without waiting for MasterLoop polling.
     */
    private void queryUnlockStatusFromCard() {
        System.out.println("=== SamVerifyPinOpt> queryUnlockStatusFromCard() called ===");
        getTransitLogger().info("SamVerifyPinOpt> Querying unlock status after successful PIN verification");

        try {
            Payload payload = new PayloadBuilder()
                    .command(Command.SAM_TRANSMIT)
                    .samSlot(reader.getSamDuk().getSlotIndex())
                    .apduRequest(ApduRequestFactory.isUnlocked().getCommandBytes())
                    .build();

            System.out.println("=== SamVerifyPinOpt> Creating TransitProtMsg for unlock status query ===");
            TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(
                    payload,
                    reader.getTransitApp().getSocketAddress(),
                    RESPONSE_TIMEOUT_MILLS,
                    createUnlockStatusQueryCallback()
            );
            System.out.println("=== SamVerifyPinOpt> Calling outputter.outputMsg() ===");
            outputter.outputMsg(transitProtMsg);
            System.out.println("=== SamVerifyPinOpt> outputter.outputMsg() completed ===");
        } catch (Exception e) {
            System.err.println("=== SamVerifyPinOpt> Exception in queryUnlockStatusFromCard: " + e.getMessage());
            e.printStackTrace();
            getTransitLogger().error("SamVerifyPinOpt> Exception in queryUnlockStatusFromCard", e);
        }
    }

    private ITransitProtServiceProcessor createUnlockStatusQueryCallback() {
        return (writtenMsg, incMsg) -> {
            System.out.println("=== SamVerifyPinOpt> CALLBACK INVOKED! ===");
            getTransitLogger().info("SamVerifyPinOpt> Unlock status query callback invoked");

            try {
                if (!ResponseUtils.isValidResponse(incMsg)) {
                    System.out.println("=== SamVerifyPinOpt> Invalid response (non-critical) ===");
                    getTransitLogger().warn("SamVerifyPinOpt> Invalid response for unlock status query (non-critical)");
                    return;
                }

                System.out.println("=== SamVerifyPinOpt> Response is valid ===");
                getTransitLogger().info("SamVerifyPinOpt> Response is valid");

                if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                    System.out.println("=== SamVerifyPinOpt> Response code is OK ===");
                    getTransitLogger().info("SamVerifyPinOpt> Response code is OK");

                    TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                    if (apduRespRec != null) {
                        System.out.println("=== SamVerifyPinOpt> APDU response record found ===");
                        getTransitLogger().info("SamVerifyPinOpt> APDU response record found");

                        ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                        if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                            int unlockStatusCode = jCommons.utils.ByteUtils.toInt(apduResp.getData()[0]);
                            UnlockStatus unlockStatus = UnlockStatus.fromCode(unlockStatusCode);

                            System.out.println("=== SamVerifyPinOpt> Parsed unlock status: " + unlockStatusCode + " -> " + unlockStatus + " ===");
                            getTransitLogger().info("SamVerifyPinOpt> Parsed unlock status code: " + unlockStatusCode + " -> " + unlockStatus);

                            // Update unlock status - this triggers the observable
                            reader.getSamDuk().setUnlockStatus(unlockStatus);
                            System.out.println("=== SamVerifyPinOpt> setUnlockStatus() called with: " + unlockStatus + " ===");
                            getTransitLogger().info("SamVerifyPinOpt> Unlock status refreshed: " + unlockStatus);
                        } else {
                            System.out.println("=== SamVerifyPinOpt> APDU response status check failed ===");
                            getTransitLogger().warn("SamVerifyPinOpt> APDU response status check failed");
                        }
                    } else {
                        System.out.println("=== SamVerifyPinOpt> APDU response record is null ===");
                        getTransitLogger().warn("SamVerifyPinOpt> APDU response record is null");
                    }
                } else {
                    System.out.println("=== SamVerifyPinOpt> Response code is not OK ===");
                    getTransitLogger().warn("SamVerifyPinOpt> Response code is not OK");
                }
            } catch (Exception e) {
                System.err.println("=== SamVerifyPinOpt> Exception in callback: " + e.getMessage() + " ===");
                e.printStackTrace();
                getTransitLogger().error("SamVerifyPinOpt> Exception in unlock status query callback", e);
            }
        };
    }

    private static ILogger getTransitLogger() {
        return LoggerFactory.get(ELogger.INGENICO_TRANSIT);
    }
}
