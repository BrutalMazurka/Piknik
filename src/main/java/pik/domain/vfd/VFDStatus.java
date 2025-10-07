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

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public boolean isError() { return error; }
    public void setError(boolean error) { this.error = error; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDisplayModel() { return displayModel; }
    public void setDisplayModel(String displayModel) { this.displayModel = displayModel; }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    public boolean isDummyMode() { return isDummyMode; }
    public void setDummyMode(boolean dummyMode) { this.isDummyMode = dummyMode; }
}
