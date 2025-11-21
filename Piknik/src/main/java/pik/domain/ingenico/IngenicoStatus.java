package pik.domain.ingenico;

import java.util.Objects;

/**
 * Ingenico Card Reader status information (IMMUTABLE)
 * Aggregates state from IngenicoReaderDevice, IngenicoIfsfApp, and IngenicoTransitApp
 *
 * Thread-safe immutable value object - all fields are final and no setters exist.
 * Use the builder() method to create new instances with modified values.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public record IngenicoStatus(
        // Initialization state
        EReaderInitState initState,
        boolean initialized,

        // IFSF Protocol (EMV/Payment) status
        boolean ifsfConnected,
        boolean ifsfAppAlive,
        String terminalId,

        // Transit Protocol (Public transport cards) status
        boolean transitConnected,
        boolean transitAppAlive,
        int transitTerminalStatusCode,
        String transitTerminalStatus,

        // SAM module status
        boolean samDukDetected,
        String samDukStatus,

        // General status
        boolean operational,  // Computed field: true when fully operational
        boolean error,
        String errorMessage,
        long lastUpdate,
        boolean dummyMode
) {
    /**
     * Validation in compact constructor
     */
    public IngenicoStatus {
        Objects.requireNonNull(initState, "initState cannot be null");
        // Allow null for optional string fields (terminalId, transitTerminalStatus, samDukStatus, errorMessage)
    }

    /**
     * Default constructor for initial state
     */
    public IngenicoStatus() {
        this(
                EReaderInitState.STARTING,
                false,
                false,
                false,
                null,
                false,
                false,
                0,
                null,
                false,
                null,
                false,  // operational
                false,
                null,
                System.currentTimeMillis(),
                false
        );
    }

    /**
     * Check if reader is fully operational (deprecated - use operational field instead)
     * A reader is operational when it's initialized, both protocols are connected and alive,
     * SAM DUK is detected, and there are no errors.
     * @deprecated Use the operational field directly instead
     */
    @Deprecated
    public boolean isOperational() {
        return operational;
    }

    /**
     * Compute operational status from current state
     */
    private static boolean computeOperational(boolean initialized, boolean ifsfConnected, boolean ifsfAppAlive,
                                              boolean transitConnected, boolean transitAppAlive,
                                              boolean samDukDetected, boolean error) {
        return initialized &&
               ifsfConnected && ifsfAppAlive &&
               transitConnected && transitAppAlive &&
               samDukDetected &&  // SAM DUK must be detected for full operation
               !error;
    }

    /**
     * Check if reader has warnings (partial connectivity)
     */
    public boolean hasWarnings() {
        return initialized &&
               (!ifsfConnected || !ifsfAppAlive || !transitConnected || !transitAppAlive);
    }

    /**
     * Check if reader has errors
     */
    public boolean hasErrors() {
        return error || (!initialized && !dummyMode);
    }

    /**
     * Create a builder pre-populated with this instance's values
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Create a new empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating new IngenicoStatus instances
     * The operational field is computed automatically from other fields when build() is called
     */
    public static class Builder {
        private EReaderInitState initState = EReaderInitState.STARTING;
        private boolean initialized = false;
        private boolean ifsfConnected = false;
        private boolean ifsfAppAlive = false;
        private String terminalId = null;
        private boolean transitConnected = false;
        private boolean transitAppAlive = false;
        private int transitTerminalStatusCode = 0;
        private String transitTerminalStatus = null;
        private boolean samDukDetected = false;
        private String samDukStatus = null;
        private boolean error = false;
        private String errorMessage = null;
        private long lastUpdate = System.currentTimeMillis();
        private boolean dummyMode = false;

        public Builder() {
        }

        public Builder(IngenicoStatus status) {
            this.initState = status.initState;
            this.initialized = status.initialized;
            this.ifsfConnected = status.ifsfConnected;
            this.ifsfAppAlive = status.ifsfAppAlive;
            this.terminalId = status.terminalId;
            this.transitConnected = status.transitConnected;
            this.transitAppAlive = status.transitAppAlive;
            this.transitTerminalStatusCode = status.transitTerminalStatusCode;
            this.transitTerminalStatus = status.transitTerminalStatus;
            this.samDukDetected = status.samDukDetected;
            this.samDukStatus = status.samDukStatus;
            this.error = status.error;
            this.errorMessage = status.errorMessage;
            this.lastUpdate = status.lastUpdate;
            this.dummyMode = status.dummyMode;
            // Note: operational is not copied - it will be computed in build()
        }

        public Builder initState(EReaderInitState initState) {
            this.initState = initState;
            return this;
        }

        public Builder initialized(boolean initialized) {
            this.initialized = initialized;
            return this;
        }

        public Builder ifsfConnected(boolean ifsfConnected) {
            this.ifsfConnected = ifsfConnected;
            return this;
        }

        public Builder ifsfAppAlive(boolean ifsfAppAlive) {
            this.ifsfAppAlive = ifsfAppAlive;
            return this;
        }

        public Builder terminalId(String terminalId) {
            this.terminalId = terminalId;
            return this;
        }

        public Builder transitConnected(boolean transitConnected) {
            this.transitConnected = transitConnected;
            return this;
        }

        public Builder transitAppAlive(boolean transitAppAlive) {
            this.transitAppAlive = transitAppAlive;
            return this;
        }

        public Builder transitTerminalStatusCode(int transitTerminalStatusCode) {
            this.transitTerminalStatusCode = transitTerminalStatusCode;
            return this;
        }

        public Builder transitTerminalStatus(String transitTerminalStatus) {
            this.transitTerminalStatus = transitTerminalStatus;
            return this;
        }

        public Builder samDukDetected(boolean samDukDetected) {
            this.samDukDetected = samDukDetected;
            return this;
        }

        public Builder samDukStatus(String samDukStatus) {
            this.samDukStatus = samDukStatus;
            return this;
        }

        public Builder error(boolean error) {
            this.error = error;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder lastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
            return this;
        }

        public Builder dummyMode(boolean dummyMode) {
            this.dummyMode = dummyMode;
            return this;
        }

        public IngenicoStatus build() {
            // Compute operational status based on current field values
            boolean operational = computeOperational(
                    initialized, ifsfConnected, ifsfAppAlive,
                    transitConnected, transitAppAlive, samDukDetected, error);

            return new IngenicoStatus(
                    initState,
                    initialized,
                    ifsfConnected,
                    ifsfAppAlive,
                    terminalId,
                    transitConnected,
                    transitAppAlive,
                    transitTerminalStatusCode,
                    transitTerminalStatus,
                    samDukDetected,
                    samDukStatus,
                    operational,  // Computed value
                    error,
                    errorMessage,
                    lastUpdate,
                    dummyMode
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "IngenicoStatus{initState=%s, initialized=%s, operational=%s, ifsfConn=%s, ifsfAlive=%s, transitConn=%s, transitAlive=%s, samDuk=%s, error=%s, dummy=%s}",
            initState, initialized, operational, ifsfConnected, ifsfAppAlive, transitConnected, transitAppAlive, samDukDetected, error, dummyMode);
    }
}
