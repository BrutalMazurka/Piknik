package pik.domain.ingenico;

import java.util.Objects;

/**
 * Ingenico Card Reader status information
 * Aggregates state from IngenicoReaderDevice, IngenicoIfsfApp, and IngenicoTransitApp
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public class IngenicoStatus {
    // Initialization state
    private EReaderInitState initState;
    private boolean initialized;

    // IFSF Protocol (EMV/Payment) status
    private boolean ifsfConnected;
    private boolean ifsfAppAlive;
    private String terminalId;

    // Transit Protocol (Public transport cards) status
    private boolean transitConnected;
    private boolean transitAppAlive;
    private int transitTerminalStatusCode;
    private String transitTerminalStatus;

    // SAM module status
    private boolean samDukDetected;
    private String samDukStatus;

    // General status
    private boolean error;
    private String errorMessage;
    private long lastUpdate;
    private boolean dummyMode;

    public IngenicoStatus() {
        this.lastUpdate = System.currentTimeMillis();
        this.initState = EReaderInitState.STARTING;
    }

    public IngenicoStatus(IngenicoStatus other) {
        this.initState = other.initState;
        this.initialized = other.initialized;
        this.ifsfConnected = other.ifsfConnected;
        this.ifsfAppAlive = other.ifsfAppAlive;
        this.terminalId = other.terminalId;
        this.transitConnected = other.transitConnected;
        this.transitAppAlive = other.transitAppAlive;
        this.transitTerminalStatusCode = other.transitTerminalStatusCode;
        this.transitTerminalStatus = other.transitTerminalStatus;
        this.samDukDetected = other.samDukDetected;
        this.samDukStatus = other.samDukStatus;
        this.error = other.error;
        this.errorMessage = other.errorMessage;
        this.lastUpdate = other.lastUpdate;
        this.dummyMode = other.dummyMode;
    }

    // Initialization state
    public EReaderInitState getInitState() {
        return initState;
    }
    public void setInitState(EReaderInitState initState) {
        this.initState = initState;
    }

    public boolean isInitialized() {
        return initialized;
    }
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    // IFSF status
    public boolean isIfsfConnected() {
        return ifsfConnected;
    }
    public void setIfsfConnected(boolean ifsfConnected) {
        this.ifsfConnected = ifsfConnected;
    }

    public boolean isIfsfAppAlive() {
        return ifsfAppAlive;
    }
    public void setIfsfAppAlive(boolean ifsfAppAlive) {
        this.ifsfAppAlive = ifsfAppAlive;
    }

    public String getTerminalId() {
        return terminalId;
    }
    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    // Transit status
    public boolean isTransitConnected() {
        return transitConnected;
    }
    public void setTransitConnected(boolean transitConnected) {
        this.transitConnected = transitConnected;
    }

    public boolean isTransitAppAlive() {
        return transitAppAlive;
    }
    public void setTransitAppAlive(boolean transitAppAlive) {
        this.transitAppAlive = transitAppAlive;
    }

    public int getTransitTerminalStatusCode() {
        return transitTerminalStatusCode;
    }
    public void setTransitTerminalStatusCode(int transitTerminalStatusCode) {
        this.transitTerminalStatusCode = transitTerminalStatusCode;
    }

    public String getTransitTerminalStatus() {
        return transitTerminalStatus;
    }
    public void setTransitTerminalStatus(String transitTerminalStatus) {
        this.transitTerminalStatus = transitTerminalStatus;
    }

    // SAM status
    public boolean isSamDukDetected() {
        return samDukDetected;
    }
    public void setSamDukDetected(boolean samDukDetected) {
        this.samDukDetected = samDukDetected;
    }

    public String getSamDukStatus() {
        return samDukStatus;
    }
    public void setSamDukStatus(String samDukStatus) {
        this.samDukStatus = samDukStatus;
    }

    // General status
    public boolean isError() {
        return error;
    }
    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }
    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isDummyMode() {
        return dummyMode;
    }
    public void setDummyMode(boolean dummyMode) {
        this.dummyMode = dummyMode;
    }

    /**
     * Check if reader is fully operational
     */
    public boolean isOperational() {
        return initialized &&
               ifsfConnected && ifsfAppAlive &&
               transitConnected && transitAppAlive &&
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

    @Override
    public String toString() {
        return String.format(
            "IngenicoStatus{initState=%s, initialized=%s, ifsfConn=%s, ifsfAlive=%s, transitConn=%s, transitAlive=%s, samDuk=%s, error=%s, dummy=%s}",
            initState, initialized, ifsfConnected, ifsfAppAlive, transitConnected, transitAppAlive, samDukDetected, error, dummyMode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngenicoStatus that = (IngenicoStatus) o;
        return initialized == that.initialized &&
                ifsfConnected == that.ifsfConnected &&
                ifsfAppAlive == that.ifsfAppAlive &&
                transitConnected == that.transitConnected &&
                transitAppAlive == that.transitAppAlive &&
                transitTerminalStatusCode == that.transitTerminalStatusCode &&
                samDukDetected == that.samDukDetected &&
                error == that.error &&
                dummyMode == that.dummyMode &&
                initState == that.initState &&
                Objects.equals(terminalId, that.terminalId) &&
                Objects.equals(transitTerminalStatus, that.transitTerminalStatus) &&
                Objects.equals(samDukStatus, that.samDukStatus) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initState, initialized, ifsfConnected, ifsfAppAlive, terminalId,
                transitConnected, transitAppAlive, transitTerminalStatusCode, transitTerminalStatus,
                samDukDetected, samDukStatus, error, errorMessage, dummyMode);
    }
}
