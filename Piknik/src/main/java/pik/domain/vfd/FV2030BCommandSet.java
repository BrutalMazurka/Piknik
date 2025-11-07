package pik.domain.vfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.FV2030BConstants;
import pik.common.TM_T20IIIConstants;

import java.io.UnsupportedEncodingException;

/**
 * Command set for FV-2030B VFD Display
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class FV2030BCommandSet implements IVFDCommandSet {
    private static final Logger logger = LoggerFactory.getLogger(FV2030BCommandSet.class);

    private static final byte ESC = 0x1B;

    @Override
    public byte[] getClearCommand() {
        return new byte[] {0x0C};
    }

    @Override
    public byte[] getHomeCursorCommand() {
        return new byte[] {ESC, '[', 'H'};
    }

    @Override
    public byte[] getCursorOnCommand() {
        return new byte[] {ESC, '_', 0x01};
    }

    @Override
    public byte[] getCursorOffCommand() {
        return new byte[] {ESC, '_', 0x00};
    }

    @Override
    public byte[] getBrightnessCommand(int brightness) {
        if (brightness < TM_T20IIIConstants.BRIGHTNESS_MIN) brightness = TM_T20IIIConstants.BRIGHTNESS_MIN;
        if (brightness > TM_T20IIIConstants.BRIGHTNESS_MAX) brightness = TM_T20IIIConstants.BRIGHTNESS_MAX;
        return new byte[] {ESC, '*', (byte)brightness};
    }

    /**
     * Generate cursor position command.
     * NOTE: The FV-2030B uses 1-based indexing:
     * - row: 1-2 (row 1 = upper row, row 2 = lower row)
     * - col: 1-20 (column 1 = leftmost position)
     * The values are passed directly to the hardware without conversion.
     *
     * @param row Row number (1-2)
     * @param col Column number (1-20)
     * @return Command byte array
     */
    @Override
    public byte[] getCursorPositionCommand(int row, int col) {
        return new byte[] {ESC, 0x6C, (byte)col, (byte)row};
    }

    @Override
    public byte[] getTextCommand(String text) {
        try {
            // Convert UTF-8 string to CP852 encoding for VFD display
            return text.getBytes(FV2030BConstants.VFD_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.warn("CP852 encoding not supported, falling back to ISO-8859-2: {}", e.getMessage());
            try {
                // Fallback to the similar ISO-8859-2 (Latin-2)
                return text.getBytes("ISO-8859-2");
            } catch (UnsupportedEncodingException e2) {
                logger.error("Neither CP852 nor ISO-8859-2 encoding supported, using platform default", e2);
                return text.getBytes();
            }
        }
    }

    @Override
    public int getMaxRows() {
        return FV2030BConstants.MAX_ROWS;
    }

    @Override
    public int getMaxColumns() {
        return FV2030BConstants.MAX_COLUMNS;
    }

    @Override
    public String getCommandDescription() {
        return "FV-2030B uses ESC-based command set with ASCII text (1-based indexing: rows 1-2, columns 1-20)";
    }
}
