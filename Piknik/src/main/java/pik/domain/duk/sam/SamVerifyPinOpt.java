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
            setResultError("Chyba při demčení SAM modulu: čtečka nebo SAM neodpovidá na povel");
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
                        reader.getSamDuk().setUnlockStatus(UnlockStatus.COMPLETED);
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

    private static ILogger getTransitLogger() {
        return LoggerFactory.get(ELogger.INGENICO_TRANSIT);
    }
}
