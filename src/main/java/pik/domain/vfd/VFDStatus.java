package pik.domain.vfd;

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

}
