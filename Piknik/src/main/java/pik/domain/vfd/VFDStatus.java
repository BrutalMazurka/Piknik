package pik.domain.vfd;

import java.util.Objects;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class VFDStatus {
    private boolean connected;
    private boolean error;
    private String errorMessage;
    private String displayModel;
    private long lastUpdate;
    private boolean dummyMode;

    public VFDStatus() {
        this.lastUpdate = System.currentTimeMillis();
    }

    public VFDStatus(VFDStatus other) {
        this.connected = other.connected;
        this.error = other.error;
        this.errorMessage = other.errorMessage;
        this.displayModel = other.displayModel;
        this.lastUpdate = other.lastUpdate;
        this.dummyMode = other.dummyMode;
    }

    // Connectivity
    public boolean isConnected() {
        return connected;
    }
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    // Error
    public boolean isError() {
        return error;
    }
    public void setError(boolean error) {
        this.error = error;
    }

    // Error message
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Display model
    public String getDisplayModel() {
        return displayModel;
    }
    public void setDisplayModel(String displayModel) {
        this.displayModel = displayModel;
    }

    // Display update
    public long getLastUpdate() {
        return lastUpdate;
    }
    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    // Dummy mode - no display connected
    public boolean isDummyMode() {
        return dummyMode;
    }
    public void setDummyMode(boolean dummyMode) {
        this.dummyMode = dummyMode;
    }

    /**
     * Check if VFD has errors
     * @return true if error flag is set or not connected (when not in dummy mode)
     */
    public boolean hasError() {
        // If in dummy mode, check only the error flag
        if (dummyMode) {
            return error;
        }
        // If not in dummy mode, not connected is also an error
        return error || !connected;
    }

    @Override
    public String toString() {
        return String.format("VFDStatus{connected=%s, displayModel=%s, isDummyMode=%s, error=%s}",
                connected, displayModel, dummyMode, error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VFDStatus that = (VFDStatus) o;
        return connected == that.connected &&
                error == that.error &&
                dummyMode == that.dummyMode &&
                Objects.equals(displayModel, that.displayModel) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connected, error, errorMessage, displayModel, dummyMode);
    }
}
