package pik.domain.vfd;

/**
 * Command Set Interface - command structure for different VFD models
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public interface IVFDCommandSet {
    byte[] getClearCommand();
    byte[] getHomeCursorCommand();
    byte[] getCursorOnCommand();
    byte[] getCursorOffCommand();
    byte[] getBrightnessCommand(int brightness);
    byte[] getCursorPositionCommand(int row, int col);
    byte[] getTextCommand(String text);
    int getMaxRows();
    int getMaxColumns();
    String getCommandDescription();
}
