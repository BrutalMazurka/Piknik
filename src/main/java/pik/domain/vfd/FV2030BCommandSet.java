package pik.domain.vfd;

import pik.common.PrinterConstants;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class FV2030BCommandSet implements IVFDCommandSet {
    private static final byte ESC = 0x1B;

    @Override
    public byte[] getClearCommand() {
        return new byte[] {ESC, 'C'};
    }

    @Override
    public byte[] getHomeCursorCommand() {
        return new byte[] {ESC, 'H'};
    }

    @Override
    public byte[] getCursorOnCommand() {
        return new byte[] {ESC, 'S'};
    }

    @Override
    public byte[] getCursorOffCommand() {
        return new byte[] {ESC, 'T'};
    }

    @Override
    public byte[] getBrightnessCommand(int brightness) {
        if (brightness < PrinterConstants.BRIGHTNESS_MIN) brightness = PrinterConstants.BRIGHTNESS_MIN;
        if (brightness > PrinterConstants.BRIGHTNESS_MAX) brightness = PrinterConstants.BRIGHTNESS_MAX;
        return new byte[] {ESC, 'L', (byte)brightness};
    }

    @Override
    public byte[] getCursorPositionCommand(int row, int col) {
        return new byte[] {ESC, 'P', (byte)col, (byte)row};
    }

    @Override
    public byte[] getTextCommand(String text) {
        try {
            return text.getBytes("ASCII");
        } catch (Exception e) {
            return text.getBytes();
        }
    }

    @Override
    public int getMaxRows() {
        return 2;
    }

    @Override
    public int getMaxColumns() {
        return 30;
    }

    @Override
    public String getCommandDescription() {
        return "FV-2030B uses ESC-based command set with ASCII text";
    }
}
