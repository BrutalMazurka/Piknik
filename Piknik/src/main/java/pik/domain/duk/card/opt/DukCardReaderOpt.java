package pik.domain.duk.card.opt;

import epis5.duk.bck.core.card.CardDuk;
import epis5.duk.bck.core.card.files.CardHolderInfo;
import epis5.duk.bck.core.card.files.CardInfo;
import epis5.duk.bck.core.card.files.SeasonTicket;
import epis5.duk.bck.core.card.files.ValueEP;
import epis5.duk.bck.core.sam.SamDuk;
import epis5.duk.bck.core.sam.Subject;
import epis5.duk.bck.core.sam.apdu.ApduRequestFactory;
import epis5.duk.bck.core.sam.apdu.ApduResponse;
import epis5.duk.bck.core.sam.apdu.CardInfoDataBuilder;
import epis5.duk.bck.core.sam.apdu.ReadFileDataBuilder;
import epis5.ingenico.transit.prot.*;
import epis5.pos.processing.opt.OptBase;
import jCommons.ByteArrayFormatter;
import pik.domain.ingenico.CardDetectedData;
import pik.domain.ingenico.IngenicoReaderDevice;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.inject.Inject;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Operation for reading DUK card data.
 * Ported from EVK project.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class DukCardReaderOpt extends OptBase {
    public enum ReadSchema {
        EP_PAYMENT,
        CARD_INFO,
    }

    private enum DukCardFile {
        CARD_INFO,
        CARD_HOLDER_INFO,
        VALUE_EP,
        TICKETS,
    }

    private static final int RESPONSE_TIMEOUT_MILLIS = 1_500;
    private static final boolean DEBUG_DUK_CARD_FILE_DATA = true;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter outputter;
    private final CardDuk.Builder cardDukBuilder;
    private ReadSchema readSchema;
    private CardDetectedData cardDetectedData;
    private EnumSet<DukCardFile> filesToRead;

    @Inject
    public DukCardReaderOpt(IngenicoReaderDevice reader, ITransitProtMsgOutputter outputter) {
        this.reader = reader;
        this.outputter = outputter;
        this.cardDukBuilder = CardDuk.newBuilder();
        this.readSchema = ReadSchema.CARD_INFO;
        this.cardDetectedData = null;
        this.filesToRead = EnumSet.noneOf(DukCardFile.class);
    }

    public void setParams(ReadSchema readSchema, CardDetectedData cardDetectedData) {
        this.readSchema = readSchema;
        this.cardDetectedData = cardDetectedData;
    }

    public CardDuk getCardDuk() {
        return cardDukBuilder.build();
    }

    @Override
    protected void executeTask() {
        if (!verifyParams()) {
            return;
        }
        initTask();

        for (DukCardFile file : DukCardFile.values()) {
            if (!isReadingWithoutError()) {
                return;
            }

            if (!filesToRead.contains(file)) {
                continue;
            }

            switch (file) {
                case CARD_INFO:
                    readCardInfo();
                    break;
                case CARD_HOLDER_INFO:
                    readCardHolderInfo();
                    break;
                case VALUE_EP:
                    readValueEp();
                    break;
                case TICKETS:
                    readTickets();
                    break;
                default:
                    setResultError("DukCardReaderOpt: unknown file: " + file);
                    return;
            }
        }

        if (isReadingWithoutError()) {
            setResultOk();
        }
    }

    private boolean verifyParams() {
        if (cardDetectedData == null) {
            setResultError("DukCardReaderOpt: cardDetectedData is null");
            return false;
        }
        return true;
    }

    private void initTask() {
        cardDukBuilder.cardUid(cardDetectedData.getUidBytes());

        filesToRead = EnumSet.noneOf(DukCardFile.class);

        if (readSchema == ReadSchema.EP_PAYMENT) {
            filesToRead.add(DukCardFile.CARD_INFO);
            filesToRead.add(DukCardFile.VALUE_EP);
        } else {
            filesToRead = EnumSet.allOf(DukCardFile.class);
        }

        if (DEBUG_DUK_CARD_FILE_DATA) {
            getLogger().info("DukCardReaderOpt> readSchema: " + readSchema);
            getLogger().info("DukCardReaderOpt> filesToRead: " + filesToRead);
            getLogger().info("DukCardReaderOpt> card UID: " + ByteArrayFormatter.toHex(cardDetectedData.getUidBytes()));
        }
    }

    private boolean isReadingWithoutError() {
        return isResultDescriptionNotApplicable();
    }

    private SamDuk getSamDuk() {
        return reader.getSamDuk();
    }

    private void readCardInfo() {
        byte[] data = new CardInfoDataBuilder()
                .cardUid(cardDetectedData.getUidBytes())
                .build();

        byte[] dataEncrypted = encryptData(data);
        if (dataEncrypted == null) {
            return;
        }

        byte[] apdu = ApduRequestFactory.readCardInfoFile(dataEncrypted).getCommandBytes();

        readFile(apdu, DukCardFile.CARD_INFO);
    }

    private void readCardHolderInfo() {
        byte[] data = new ReadFileDataBuilder()
                .cardUid(cardDetectedData.getUidBytes())
                .build();

        byte[] dataEncrypted = encryptData(data);
        if (dataEncrypted == null) {
            return;
        }

        byte[] apdu = ApduRequestFactory.readCardHolderInfoFile(dataEncrypted).getCommandBytes();

        readFile(apdu, DukCardFile.CARD_HOLDER_INFO);
    }

    private void readValueEp() {
        byte[] data = new ReadFileDataBuilder()
                .cardUid(cardDetectedData.getUidBytes())
                .build();

        byte[] dataEncrypted = encryptData(data);
        if (dataEncrypted == null) {
            return;
        }

        byte[] apdu = ApduRequestFactory.readValueEPFile(dataEncrypted).getCommandBytes();

        readFile(apdu, DukCardFile.VALUE_EP);
    }

    private void readTickets() {
        byte[] data = new ReadFileDataBuilder()
                .cardUid(cardDetectedData.getUidBytes())
                .build();

        byte[] dataEncrypted = encryptData(data);
        if (dataEncrypted == null) {
            return;
        }

        byte[] apdu = ApduRequestFactory.readAllTickets(dataEncrypted).getCommandBytes();

        readFile(apdu, DukCardFile.TICKETS);
    }

    private byte[] encryptData(byte[] data) {
        try {
            return getSamDuk().getSessionCipher().encrypt(data);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            getLogger().error("DukCardReaderOpt> error encrypting card read data: " + e.getMessage(), e);
            setCardReadingGeneralError();
            return null;
        }
    }

    private void readFile(byte[] apdu, DukCardFile file) {
        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT_EX)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(apdu)
                .subject(Subject.CUSTOMER_CARD.getCode())
                .turnNumber(SamDuk.READER_DEFAULT_TURN_NUMBER)
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(
                payload,
                reader.getTransitApp().getSocketAddress(),
                RESPONSE_TIMEOUT_MILLIS,
                createResponseCallback(file, deliveredSignal)
        );
        outputter.outputMsg(transitProtMsg);

        try {
            if (!deliveredSignal.await(RESPONSE_TIMEOUT_MILLIS + 300, TimeUnit.MILLISECONDS)) {
                getLogger().error("DukCardReaderOpt> response timeout for file: " + file);
                setCardReadingGeneralError();
            }
        } catch (InterruptedException e) {
            getLogger().warn("DukCardReaderOpt interrupted", e);
            setCardReadingGeneralError();
        }
    }

    private ITransitProtServiceProcessor createResponseCallback(final DukCardFile file, final CountDownLatch deliveredSignal) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(reading file " + file + ")", getLogger());
                setCardReadingGeneralError();
                return;
            }

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        byte[] encryptedData = apduResp.getData();
                        try {
                            byte[] fileData = reader.getSamDuk().getSessionCipher().decrypt(encryptedData);

                            if (DEBUG_DUK_CARD_FILE_DATA) {
                                getLogger().info("DukCardReaderOpt> file: " + file + ", data: " + ByteArrayFormatter.toHex(fileData));
                            }

                            switch (file) {
                                case CARD_INFO:
                                    cardDukBuilder.cardInfo(CardInfo.parseFromFile(fileData));
                                    break;

                                case CARD_HOLDER_INFO:
                                    cardDukBuilder.cardHolderInfo(CardHolderInfo.parseFromFile(fileData));
                                    break;

                                case VALUE_EP:
                                    cardDukBuilder.valueEP(ValueEP.parseFromFile(fileData));
                                    break;

                                case TICKETS:
                                    cardDukBuilder.tickets(SeasonTicket.parseAllValidTickets(fileData));
                                    break;
                            }

                        } catch (IllegalBlockSizeException | BadPaddingException e) {
                            getLogger().error("DukCardReaderOpt> error decrypting file data: " + e.getMessage(), e);
                            setCardReadingGeneralError();
                        }
                    } else {
                        getLogger().error("DukCardReaderOpt> ApduResponse status word error! File=" + file);
                        setCardReadingGeneralError();
                    }
                } else {
                    getLogger().error("DukCardReaderOpt> missing APDU_RESPONSE tag! File=" + file);
                    setCardReadingGeneralError();
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(DukCardReaderOpt)-" + file, getLogger());
                setCardReadingGeneralError();
            }

            deliveredSignal.countDown();
        };
    }

    private void setCardReadingGeneralError() {
        setResultError("Error reading DUK card");
    }
}