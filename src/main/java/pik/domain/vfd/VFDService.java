package pik.domain.vfd;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.EDisplayType;
import pik.common.PrinterConstants;
import pik.dal.VFDConfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * VFD Service - manages VFD display operations
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class VFDService implements IVFDService {
    private static final Logger logger = LoggerFactory.getLogger(VFDService.class);

    private final VFDConfig config;
    private final ReentrantLock displayLock = new ReentrantLock();
    private final Gson gson = new Gson();
    private final List<IVFDStatusListener> statusListeners = new CopyOnWriteArrayList<>();

    private IVFDDisplay display;
    private final VFDStatus currentStatus;
    private volatile boolean initialized = false;

    public VFDService(VFDConfig config) {
        this.config = config;
        this.currentStatus = new VFDStatus();
    }

    public void addStatusListener(IVFDStatusListener listener) {
        if (listener != null && !statusListeners.contains(listener)) {
            statusListeners.add(listener);
        }
    }

    public void removeStatusListener(IVFDStatusListener listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusChanged() {
        VFDStatusEvent event = new VFDStatusEvent(new VFDStatus(currentStatus), "status_update");

        for (IVFDStatusListener listener : statusListeners) {
            try {
                listener.onStatusChanged(event);
            } catch (Exception e) {
                logger.error("Error notifying VFD listener: {}", e.getMessage());
            }
        }
    }

    /**
     * Update status and notify listeners
     */
    private void updateStatus(boolean connected, String errorMessage) {
        currentStatus.setConnected(connected);
        currentStatus.setError(errorMessage != null && !errorMessage.startsWith("Using dummy"));
        currentStatus.setErrorMessage(errorMessage);
        currentStatus.setLastUpdate(System.currentTimeMillis());

        if (display != null) {
            currentStatus.setDisplayModel(display.getDisplayModel());
            currentStatus.setDummyMode(display instanceof DummyDisplay);
        }

        notifyStatusChanged();
    }

    /**
     * Initialize VFD display connection
     */
    @Override
    public void initialize() {
        displayLock.lock();
        try {
            logger.info("Initializing VFD display with config: {}", config);

            // Create display instance
            display = VFDDisplayFactory.createDisplay(config.displayType());
            logger.debug("Created display instance: {}", display.getDisplayModel());

            // Attempt to connect to display
            boolean connected = display.connect(config.portName(), config.baudRate());

            if (connected) {
                initialized = true;
                updateStatus(true, null);
                logger.info("VFD display initialized successfully");
            } else {
                initialized = false;
                logger.warn("Failed to connect to VFD display, falling back to DummyDisplay");
                fallbackToDummyDisplay("Connection failed - port may not be available");
            }

        } catch (Exception e) {
            initialized = false;
            logger.error("Error during VFD display initialization, falling back to DummyDisplay", e);
            fallbackToDummyDisplay(e.getMessage());
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Fallback to dummy display when real display is unavailable
     */
    private void fallbackToDummyDisplay(String reason) {
        try {
            // Close any existing connection
            if (display != null && display.isConnected()) {
                display.disconnect();
            }

            // Create dummy display
            display = VFDDisplayFactory.createDisplay(EDisplayType.NONE);

            // Dummy display always "connects" successfully
            display.connect("DUMMY", 9600);

            initialized = true;
            updateStatus(true, "Using dummy display: " + reason);
            logger.info("VFD service running in dummy mode");

        } catch (Exception e) {
            logger.error("Failed to create dummy display", e);
            initialized = false;
            updateStatus(false, "Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * Display text on VFD
     */
    @Override
    public void displayText(String text) throws Exception {
        if (!isReady()) {
            throw new IllegalStateException("VFD display is not ready");
        }

        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }

        int maxLength = display.getMaxRows() * display.getMaxColumns();
        if (text.length() > maxLength) {
            logger.warn("Text truncated from {} to {} characters", text.length(), maxLength);
            text = text.substring(0, maxLength);
        }

        displayLock.lock();
        try {
            logger.debug("Displaying text: {}", text);
            display.displayText(text);
            logger.debug("Text displayed successfully");
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Clear display
     */
    @Override
    public void clearDisplay() throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        displayLock.lock();
        try {
            logger.debug("Clearing display");
            display.clearDisplay();
            logger.debug("Display cleared successfully");
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Set cursor position (1-based indexing: rows 1-2, columns 1-20)
     */
    @Override
    public void setCursorPosition(int row, int col) throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        // Boundary check - VFD uses 1-based indexing
        if (row < 1 || row > display.getMaxRows()) {
            throw new IllegalArgumentException("Row must be between 1 and " + display.getMaxRows());
        }
        if (col < 1 || col > display.getMaxColumns()) {
            throw new IllegalArgumentException("Column must be between 1 and " + display.getMaxColumns());
        }

        displayLock.lock();
        try {
            logger.debug("Setting cursor position to row={}, col={}", row, col);
            display.setCursorPosition(row, col);
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Set brightness
     */
    @Override
    public void setBrightness(int brightness) throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        if (brightness < PrinterConstants.BRIGHTNESS_MIN || brightness > PrinterConstants.BRIGHTNESS_MAX) {
            throw new IllegalArgumentException("Brightness must be between " + PrinterConstants.BRIGHTNESS_MIN + " and " + PrinterConstants.BRIGHTNESS_MAX);
        }

        displayLock.lock();
        try {
            logger.debug("Setting brightness to {}", brightness);
            display.setBrightness(brightness);
            logger.debug("Brightness set successfully");
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Show or hide cursor
     */
    @Override
    public void showCursor(boolean show) throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        displayLock.lock();
        try {
            logger.debug("Setting cursor visibility to {}", show);
            display.showCursor(show);
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Home cursor
     */
    @Override
    public void homeCursor() throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        displayLock.lock();
        try {
            logger.debug("Homing cursor");
            display.homeCursor();
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Send custom command
     */
    @Override
    public void sendCustomCommand(String command) throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        displayLock.lock();
        try {
            logger.debug("Sending custom command: {}", command);
            display.sendCustomCommand(command);
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Run demo
     */
    @Override
    public void runDemo() throws Exception {
        if (!isReady()) {
            throw new Exception("VFD display is not ready");
        }

        displayLock.lock();
        try {
            logger.info("Running VFD demo");
            display.runDemo();
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Check if display is ready
     */
    @Override
    public boolean isReady() {
        return initialized && display != null && display.isConnected();
    }

    /**
     * Get current status
     */
    @Override
    public VFDStatus getStatus() {
        return currentStatus;
    }

    /**
     * Attempt to reconnect to real VFD display
     * Can be called when running in dummy mode to try connecting to hardware
     */
    @Override
    public boolean attemptReconnect() {
        displayLock.lock();
        try {
            logger.info("Attempting to reconnect to VFD display...");

            // Disconnect current display
            if (display != null) {
                try {
                    if (display.isConnected()) {
                        display.disconnect();
                    }
                } catch (Exception e) {
                    logger.warn("Error disconnecting old display: ", e);
                }
            }

            // Try to create and connect to real display
            IVFDDisplay newDisplay = VFDDisplayFactory.createDisplay(config.displayType());
            boolean connected = newDisplay.connect(config.portName(), config.baudRate());

            if (connected) {
                display = newDisplay;
                initialized = true;
                updateStatus(true, null);
                logger.info("Successfully reconnected to VFD display");
                return true;
            } else {
                logger.warn("Reconnection failed, staying in dummy mode");
                fallbackToDummyDisplay("Reconnection failed");
                return false;
            }

        } catch (Exception e) {
            logger.error("Error during reconnection attempt", e);
            fallbackToDummyDisplay("Reconnection error: " + e.getMessage());
            return false;
        } finally {
            displayLock.unlock();
        }
    }

    /**
     * Check if display is in dummy mode
     */
    public boolean isDummyMode() {
        return display != null && display.isDummy();
    }

    /**
     * Close display connection
     */
    @Override
    public void close() {
        displayLock.lock();
        try {
            if (display != null && initialized) {
                display.disconnect();
                logger.info("VFD display closed successfully");
            }

            initialized = false;
            updateStatus(false, "Display closed");

        } finally {
            displayLock.unlock();
        }
    }
    /**
     * Get display information
     */
    @Override
    public String getDisplayInfo() {
        if (display == null) {
            return "No display connected";
        }
        return String.format("Model: %s, Connected: %s",
                display.getDisplayModel(),
                display.isConnected());
    }
}
