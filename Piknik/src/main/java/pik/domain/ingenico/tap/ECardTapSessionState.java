package pik.domain.ingenico.tap;

public enum ECardTapSessionState {
    INACTIVE,
    STARTING_CHECKING_STATUS_1,
    STARTING_BACK_TO_IDLE,
    STARTING_CHECKING_STATUS_2,
    STARTING_LED_ON,
    STARTED,
    STOPPING_LED_OFF;

    public static boolean isStartingState(ECardTapSessionState state) {
        switch (state) {
            case STARTING_CHECKING_STATUS_1:
            case STARTING_BACK_TO_IDLE:
            case STARTING_CHECKING_STATUS_2:
            case STARTING_LED_ON:
                return true;

            default:
                return false;
        }
    }

    public static boolean isStoppingState(ECardTapSessionState state) {
        return state == STOPPING_LED_OFF;
    }


}
