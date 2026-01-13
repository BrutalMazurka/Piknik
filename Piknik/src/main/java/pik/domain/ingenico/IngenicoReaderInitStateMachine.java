package pik.domain.ingenico;

import epis5.duk.bck.core.sam.SamDukATR;
import epis5.ingenico.transit.init.SamStatusPolling;
import epis5.ingenico.transit.prot.*;
import epis5.ingenico.transit.sam.SamATR;
import epis5.ingenico.transit.sam.SamSlot;
import epis5.ingenico.transit.sam.SamSlotStatus;
import epis5.ingenicoifsf.prot.IIfsfProtMsgOutputter;
import jCommons.comm.protocol.IPeriodicalChecker;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import jCommons.timer.TickCounter;
import pik.common.ELogger;
import pik.domain.ingenico.transit.IngenicoTransitEventArgs;

import javax.inject.Inject;

public class IngenicoReaderInitStateMachine implements IPeriodicalChecker {
    private static final int RESPONSE_TIMEOUT_MILLS = 800;
    private static final int CHECK_PERIOD_MILLS = RESPONSE_TIMEOUT_MILLS + 50;

    private final IngenicoReaderDevice reader;
    private final IIfsfProtMsgOutputter ifsfOutputter;
    private final ITransitProtMsgOutputter transitOutputter;
    private final ILogger ifsfLogger;
    private final ILogger transitLogger;
    private final TickCounter periodTC;

    private boolean transitReadVersionsReceived;
    private final SamStatusPolling samSlotStatusPolling;
    private final SamStatusPolling samAtrPolling;

    @Inject
    public IngenicoReaderInitStateMachine(IngenicoReaderDevice reader, IIfsfProtMsgOutputter ifsfOutputter, ITransitProtMsgOutputter transitOutputter) {
        this.reader = reader;
        this.ifsfOutputter = ifsfOutputter;
        this.transitOutputter = transitOutputter;

        this.ifsfLogger = LoggerFactory.get(ELogger.INGENICO_IFSF);
        this.transitLogger = LoggerFactory.get(ELogger.INGENICO_TRANSIT);
        this.periodTC = TickCounter.instanceFromNow();

        this.samSlotStatusPolling = new SamStatusPolling();
        this.samAtrPolling = new SamStatusPolling();

        localReset();

        this.reader.getTransitApp().getAppAliveChanges().subscribe(this::onTransitAppAliveChanged);
        this.reader.getTransitApp().getTcpConnectionChanges().subscribe(this::onTransitTcpConnectionChanged);

        // Verify logger is working
        logTransit("IngenicoReaderInitStateMachine initialized");
    }

    private void onTransitAppAliveChanged(IngenicoTransitEventArgs ea) {
        if (reader.getTransitApp().isAppAlive()) {
            if (reader.getInitStatus() == EReaderInitState.TRANSIT_APP_ALIVE) {
                makeCheckPeriodElapsed();
            }
        } else {
            //Doslo k restartu ctecky - ctecka muze po 48 hodinach od zapnuti provede automaticky svuj reboot
            //Nebo probehlo TMS
            reader.setExtraDelayOnInit(true);
            reader.setInitState(EReaderInitState.STARTING);
        }
    }

    private void onTransitTcpConnectionChanged(IngenicoTransitEventArgs ea) {
        if (reader.getTransitApp().isConnected() && reader.getInitStatus() == EReaderInitState.TRANSIT_TCP_CONNECTION) {
            makeCheckPeriodElapsed();
        }
    }

    private void makeCheckPeriodElapsed() {
        periodTC.fromNowBackMills(CHECK_PERIOD_MILLS);
    }

    @Override
    public void periodicalCheck() {
        if (reader.isInitStatusDone()) {
            return;
        }

        if (!periodTC.isElapsedMills(CHECK_PERIOD_MILLS)) {
            return;
        }
        periodTC.recordNow();

        onCheckPeriodElapsed();
    }

    private void onCheckPeriodElapsed() {
        EReaderInitState initStatus = reader.getInitStatus();
        switch (initStatus) {
            case STARTING:
                onStarting();
                break;
            case TRANSIT_TCP_CONNECTION:
                checkTransitTcpConnection();
                break;
            case TRANSIT_APP_ALIVE:
                checkTransitAppAlive();
                break;
            case TRANSIT_READING_VERSIONS:
                checkTransitReadVersions();
                break;
            case TRANSIT_SAM_SLOT_POLLING_ATR:
                checkSamAtrPolling();
                break;
            case TRANSIT_SAM_SLOT_POLLING_STATUS:
                checkSamSlotStatusPolling();
                break;
            case IFSF_TCP_CONNECTION:
                checkIfsfTcpConnection();
                break;
            case IFSF_APP_ALIVE:
                checkIfsfAppAlive();
                break;

            //TODO

            case DONE:
                break;
            default:
                logTransitWarn("Unknown EReaderInitState: " + initStatus);
                setInitStateDone();
                break;
        }
    }

