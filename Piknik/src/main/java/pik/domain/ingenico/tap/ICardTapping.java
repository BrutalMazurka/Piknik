package pik.domain.ingenico.tap;

public interface ICardTapping {
    void start(CardTappingRequest request);

    /**
     * Stops the card tapping process.
     *
     * @param setLedDiodesOff if true, turns off LED diodes when stopping TAP operation.
     *                        Should be false if card processing continues in the
     *                        transaction to avoid inappropriate LED behavior during stop.
     */
    void stop(boolean setLedDiodesOff);

    /**
     * Forces LED diodes to turn off if the tapping process is inactive.
     * This method allows turning off LEDs even when the last stop operation
     * was called with setLedDiodesOff == false.
     */
    void setLedDiodesOffIfInactive();

    void setMaintenanceMode(boolean on);
}
