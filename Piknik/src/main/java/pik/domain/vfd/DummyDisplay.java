package pik.domain.vfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.TM_T20IIIConstants;

/**
 * Dummy Display - Used when no physical display is available
 * Ensures application remains functional even without hardware
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class DummyDisplay extends AbstractVFDDisplay {
    private static final Logger logger = LoggerFactory.getLogger(DummyDisplay.class);

    public DummyDisplay() {
        super("DummyDisplay");
    }

    @Override
    public boolean connect(String portName, int baudRate) {
        // Dummy display always "connects" successfully
        isConnected = true;
        logger.info("DummyDisplay connected (no physical hardware)");
        return true;
    }

    @Override
    public void disconnect() {
        isConnected = false;
        logger.info("DummyDisplay disconnected");
    }

    @Override
    protected void initializeDisplay() {
        logger.debug("DummyDisplay initialized (no-op)");
    }

    @Override
    protected IVFDCommandSet getCommandSet() {
        return new FV2030BCommandSet(); // No matter what is returned here
    }

    @Override
    public void displayText(String text) {
        logger.debug("DummyDisplay.displayText: {}", text);
    }

    @Override
    public void clearDisplay() {
        logger.debug("DummyDisplay.clearDisplay");
    }

    @Override
    public void homeCursor() {
        logger.debug("DummyDisplay.homeCursor");
    }

    @Override
    public void setCursorPosition(int row, int col) {
        logger.debug("DummyDisplay.setCursorPosition: row={}, col={}", row, col);
    }

    @Override
    public void setBrightness(int brightness) {
        logger.debug("DummyDisplay.setBrightness: {}", brightness);
    }

    @Override
    public void showCursor(boolean show) {
        logger.debug("DummyDisplay.showCursor: {}", show);
    }

    @Override
    public void sendCustomCommand(String command) {
        logger.debug("DummyDisplay.sendCustomCommand: {}", command);
    }

    @Override
    public void runDemo() {
        logger.info("DummyDisplay.runDemo (simulated)");
        try {
            Thread.sleep(TM_T20IIIConstants.DEMO_STEP_DELAY_MS);
            logger.debug("Demo step 1: Clear");
            Thread.sleep(TM_T20IIIConstants.DEMO_STEP_DELAY_MS);
            logger.debug("Demo step 2: Display text");
            Thread.sleep(TM_T20IIIConstants.DEMO_STEP_DELAY_MS);
            logger.debug("Demo step 3: Brightness");
            Thread.sleep(TM_T20IIIConstants.DEMO_STEP_DELAY_MS);
            logger.debug("Demo complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isDummy() {
        return true;
    }

}