    //**************************************************************
    //***************** STARTING ***********************************
    //**************************************************************

    private void onStarting() {
        localReset();

        changeState(EReaderInitState.TRANSIT_TCP_CONNECTION);
    }

    private void localReset() {
        transitReadVersionsReceived = false;

        samSlotStatusPolling.reset(reader.getTransitApp().getSamSlots().getList());
        samAtrPolling.reset(reader.getTransitApp().getSamSlots().getList());

        for (SamSlot samSlot : reader.getTransitApp().getSamSlots().getList()) {
            samSlot.setSlotStatus(SamSlotStatus.UNKNOWN);
            samSlot.setSamAtr(SamATR.NULL_INSTANCE.toByteArray());
        }

        reader.getSamDuk().setSlotStatusToDefault();
        reader.getSamDuk().getAuth().restart();
        // Don't clear foundSamType on auth restart - only clear when SAM is removed
    }

    //**************************************************************
    //********** TRANSIT_TCP_CONNECTION ****************************
    //**************************************************************

    private void checkTransitTcpConnection() {
        if (reader.getTransitApp().isConnected()) {
            changeState(EReaderInitState.TRANSIT_APP_ALIVE);
        }
    }

    //**************************************************************
    //********** TRANSIT_APP_ALIVE *********************************
    //**************************************************************

    private void checkTransitAppAlive() {
        if (reader.getTransitApp().isAppAlive()) {
            changeState(EReaderInitState.TRANSIT_READING_VERSIONS);
        }
    }

    //**************************************************************
    //********** TRANSIT_READING_VERSIONS **************************
    //**************************************************************

