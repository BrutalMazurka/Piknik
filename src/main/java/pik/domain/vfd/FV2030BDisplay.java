package pik.domain.vfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.FV2030BConstants;
import pik.common.TM_T20IIIConstants;

import java.io.UnsupportedEncodingException;

/**
 * FV-2030B Specific Implementation
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class FV2030BDisplay extends AbstractVFDDisplay {
    private static final Logger logger = LoggerFactory.getLogger(FV2030BDisplay.class);

    public FV2030BDisplay() {
        super("FV-2030B");
    }

    @Override
    protected void initializeDisplay() {
        try {
            clearDisplay();
            Thread.sleep(100);
            homeCursor();
            Thread.sleep(100);
            showCursor(false);
            logger.info("{} display initialized successfully", displayModel);
        } catch (Exception e) {
            logger.error("Error initializing {}: {}", displayModel, e.getMessage());
        }
    }

    @Override
    protected IVFDCommandSet getCommandSet() {
        return new FV2030BCommandSet();
    }

    @Override
    public void displayText(String text) {
        if (!isConnected) {
            logger.error("Not connected to display");
            return;
        }

        byte[] textBytes = getCommandSet().getTextCommand(text);
        sendRawData(textBytes);
        logger.debug("Displayed on {}: {}", displayModel, text);
    }

    @Override
    public void clearDisplay() {
        sendRawData(getCommandSet().getClearCommand());
        logger.debug("{} display cleared", displayModel);
    }

    @Override
    public void homeCursor() {
        sendRawData(getCommandSet().getHomeCursorCommand());
    }

    @Override
    public void setCursorPosition(int row, int col) {
        byte[] posCmd = getCommandSet().getCursorPositionCommand(row, col);
        sendRawData(posCmd);
    }

    @Override
    public void setBrightness(int brightness) {
        byte[] brightnessCmd = getCommandSet().getBrightnessCommand(brightness);
        sendRawData(brightnessCmd);
        logger.debug("{} brightness set to: {}%",  displayModel, brightness);
    }

    @Override
    public void showCursor(boolean show) {
        if (show) {
            sendRawData(getCommandSet().getCursorOnCommand());
        } else {
            sendRawData(getCommandSet().getCursorOffCommand());
        }
    }

    @Override
    public void sendCustomCommand(String command) {
        try {
            // Use CP852 encoding for custom commands as well
            byte[] commandBytes = command.getBytes(FV2030BConstants.VFD_ENCODING);
            sendRawData(commandBytes);
            logger.debug("Custom command sent to {}: {}", displayModel, command);
        } catch (UnsupportedEncodingException e) {
            logger.error("Error encoding custom command: {}", e.getMessage());
            // Fallback
            byte[] commandBytes = command.getBytes();
            sendRawData(commandBytes);
        }
    }

    @Override
    public void runDemo() {
        if (!isConnected) {
            logger.info("Please connect to display first");
            return;
        }

        try {
            logger.debug("\n--- {} Demo ---", displayModel);

            clearDisplay();
            Thread.sleep(TM_T20IIIConstants.DEMO_STEP_DELAY_MS);
            displayText("FV-2030B Ready");
            Thread.sleep(2000);

            // Brightness test
            clearDisplay();
            displayText("Brightness Test");
            Thread.sleep(1000);

            for (int brightness = 20; brightness <= TM_T20IIIConstants.BRIGHTNESS_MAX; brightness += 20) {
                setBrightness(brightness);
                Thread.sleep(800);
            }
            setBrightness(80);

            clearDisplay();
            displayText("Demo Complete!");
            Thread.sleep(2000);
            clearDisplay();

        } catch (InterruptedException e) {
            logger.error("Demo interrupted: {}", e.getMessage());
        }
    }
}
