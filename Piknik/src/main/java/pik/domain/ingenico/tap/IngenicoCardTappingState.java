package pik.domain.ingenico.tap;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import jCommons.AttemptMonitor;
import jCommons.timer.TickCounter;

public class IngenicoCardTappingState implements ICardTapping {
    private static final int CARD_INFO_READING_PERIOD_MILLS = 100;
    private static final int CARD_INFO_INTERNAL_TIMEOUT_MILLS = 1500;
    protected static final int CARD_INFO_RESPONSE_TIMEOUT_MILLS = CARD_INFO_INTERNAL_TIMEOUT_MILLS - 50;
    private static final int MAINTENANCE_MODE_TIMEOUT_MIN = 20;

    private final PublishSubject<CardTappingEventArgs> eventBus;

    private ECardTapSessionState sessionState;
    private final TickCounter lastStateChangedTc;
    private CardTappingRequest cardTappingRequest;
    private final AttemptMonitor startingStatus1AttemptMonitor;
    private final AttemptMonitor startingBackToIdleAttemptMonitor;
    private final AttemptMonitor startingStatus2AttemptMonitor;
    private final AttemptMonitor setLedsAttemptMonitor;
    private boolean setLedDiodesOffOnStoppingFlag;
    private boolean setLedDiodesOffFlagWhileInactiveState;
    private final TickCounter cardInfoTc;
    private boolean maintenanceMode;
    private final TickCounter maintenanceModeTc;

    public IngenicoCardTappingState() {
        this.setLedDiodesOffOnStoppingFlag = true;
        this.setLedDiodesOffFlagWhileInactiveState = false;
        this.sessionState = ECardTapSessionState.INACTIVE;
        this.lastStateChangedTc = TickCounter.instanceFromNow();
        this.cardTappingRequest = CardTappingRequest.NULL_INSTANCE;
        this.maintenanceMode = false;
        this.cardInfoTc = TickCounter.instanceFromNow();
        this.maintenanceModeTc = TickCounter.instanceFromNow();

        this.eventBus = PublishSubject.create();

        this.startingStatus1AttemptMonitor = new AttemptMonitor(350, 2);
        this.startingBackToIdleAttemptMonitor = new AttemptMonitor(500, 2);
        this.startingStatus2AttemptMonitor = new AttemptMonitor(250, 5);
        this.setLedsAttemptMonitor = new AttemptMonitor(250, 2);
    }

    protected Observable<CardTappingEventArgs> getApiTapRequestChanges() {
        return eventBus.filter(ea -> ea.getType() == CardTappingEventArgs.EType.API_TAP_REQUEST);
    }

    protected Observable<CardTappingEventArgs> getTapSessionStateChanges() {
        return eventBus.filter(ea -> ea.getType() == CardTappingEventArgs.EType.TAP_SESSION_STATE);
    }

    @Override
    public void start(CardTappingRequest request) {
        if (request == null) {
            request = CardTappingRequest.NULL_INSTANCE;
        }

        if (this.cardTappingRequest.getSource() != request.getSource()) {
            this.cardTappingRequest = request;
            eventBus.onNext(newEventArgs(CardTappingEventArgs.EType.API_TAP_REQUEST));
        }
    }

    @Override
    public void stop(boolean setLedDiodesOff) {
        if (isValidCardTapRequest()) {
            this.setLedDiodesOffOnStoppingFlag = setLedDiodesOff;
            this.cardTappingRequest = CardTappingRequest.NULL_INSTANCE;
            eventBus.onNext(newEventArgs(CardTappingEventArgs.EType.API_TAP_REQUEST));
        }
    }

    @Override
    public void setLedDiodesOffIfInactive() {
        if (sessionState == ECardTapSessionState.INACTIVE) {
            this.setLedDiodesOffFlagWhileInactiveState = true;
        }
    }

    protected boolean isValidCardTapRequest() {
        return cardTappingRequest != null && !cardTappingRequest.isNull();
    }

    protected CardTappingRequest getCardTappingRequest() {
        return cardTappingRequest;
    }

    protected ECardTapSessionState getSessionState() {
        return sessionState;
    }