    private void checkTransitReadVersions() {
        logTransit("Tx GET_INFO");

        Payload payload = new PayloadBuilder().command(Command.GET_INFO).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, createTransitGetInfoCallback());
        transitOutputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor createTransitGetInfoCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "GET_INFO(reader-init)", transitLogger);
                return;
            }

            if (this.transitReadVersionsReceived) {
                logTransitWarn("GET_INFO response when transitReadVersionsReceived==true");
                return;
            }

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                logTransit("Rx GET_INFO payload: " + incMsg.getPayload().formatForLog());
                this.transitReadVersionsReceived = true;

                //TODO parse and update
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "GET_INFO(reader-init)", transitLogger);
            }

            changeState(EReaderInitState.TRANSIT_SAM_SLOT_POLLING_ATR);
        };
    }

    //**************************************************************
    //********** TRANSIT_SAM_SLOT_POLLING_ATR **********************
    //**************************************************************

    private void checkSamAtrPolling() {
        if (samAtrPolling.isAllReceived()) {
            onSamAttPollingFinished();
            return;
        }

        if (!reader.isExtraDelayElapsed()) {
            //Pokud se ctecka restartne (napr. TMS) a aplikace EVK bezi - zacne ctecka odpovidat na zakladni prikazy (GET_INFO),
            // ale na stav SAM modulu vraci error
            // Aplikace EVK musi dat ctece nejaky cas aby si inicializovala SAM rozhranni
            return;
        }

        int slotIndex = samAtrPolling.getFirstSamSlotIndexWithUnknownStatus();
        SamSlot samSlot = reader.getTransitApp().getSamSlots().getByIndex(slotIndex);
        if (samSlot == null) {
            logTransitError("Tx SAM_CARD_INFO: SamSlot is null, index=" + slotIndex);
            return;
        }

        logTransit("Tx SAM_CARD_INFO, sam_slot_index=" + samSlot.getSlotIndex());

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_CARD_INFO)
                .samSlot(samSlot.getSlotIndex())
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, createTransitGetSamAtrCallback(samSlot.getSlotIndex()));
        transitOutputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor createTransitGetSamAtrCallback(final int slotIndex) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_CARD_INFO(reader-init)", transitLogger);
                return;
            }

            if (samAtrPolling.isReceived(slotIndex)) {
                logTransitWarn("SAM_CARD_INFO response when already received for slotIndex=" + slotIndex);
                return;
            }
            samAtrPolling.markReceived(slotIndex);

            logTransit("Rx SAM_CARD_INFO sam_slot_index=" + slotIndex + ", payload: " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord attRec = incMsg.getPayload().getRecord(TagType.ATR);
                if (attRec != null) {
                    byte[] atrBytes = attRec.getValue();
                    reader.getTransitApp().getSamSlots().setSamAtr(slotIndex, atrBytes);

                    if (SamDukATR.isDukAtr(atrBytes)) {
                        // Check if ATR changed (different SAM inserted)
                        byte[] currentAtr = reader.getSamDuk().getSamAtr().toByteArray();
                        boolean atrChanged = !java.util.Arrays.equals(currentAtr, atrBytes);

                        // Debug: Simple log to verify execution
                        logTransit("DEBUG: SAM detection code executing");

                        try {
                            String foundTypeStr = reader.getFoundSamType() != null ? reader.getFoundSamType().toString() : "null";
                            logTransit(String.format("SAM detected: slot=%d, atrChanged=%b, currentFoundType=%s",
                                slotIndex, atrChanged, foundTypeStr));
                        } catch (Exception e) {
                            logTransitError("Error logging SAM detection: " + e.getMessage());
                        }

                        reader.getSamDuk().setSamDetected(slotIndex, atrBytes);

                        // Only clear found SAM type if ATR changed (different SAM)
                        if (atrChanged) {
                            logTransit("Clearing foundSamType due to ATR change");
                            reader.setFoundSamType(null);
                        }
                    }
                } else {
                    logTransitWarn("Rx payload for SAM_CARD_INFO(reader-init) has missing ATR tag! sam_slot_index=" + slotIndex);
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_CARD_INFO(reader-init)", transitLogger);
            }

            if (samAtrPolling.isAllReceived()) {
                onSamAttPollingFinished();
            }
            makeCheckPeriodElapsed();
        };
    }

    private void onSamAttPollingFinished() {
        if (!reader.getSamDuk().getSamAtr().isDukAtr()) {
            logTransitError("SAM DUK not detected after reading ATR from all slots, setting slot status to: ERROR");
            reader.getSamDuk().setSamDetectionError();
            // Clear found SAM type when SAM is not detected
            logTransit("Clearing foundSamType - SAM not detected");
            reader.setFoundSamType(null);
        }

        changeState(EReaderInitState.TRANSIT_SAM_SLOT_POLLING_STATUS);
    }

    //**************************************************************
    //********** TRANSIT_SAM_SLOT_POLLING_STATUS *******************
    //**************************************************************

    private void checkSamSlotStatusPolling() {
        if (samSlotStatusPolling.isAllReceived()) {
            onSamSlotStatusPollingFinished();
            return;
        }

        int slotIndex = samSlotStatusPolling.getFirstSamSlotIndexWithUnknownStatus();
        SamSlot samSlot = reader.getTransitApp().getSamSlots().getByIndex(slotIndex);
        if (samSlot == null) {
            logTransitError("Tx SAM_GET_STATUS: SamSlot is null, index=" + slotIndex);
            return;
        }

        logTransit("Tx SAM_GET_STATUS, sam_slot_index=" + samSlot.getSlotIndex());

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_GET_STATUS)
                .samSlot(samSlot.getSlotIndex())
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, createTransitGetSamStatusCallback(samSlot.getSlotIndex()));
        transitOutputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor createTransitGetSamStatusCallback(final int slotIndex) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_GET_STATUS(reader-init)", transitLogger);
                return;
            }

            if (samSlotStatusPolling.isReceived(slotIndex)) {
                logTransitWarn("SAM_GET_STATUS response when already received for slotIndex=" + slotIndex);
                return;
            }
            samSlotStatusPolling.markReceived(slotIndex);

            logTransit("Rx SAM_GET_STATUS sam_slot_index=" + slotIndex + ", payload: " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord samSlotStatusRec = incMsg.getPayload().getRecord(TagType.SAM_SLOT_STATUS);
                if (samSlotStatusRec != null) {
                    SamSlotStatus samSlotStatus = SamSlotStatus.fromCode(samSlotStatusRec.getValueAsInt());
                    reader.getTransitApp().getSamSlots().setSlotStatus(slotIndex, samSlotStatus);


                } else {
                    logTransitWarn("Rx payload for SAM_GET_STATUS(reader-init) has missing SAM_SLOT_STATUS tag! sam_slot_index=" + slotIndex);
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_GET_STATUS(reader-init)", transitLogger);
            }

            if (samSlotStatusPolling.isAllReceived()) {
                onSamSlotStatusPollingFinished();
            }
            makeCheckPeriodElapsed();
        };
    }

    private void onSamSlotStatusPollingFinished() {
        changeState(EReaderInitState.IFSF_TCP_CONNECTION);
    }

    //**************************************************************
    //********** IFSF_TCP_CONNECTION ****************************
    //**************************************************************

    private void checkIfsfTcpConnection() {
        if (reader.getIfsfApp().isConnected()) {
            changeState(EReaderInitState.IFSF_APP_ALIVE);
        }
    }

    //**************************************************************
    //********** IFSF_APP_ALIVE *********************************
    //**************************************************************

    private void checkIfsfAppAlive() {
        if (reader.getIfsfApp().isAppAlive()) {
            setInitStateDone();
        }
    }

    //**************************************************************
    //**************************************************************
    //**************************************************************

    private void setInitStateDone() {
        changeState(EReaderInitState.DONE);
    }

    private void changeState(EReaderInitState newState) {
        reader.setInitState(newState);
        periodTC.fromNowBackMills(CHECK_PERIOD_MILLS);
    }

    private void logTransit(String msg) {
        transitLogger.info("ReaderInit> " + msg);
    }

    private void logTransitWarn(String msg) {
        transitLogger.warn("ReaderInit> " + msg);
    }

    private void logTransitError(String msg) {
        transitLogger.error("ReaderInit> " + msg);
    }
}
