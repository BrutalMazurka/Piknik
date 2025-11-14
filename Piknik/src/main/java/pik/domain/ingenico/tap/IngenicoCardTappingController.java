package pik.domain.ingenico.tap;

import epis5.ingenico.transit.TerminalStatus;
import epis5.ingenico.transit.prot.*;
import pik.common.ELogger;
import pik.domain.ingenico.CardDetectedData;
import pik.domain.ingenico.IngenicoReaderDevice;
import jCommons.AttemptMonitor;
import jCommons.comm.protocol.IPeriodicalChecker;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IngenicoCardTappingController implements IPeriodicalChecker {
    private final IngenicoReaderDevice readerDevice;
    private final IngenicoCardTappingState cardTappingState;
    private final ITransitProtMsgOutputter outputter;
    private final ILogger logger;

    @Inject
    public IngenicoCardTappingController(IngenicoReaderDevice readerDevice, IngenicoCardTappingState cardTappingState, ITransitProtMsgOutputter outputter) {
        this.readerDevice = readerDevice;
        this.cardTappingState = cardTappingState;
        this.outputter = outputter;

        this.logger = LoggerFactory.get(ELogger.INGENICO_TRANSIT);

        this.cardTappingState.getApiTapRequestChanges().subscribe(this::onApiTapRequestChanges);
        this.cardTappingState.getTapSessionStateChanges().subscribe(this::onSessionStateChanged);
    }

    private void onApiTapRequestChanges(CardTappingEventArgs evArgs) {
        if (logger.isDebugEnabled()) {
            logger.debug("IngenicoCardTappingController - " + cardTappingState.getApiRequestAsLogMsg());
        }
    }

    private void onSessionStateChanged(CardTappingEventArgs evArgs) {
        if (logger.isDebugEnabled()) {
            logger.debug("IngenicoCardTappingController - " + cardTappingState.getSessionStateAsLogMsg());
        }
    }

    @Override
    public void periodicalCheck() {
        if (!readerDevice.isInitStatusDone()) {
            return;
        }

        checkCardTappingSession();
    }

    private void checkCardTappingSession() {
        ECardTapSessionState state = cardTappingState.getSessionState();

        if (isConditionForAbort(state)) {
            startStoppingState();
            return;
        }

        switch (state) {
            case INACTIVE:
                checkLedDiodesOffFlagWhileInactiveState();

                if (isConditionForActiveTappingMode()) {
                    cardTappingState.setSessionState(ECardTapSessionState.STARTING_CHECKING_STATUS_1);
                }
                break;

            case STARTING_CHECKING_STATUS_1:
                checkStartingCheckingStatus(state);
                break;
            case STARTING_BACK_TO_IDLE:
                checkStartingBackToIdle(state);
                break;
            case STARTING_CHECKING_STATUS_2:
                checkStartingCheckingStatus(state);
                break;
            case STARTING_LED_ON:
                checkStartingLedOn(state);
                break;

            case STARTED:
                checkCardReading(state);
                break;

            case STOPPING_LED_OFF:
                checkStartingLedOff(state);
                break;
        }
    }

    private boolean isConditionForAbort(ECardTapSessionState state) {
        return state != ECardTapSessionState.INACTIVE && !ECardTapSessionState.isStoppingState(state) && !isConditionForActiveTappingMode();
    }

    private boolean isConditionForActiveTappingMode() {
        if (!cardTappingState.isValidCardTapRequest()) {
            return false;
        }

        if (!readerDevice.isInitStatusDone()) {
            return false;
        }

        if (!readerDevice.getSamDuk().getAuth().isProcessStateFinished()) {
            return false;
        }

        if (!readerDevice.getTransitApp().isConnectedAndAppAlive()) {
            return false;
        }

        cardTappingState.checkMaintenanceModeTimeout();
        if (cardTappingState.isMaintenanceMode()) {
            return false;
        }

        return true;
    }

    private void startStoppingState() {
        cardTappingState.setSessionState(ECardTapSessionState.STOPPING_LED_OFF);
    }

    private void checkStartingCheckingStatus(ECardTapSessionState state) {
        AttemptMonitor attemptMonitor = cardTappingState.getAttemptMonitor(state);
        if (attemptMonitor == null) {
            logAttemptMonitorNull(state);
            return;
        }

        if (!attemptMonitor.isTimeForNextAttempt()) {
            return;
        }

        if (!attemptMonitor.isNextAttemptPossible()) {
            logger.warn("IngenicoCardTappingController: could not read transit status (max. attempts expired - no response received), state=" + state);
            cardTappingState.setSessionState(ECardTapSessionState.INACTIVE);
            return;
        }
        attemptMonitor.recordAttemptNow();

        Payload payload = new PayloadBuilder().command(Command.GET_STATE).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), 500, getGetStateProcessor(state));
        outputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor getGetStateProcessor(final ECardTapSessionState state) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.GET_STATE.toString(), logger);
                return;
            }

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), Command.GET_STATE.toString(), logger);
                return;
            }

            TlvRecord statusRec = incMsg.getPayload().getRecord(TagType.STATUS);
            if (statusRec == null) {
                logger.warn("Rx payload for " + Command.GET_STATE + " has missing STATUS tag!");
                return;
            }

            int statusAsInt = statusRec.getValueAsIntLittleEndian();
            readerDevice.getTransitApp().setTerminalStatusCode(statusAsInt);
            TerminalStatus terminalStatus = TerminalStatus.fromCode(statusAsInt);

            if (cardTappingState.getSessionState() != state) {
                logger.warn("IngenicoCardTappingController.getGetStateProcessor: received response for wrong session " +
                        "callback-state: " + state +
                        ", received status: " + terminalStatus +
                        ", current-state: " + cardTappingState.getSessionState());
                return;
            }

            if (terminalStatus == TerminalStatus.IDLE) {
                cardTappingState.setSessionState(ECardTapSessionState.STARTING_LED_ON);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("IngenicoCardTappingController.getGetStateProcessor: terminalStatus=" + terminalStatus + " while starting card tapping, state = " + state);
                }

                if (cardTappingState.getSessionState() == ECardTapSessionState.STARTING_CHECKING_STATUS_1) {
                    //Terminal neni pripraven na tapovani (prevod jej do stavu IDLE)
                    cardTappingState.setSessionState(ECardTapSessionState.STARTING_BACK_TO_IDLE);
                }
            }
        };
    }

    private void checkStartingBackToIdle(ECardTapSessionState state) {
        AttemptMonitor attemptMonitor = cardTappingState.getAttemptMonitor(state);
        if (attemptMonitor == null) {
            logAttemptMonitorNull(state);
            return;
        }

        if (!attemptMonitor.isTimeForNextAttempt()) {
            return;
        }

        if (!attemptMonitor.isNextAttemptPossible()) {
            logger.warn("IngenicoCardTappingController: could send back to idle (max. attempts expired - no response received), state=" + state);
            cardTappingState.setSessionState(ECardTapSessionState.INACTIVE);
            return;
        }
        attemptMonitor.recordAttemptNow();

        Payload payload = new PayloadBuilder().command(Command.BACK_TO_IDLE).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), 500, getBackToIdleProcessor(state));
        outputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor getBackToIdleProcessor(final ECardTapSessionState state) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.BACK_TO_IDLE.toString(), logger);
                return;
            }

            //neznam spravne navratove hodnoty v response code
            //napr. pokud se zasle back to idle ve stavu IDLE je vracno: resp=ERR_CMD_EXEC(0xF4), msgId=120, payload=[RESPONSE_CODE: F4 ]

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), Command.BACK_TO_IDLE.toString(), logger);
            }

            if (cardTappingState.getSessionState() != state) {
                logWrongSessionInCallback("getBackToIdleProcessor", state);
                return;
            }

            cardTappingState.setSessionState(ECardTapSessionState.STARTING_CHECKING_STATUS_2);
        };
    }

    private void logWrongSessionInCallback(String callbackName, ECardTapSessionState state) {
        logger.warn("IngenicoCardTappingController." + callbackName + ": received response for wrong session " +
                "callback-state: " + state +
                ", current-state: " + cardTappingState.getSessionState());
    }

    private void checkStartingLedOn(ECardTapSessionState state) {
        AttemptMonitor attemptMonitor = cardTappingState.getAttemptMonitor(state);
        if (attemptMonitor == null) {
            logAttemptMonitorNull(state);
            return;
        }

        if (!attemptMonitor.isTimeForNextAttempt()) {
            return;
        }

        if (!attemptMonitor.isNextAttemptPossible()) {
            logger.warn("IngenicoCardTappingController: could not set LED ON (max. attempts expired - no response received), state=" + state);
            cardTappingState.setSessionState(ECardTapSessionState.STARTED);
            return;
        }
        attemptMonitor.recordAttemptNow();

        Payload payload = new PayloadBuilder()
                .command(Command.LED_DI0D_STATE)
                .ledNumber(0x01)
                .ledOperation(LedOperation.ON)
                .targetPinpad()
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), 500, getLedOnProcessor(state));
        outputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor getLedOnProcessor(final ECardTapSessionState state) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.LED_DI0D_STATE.toString(), logger);
                return;
            }

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), Command.LED_DI0D_STATE.toString(), logger);
                return;
            }

            if (cardTappingState.getSessionState() != state) {
                logWrongSessionInCallback("getLedOnProcessor", state);
                return;
            }

            cardTappingState.setSessionState(ECardTapSessionState.STARTED);
        };
    }

    private void checkCardReading(ECardTapSessionState state) {
        if (!cardTappingState.isTimeForCardInfo()) {
            return;
        }
        cardTappingState.recordCardInfoAttempt();

        Payload payload = new PayloadBuilder().command(Command.CL_CARD_INFO).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), IngenicoCardTappingState.CARD_INFO_RESPONSE_TIMEOUT_MILLS, getCardInfoProcessor(state));
        outputter.outputMsg(transitProtMsg);
    }

    private ITransitProtServiceProcessor getCardInfoProcessor(final ECardTapSessionState state) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.CL_CARD_INFO.toString(), logger);
                return;
            }

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), Command.CL_CARD_INFO.toString(), logger);
                return;
            }

            if (cardTappingState.getSessionState() != state) {
                logWrongSessionInCallback("getCardInfoProcessor", state);
                return;
            }

            TlvRecord uidRec = incMsg.getPayload().getRecord(TagType.UID);
            if (uidRec == null) {
                //zadna karta v poli - odesli dalsi prikaz CL_CARD_INFO
                cardTappingState.requestNextCardInfoOnCardNotDetected();
                return;
            }

            TlvRecord cardTypeRec = incMsg.getPayload().getRecord(TagType.CARD_TYPE);
            if (cardTypeRec == null) {
                ResponseUtils.logMissingTag(Command.CL_CARD_INFO, TagType.CARD_TYPE, logger);
                return;
            }

            TlvRecord responseRec = incMsg.getPayload().getRecord(TagType.RESPONSE_CODE);
            ResponseCode responseCode = ResponseCode.findByCode(responseRec.getValueAsInt());

            cardTappingState.setCardDetectedInField();

            boolean isSessionActive = state == ECardTapSessionState.STARTED || ECardTapSessionState.isStartingState(state);

            if (cardTappingState.isValidCardTapRequest()) {
                CardTappingRequest cardTappingRequest = cardTappingState.getCardTappingRequest();
                if (isSessionActive) {
                    CardDetectedData cardDetectedData = new CardDetectedData(responseCode,
                            CardType.findByCode(cardTypeRec.getValueAsInt()),
                            uidRec.getValue());

                    cardTappingRequest.getCallback().onCardTapDetected(cardDetectedData);
                }
            } else {
                logger.warn("IngenicoCardTappingController: CL_CARD_INFO detected card while no callback registered!");
                if (isSessionActive) {
                    startStoppingState();
                }
            }

        };
    }

    private void checkStartingLedOff(ECardTapSessionState state) {
        if (!cardTappingState.isSetLedDiodesOffOnStoppingFlag()) {
            cardTappingState.setSessionState(ECardTapSessionState.INACTIVE);
            return;
        }

        AttemptMonitor attemptMonitor = cardTappingState.getAttemptMonitor(state);
        if (attemptMonitor == null) {
            logAttemptMonitorNull(state);
            return;
        }

        if (!attemptMonitor.isTimeForNextAttempt()) {
            return;
        }

        if (!attemptMonitor.isNextAttemptPossible()) {
            logger.warn("IngenicoCardTappingController: could not set LED OFF (max. attempts expired - no response received), state=" + state);
            cardTappingState.setSessionState(ECardTapSessionState.INACTIVE);
            return;
        }
        attemptMonitor.recordAttemptNow();

        Payload payload = createLedOffPayload();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), 500, getLedOffProcessor(state));
        outputter.outputMsg(transitProtMsg);
    }

    private static Payload createLedOffPayload() {
        Payload payload = new PayloadBuilder()
                .command(Command.LED_DI0D_STATE)
                .ledNumber(0x0F)
                .ledOperation(LedOperation.OFF)
                .targetPinpad()
                .build();
        return payload;
    }

    private ITransitProtServiceProcessor getLedOffProcessor(final ECardTapSessionState state) {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.LED_DI0D_STATE.toString(), logger);
                return;
            }

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), Command.LED_DI0D_STATE.toString(), logger);
                return;
            }

            if (cardTappingState.getSessionState() != state) {
                logWrongSessionInCallback("getLedOffProcessor", state);
                return;
            }

            cardTappingState.setSessionState(ECardTapSessionState.INACTIVE);
        };
    }

    private void checkLedDiodesOffFlagWhileInactiveState() {
        if (!cardTappingState.isSetLedDiodesOffFlagWhileInactiveState()) {
            return;
        }

        if (!readerDevice.isInitStatusDone()) {
            return;
        }

        if (!readerDevice.getTransitApp().isConnectedAndAppAlive()) {
            return;
        }

        cardTappingState.clearSetLedDiodesOffFlagWhileInactiveState();

        Payload payload = createLedOffPayload();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, readerDevice.getTransitApp().getSocketAddress(), 500,
                (writtenMsg, incMsg) -> {
                });
        outputter.outputMsg(transitProtMsg);
    }

    private void logAttemptMonitorNull(ECardTapSessionState state) {
        logger.error("IngenicoCardTappingController.checkStartingCheckingStatus: attemptMonitor == null for state: " + state);
    }
}
