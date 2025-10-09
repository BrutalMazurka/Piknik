package pik.domain.vfd;

import pik.domain.thprinter.PrinterStatus;

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
    private boolean isDummyMode;

    public VFDStatus() {
        this.lastUpdate = System.currentTimeMillis();
    }

    public VFDStatus(VFDStatus other) {
        this.connected = other.connected;
        this.error = other.error;
        this.errorMessage = other.errorMessage;
        this.displayModel = other.displayModel;
        this.lastUpdate = other.lastUpdate;
        this.isDummyMode = other.isDummyMode;
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
        return isDummyMode;
    }
    public void setDummyMode(boolean dummyMode) {
        this.isDummyMode = dummyMode;
    }

    @Override
    public String toString() {
        return String.format("VFDStatus{connected=%s, displayModel=%s, isDummyMode=%s, error=%s}",
                connected, displayModel, isDummyMode, error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VFDStatus that = (VFDStatus) o;
        return connected == that.connected &&
                error == that.error &&
                isDummyMode == that.isDummyMode &&
                Objects.equals(displayModel, that.displayModel) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connected, error, errorMessage, displayModel, isDummyMode);
    }
}
