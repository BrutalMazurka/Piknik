package pik.domain.vfd;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 08/10/2025
 */
public interface IVFDService {
    void initialize();
    void displayText(String text) throws Exception;
    void clearDisplay() throws Exception;
    void setCursorPosition(int row, int col) throws Exception;
    void setBrightness(int brightness) throws Exception;
    void showCursor(boolean show) throws Exception;
    void homeCursor() throws Exception;
    void sendCustomCommand(String command) throws Exception;
    void runDemo() throws Exception;
    VFDStatus getStatus();
    void close();
}