    protected void setSessionState(ECardTapSessionState sessionState) {
        if (this.sessionState != sessionState) {
            this.lastStateChangedTc.recordNow();

            AttemptMonitor attemptMonitor = getAttemptMonitor(sessionState);
            if (attemptMonitor != null) {
                attemptMonitor.reset();
            }

            if (sessionState != ECardTapSessionState.INACTIVE) {
                this.setLedDiodesOffFlagWhileInactiveState = false;
            }

            if (sessionState == ECardTapSessionState.INACTIVE) {
                this.setLedDiodesOffOnStoppingFlag = false;
            } else if (sessionState == ECardTapSessionState.STARTED) {
                cardInfoTc.fromNowBackMills(CARD_INFO_INTERNAL_TIMEOUT_MILLS);
            }

            this.sessionState = sessionState;
            eventBus.onNext(newEventArgs(CardTappingEventArgs.EType.TAP_SESSION_STATE));
        }
    }

    protected AttemptMonitor getAttemptMonitor(ECardTapSessionState state) {
        switch (state) {
            case STARTING_CHECKING_STATUS_1:
                return startingStatus1AttemptMonitor;
            case STARTING_BACK_TO_IDLE:
                return startingBackToIdleAttemptMonitor;
            case STARTING_CHECKING_STATUS_2:
                return startingStatus2AttemptMonitor;
            case STARTING_LED_ON:
            case STOPPING_LED_OFF:
                return setLedsAttemptMonitor;
            default:
                return null;
        }
    }

    public boolean isTimeForCardInfo() {
        return cardInfoTc.isElapsedMills(CARD_INFO_INTERNAL_TIMEOUT_MILLS);
    }

    public void recordCardInfoAttempt() {
        cardInfoTc.recordNow();
    }

    public void requestNextCardInfoOnCardNotDetected() {
        cardInfoTc.fromNowBackMills(CARD_INFO_INTERNAL_TIMEOUT_MILLS - CARD_INFO_READING_PERIOD_MILLS);
    }

    public void setCardDetectedInField() {
        //zaruci ze karta bude zpracovana bez toho aniz by do toho byl odesilan pozadavek na stav karty
        cardInfoTc.recordNow();
    }

    @Override
    public void setMaintenanceMode(boolean on) {
        if (this.maintenanceMode != on) {
            if (on) {
                this.setLedDiodesOffOnStoppingFlag = true;
            }
            maintenanceModeTc.recordNow();
            this.maintenanceMode = on;
            eventBus.onNext(newEventArgs(CardTappingEventArgs.EType.MAINTENANCE_MODE));
        }
    }

    public boolean isSetLedDiodesOffOnStoppingFlag() {
        return setLedDiodesOffOnStoppingFlag;
    }

    public boolean isSetLedDiodesOffFlagWhileInactiveState() {
        return setLedDiodesOffFlagWhileInactiveState;
    }

    public void clearSetLedDiodesOffFlagWhileInactiveState() {
        this.setLedDiodesOffFlagWhileInactiveState = false;
    }

    protected boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    protected void checkMaintenanceModeTimeout() {
        if (maintenanceMode && maintenanceModeTc.isElapsedMin(MAINTENANCE_MODE_TIMEOUT_MIN)) {
            setMaintenanceMode(false);
        }
    }

    private CardTappingEventArgs newEventArgs(CardTappingEventArgs.EType type) {
        return new CardTappingEventArgs(type, this);
    }

    protected String getApiRequestAsLogMsg() {
        StringBuilder sb = new StringBuilder();
        sb.append("IngenicoCardTappingState.cardTappingRequest: ");
        CardTappingRequest req = cardTappingRequest;
        if (!req.isNull()) {
            sb.append("active, src=").append(req.getSource().toString().toLowerCase());
            sb.append(" (card tapping start request)");
        } else {
            sb.append("none (card tapping stop request)");
        }
        return sb.toString();
    }

    protected String getSessionStateAsLogMsg() {
        String flags = "";
        if (sessionState == ECardTapSessionState.STOPPING_LED_OFF) {
            flags = " (led_diod_off_flag=" + (setLedDiodesOffOnStoppingFlag ? "1" : "0") + ")";
        }
        return "IngenicoCardTappingState.sessionState = " + sessionState.toString().toLowerCase() + flags;
    }
}
