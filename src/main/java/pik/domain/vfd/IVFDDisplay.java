package pik.domain.vfd;

/**
 * VFD Display Interface - common operations for all VFD displays
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public interface IVFDDisplay {
    boolean connect(String portName, int baudRate);
    void disconnect();
    boolean isConnected();
    void displayText(String text);
    void clearDisplay();
    void homeCursor();
    void setCursorPosition(int row, int col);
    void setBrightness(int brightness);
    void showCursor(boolean show);
    void sendCustomCommand(String command);
    String getDisplayModel();
    void runDemo();
    // Check if this is a dummy/simulated display
    default boolean isDummy() {
        return false;
    }
    int getMaxRows();
    int getMaxColumns();
}
