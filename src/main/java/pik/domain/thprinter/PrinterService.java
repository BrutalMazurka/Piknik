package pik.domain.thprinter;

import com.google.gson.Gson;
import jpos.JposConst;
import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.TM_T20IIIConstants;
import pik.dal.PrinterConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core printer service for managing JavaPOS POSPrinter operations
 * Supports both real printer and dummy mode for testing/development
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrinterService implements IPrinterService, StatusUpdateListener, ErrorListener,
        DirectIOListener, OutputCompleteListener {

    private static final Logger logger = LoggerFactory.getLogger(PrinterService.class);

    private final PrinterConfig config;
    private final List<IPrinterStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock printerLock = new ReentrantLock();
    private final Gson gson = new Gson();

    private POSPrinter printer;
    private PrinterStatus currentStatus;
    private volatile PrinterState state = PrinterState.UNINITIALIZED;
    private volatile boolean dummyMode = false;
    private final Object outputCompleteLock = new Object();
    private volatile boolean outputComplete = false;

    public PrinterService(PrinterConfig config) {
        this.config = config;
        this.currentStatus = new PrinterStatus();

        // If configured as NONE, start in dummy mode immediately
        if (config.isDummy()) {
            this.dummyMode = true;
            logger.info("Printer service configured for DUMMY mode");
        } else {
            this.printer = new POSPrinter();
        }
    }

    // Observer registration methods
    public void addStatusListener(IPrinterStatusListener listener) {
        if (listener != null && !statusListeners.contains(listener)) {
            statusListeners.add(listener);
            logger.debug("Added status listener: {}", listener.getClass().getSimpleName());
        }
    }

    public void removeStatusListener(IPrinterStatusListener listener) {
        statusListeners.remove(listener);
        logger.debug("Removed status listener: {}", listener.getClass().getSimpleName());
    }

    // Notify all observers
    private void notifyStatusChanged(String source) {
        if (statusListeners.isEmpty()) {
            return;
        }

        PrinterStatusEvent event = new PrinterStatusEvent(new PrinterStatus(currentStatus), source);

        for (IPrinterStatusListener listener : statusListeners) {
            try {
                listener.onStatusChanged(event);
            } catch (Exception e) {
                logger.error("Error notifying listener {}: {}", listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Manually load JavaPOS configuration from jpos.xml
     * This bypasses the auto-discovery mechanism which can fail due to timing issues
     */
    private void loadJposConfiguration() throws JposException {
        try {
            // Get path to jpos.xml
            String configPath = System.getProperty("jpos.config.populatorFile");

            if (configPath == null || configPath.isEmpty()) {
                // Fallback: try to find it in standard locations
                String[] searchPaths = {"config/jpos.xml", "../config/jpos.xml", "jpos.xml"};

                for (String path : searchPaths) {
                    File f = new File(path);
                    if (f.exists()) {
                        configPath = f.getAbsolutePath();
                        logger.info("Found jpos.xml at: {}", configPath);
                        break;
                    }
                }
            }

            if (configPath == null) {
                throw new JposException(JposConst.JPOS_E_NOSERVICE,
                        "Cannot find jpos.xml configuration file");
            }

            // Remove file:// prefix if present
            configPath = configPath.replace("file://", "").replace("file:", "");

            logger.info("Loading JavaPOS configuration from: {}", configPath);

            // Use JCL's SimpleXmlRegPopulator to load the configuration
            jpos.config.simple.xml.SimpleXmlRegPopulator populator = new jpos.config.simple.xml.SimpleXmlRegPopulator();
            populator.load(configPath);

            logger.info("JavaPOS configuration loaded successfully");

        } catch (Exception e) {
            logger.error("Failed to load JavaPOS configuration", e);
            throw new JposException(JposConst.JPOS_E_NOSERVICE, "Failed to load jpos.xml: " + e.getMessage());
        }
    }

    /**
     * Initialize the printer connection and setup
     */
    @Override
    public void initialize() throws JposException {
        printerLock.lock();
        try {
            logger.info("Initializing printer service with config: {}", config);

            // If already in dummy mode from config, just initialize as dummy
            if (config.isDummy()) {
                initializeDummyMode("Configured for dummy mode");
                return;
            }

            transitionTo(PrinterState.OPENING);

            try {
                // JavaPOS hack - Load configuration FIRST
                loadJposConfiguration();

                String logicalName = config.getLogicalName();

                printer.open(logicalName);
                logger.debug("Printer opened with logical name: {}", logicalName);
                transitionTo(PrinterState.OPENED);

                transitionTo(PrinterState.CLAIMING);
                printer.claim(TM_T20IIIConstants.DEFAULT_CONNECTION_TIMEOUT);
                logger.debug("Printer claimed successfully");
                transitionTo(PrinterState.CLAIMED);

                transitionTo(PrinterState.ENABLING);
                printer.setDeviceEnabled(true);
                logger.debug("Printer enabled");
                transitionTo(PrinterState.ENABLED);

                // Setup event listeners
                printer.addStatusUpdateListener(this);
                printer.addErrorListener(this);
                printer.addDirectIOListener(this);
                printer.addOutputCompleteListener(this);
                logger.debug("Event listeners registered");

                // Configure printer settings
                transitionTo(PrinterState.CONFIGURING);
                configurePrinter();

                // Update status
                updatePrinterStatus();

                transitionTo(PrinterState.READY);
                dummyMode = false;
                logger.info("Printer service initialized successfully");

            } catch (JposException e) {
                logger.error("Failed to initialize printer: {} - {}", e.getErrorCode(), e.getMessage());
                logger.warn("Falling back to DUMMY mode");
                cleanup();
                initializeDummyMode("Hardware connection failed: " + e.getMessage());
            }

        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Initialize printer in dummy mode
     */
    private void initializeDummyMode(String reason) {
        dummyMode = true;
        transitionTo(PrinterState.READY);
        currentStatus.setOnline(true);
        currentStatus.setError(false);
        currentStatus.setErrorMessage("Running in dummy mode: " + reason);
        currentStatus.setLastUpdate(System.currentTimeMillis());
        currentStatus.setDummyMode(true);
        notifyStatusChanged("dummy_mode_init");
        logger.info("Printer service initialized in DUMMY mode: {}", reason);
    }

    /**
     * Check if printer is in dummy mode
     */
    public boolean isDummyMode() {
        return dummyMode;
    }

    /**
     * Transition to a new printer state
     */
    private void transitionTo(PrinterState newState) {
        logger.debug("Printer state transition: {} -> {}", state, newState);
        this.state = newState;
    }

    /**
     * Get current printer state
     */
    public PrinterState getPrinterState() {
        return state;
    }

    /**
     * Cleanup printer resources based on current state
     */
    private void cleanup() {
        try {
            logger.debug("Cleaning up printer in state: {}", state);

            if (printer == null || dummyMode) {
                return;
            }

            // Remove listeners if they were added (after ENABLED state)
            if (state.ordinal() >= PrinterState.ENABLED.ordinal()) {
                try {
                    printer.removeStatusUpdateListener(this);
                    printer.removeErrorListener(this);
                    printer.removeDirectIOListener(this);
                    printer.removeOutputCompleteListener(this);
                    logger.debug("Event listeners removed");
                } catch (Exception e) {
                    logger.debug("Error removing listeners: {}", e.getMessage());
                }
            }

            // Disable if enabled
            if (state.ordinal() >= PrinterState.ENABLED.ordinal()) {
                try {
                    if (printer.getDeviceEnabled()) {
                        printer.setDeviceEnabled(false);
                        logger.debug("Device disabled");
                    }
                } catch (Exception e) {
                    logger.debug("Error disabling device: {}", e.getMessage());
                }
            }

            // Release if claimed
            if (state.ordinal() >= PrinterState.CLAIMED.ordinal()) {
                try {
                    if (printer.getClaimed()) {
                        printer.release();
                        logger.debug("Device released");
                    }
                } catch (Exception e) {
                    logger.debug("Error releasing device: {}", e.getMessage());
                }
            }

            // Close if opened
            if (state.ordinal() >= PrinterState.OPENED.ordinal()) {
                try {
                    if (printer.getState() != JposConst.JPOS_S_CLOSED) {
                        printer.close();
                        logger.debug("Device closed");
                    }
                } catch (Exception e) {
                    logger.debug("Error closing device: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error during cleanup", e);
        } finally {
            transitionTo(PrinterState.UNINITIALIZED);
        }
    }

    /**
     * Wait for output to complete (for async mode)
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if output completed, false if timeout
     */
    public boolean waitForOutputComplete(long timeoutMs) {
        if (dummyMode) {
            return true; // No need to wait in dummy mode
        }

        try {
            // Check if printer is in async mode
            if (!printer.getAsyncMode()) {
                // In synchronous mode, print commands block until complete adding small delay for network buffer flushing
                Thread.sleep(100);
                return true;
            }
        } catch (JposException e) {
            logger.warn("Error checking async mode: {}", e.getMessage());
            // Assume async mode and proceed with waiting
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Wait for async output completion
        synchronized (outputCompleteLock) {
            outputComplete = false;
            long startTime = System.currentTimeMillis();

            while (!outputComplete) {
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = timeoutMs - elapsed;

                if (remaining <= 0) {
                    logger.warn("Timeout waiting for output completion");
                    return false;
                }

                try {
                    outputCompleteLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for output completion");
                    return false;
                }
            }

            logger.debug("Output completed successfully");
            return true;
        }
    }

    /**
     * Check if service is initialized (regardless of current errors)
     */
    @Override
    public boolean isInitialized() {
        return state == PrinterState.READY;
    }

    /**
     * Configure printer-specific settings
     */
    private void configurePrinter() throws JposException {
        if (dummyMode) {
            logger.debug("Skipping printer configuration in dummy mode");
            return;
        }

        try {
            // Set character set
            if (printer.getCapCharacterSet() > 0) {
                printer.setCharacterSet(POSPrinterConst.PTR_CS_ASCII);
            }

            // IMPORTANT: Enable asynchronous mode to get OutputCompleteEvents
            printer.setAsyncMode(true);
            logger.debug("Async mode enabled for output completion events");

            // Set map mode for metric measurements
            printer.setMapMode(POSPrinterConst.PTR_MM_METRIC);

            logger.debug("Printer configured with basic settings");
        } catch (JposException e) {
            logger.warn("Some printer configurations failed: {}", e.getMessage());
        }
    }

    @Override
    public void outputCompleteOccurred(OutputCompleteEvent e) {
        logger.debug("Output complete event received: outputID={}", e.getOutputID());
        synchronized (outputCompleteLock) {
            outputComplete = true;
            outputCompleteLock.notifyAll();
        }
    }

    /**
     * Print text content
     */
    @Override
    public void printText(String text) throws JposException, InterruptedException {
        if (!isReady()) {
            throw new JposException(JposConst.JPOS_E_OFFLINE, "Printer is not ready");
        }

        if (!printerLock.tryLock(config.connectionTimeout(), TimeUnit.MILLISECONDS)) {
            throw new JposException(JposConst.JPOS_E_TIMEOUT, "Could not acquire printer lock within timeout");
        }

        try {
            if (dummyMode) {
                logger.info("[DUMMY] Print text: {}", text.substring(0, Math.min(100, text.length())));
                simulatePrintDelay();
                return;
            }

            logger.debug("Printing text: {}", text.substring(0, Math.min(50, text.length())));
            printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, text);
            logger.debug("Text printed successfully");
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Print structured content from PrintRequest
     */
    @Override
    public void print(PrintRequest request) throws JposException {
        if (!isReady()) {
            throw new JposException(JposConst.JPOS_E_OFFLINE, "Printer is not ready");
        }

        printerLock.lock();
        try {
            logger.debug("Processing print request with {} copies", request.getCopies());

            if (dummyMode) {
                logger.info("[DUMMY] Print request: {} copies", request.getCopies());
                if (request.getText() != null) {
                    logger.info("[DUMMY] Text: {}", request.getText().substring(0, Math.min(100, request.getText().length())));
                }
                if (request.getItems() != null) {
                    logger.info("[DUMMY] Items count: {}", request.getItems().size());
                }
                simulatePrintDelay();
                return;
            }

            for (int copy = 0; copy < request.getCopies(); copy++) {
                PrintRequest.PrintOptions options = request.getOptions();
                if (options == null) {
                    options = new PrintRequest.PrintOptions();  // Use defaults
                }

                if (request.getText() != null && !request.getText().isEmpty()) {
                    printFormattedText(request.getText(), options);
                }

                if (request.getItems() != null) {
                    for (PrintRequest.PrintItem item : request.getItems()) {
                        printItem(item);
                    }
                }

                if (request.isCutPaper()) {
                    cutPaper();
                }
            }

            logger.info("Print request completed successfully");
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Simulate print delay in dummy mode
     */
    private void simulatePrintDelay() {
        try {
            Thread.sleep(200); // Simulate print time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Print formatted text with options
     */
    private void printFormattedText(String text, PrintRequest.PrintOptions options) throws JposException {
        if (dummyMode) {
            logger.debug("[DUMMY] Print formatted text");
            return;
        }

        StringBuilder formattedText = new StringBuilder();

        // Apply formatting based on options
        if (options != null) {
            if (options.getLineSpacing() != 30) {
                formattedText.append((char)0x1B).append((char)0x33).append((char)options.getLineSpacing());
            }
        }

        formattedText.append(text);

        if (!text.endsWith("\n")) {
            formattedText.append("\n");
        }

        printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, formattedText.toString());
    }

    /**
     * Print individual item based on type
     */
    private void printItem(PrintRequest.PrintItem item) throws JposException {
        if (item == null || item.getType() == null) {
            throw new IllegalArgumentException("Print item and type cannot be null");
        }

        if (dummyMode) {
            logger.debug("[DUMMY] Print item: type={}, content={}", item.getType(),
                    item.getContent() != null ? item.getContent().substring(0, Math.min(50, item.getContent().length())) : "null");
            return;
        }

        switch (item.getType().toUpperCase()) {
            case "TEXT":
                printFormattedItem(item);
                break;
            case "IMAGE":
                printImageItem(item);
                break;
            case "BARCODE":
                printBarcodeItem(item);
                break;
            case "LINE":
                printLineItem(item);
                break;
            default:
                logger.warn("Unknown print item type: {}", item.getType());
        }
    }

    /**
     * Print formatted text item
     */
    private void printFormattedItem(PrintRequest.PrintItem item) throws JposException {
        StringBuilder formattedText = new StringBuilder();
        PrintRequest.PrintItemOptions opts = item.getOptions();

        if (opts != null) {
            // Apply text formatting
            if (opts.isBold()) {
                formattedText.append((char)0x1B).append("E").append((char)1);
            }
            if (opts.isUnderline()) {
                formattedText.append((char)0x1B).append("-").append((char)1);
            }

            // Apply alignment
            switch (opts.getAlignment().toUpperCase()) {
                case "CENTER":
                    formattedText.append((char)0x1B).append("a").append((char)1);
                    break;
                case "RIGHT":
                    formattedText.append((char)0x1B).append("a").append((char)2);
                    break;
                default:
                    formattedText.append((char)0x1B).append("a").append((char)0);
            }

            // Apply font size
            if (opts.getFontSize() > TM_T20IIIConstants.MIN_FONT_SIZE) {
                int size = Math.min(opts.getFontSize(), TM_T20IIIConstants.MAX_FONT_SIZE) - 1;
                formattedText.append((char)0x1D).append("!").append((char)size);
            }
        }

        formattedText.append(item.getContent());

        if (opts != null) {
            // Reset formatting
            if (opts.isBold()) {
                formattedText.append((char)0x1B).append("E").append((char)0);
            }
            if (opts.isUnderline()) {
                formattedText.append((char)0x1B).append("-").append((char)0);
            }
            formattedText.append((char)0x1B).append("a").append((char)0); // Reset alignment
        }

        if (!item.getContent().endsWith("\n")) {
            formattedText.append("\n");
        }

        printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, formattedText.toString());
    }

    /**
     * Print image from base64 data or file path
     */
    private void printImageItem(PrintRequest.PrintItem item) throws JposException {
        try {
            BufferedImage image;
            String content = item.getContent();

            if (content.startsWith("data:image/") || content.startsWith("iVBORw0K")) {
                // Base64 encoded image
                String base64Data = content.contains(",") ? content.split(",")[1] : content;
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            } else {
                // File path
                image = ImageIO.read(new File(content));
            }

            if (image == null) {
                logger.error("Failed to load image - ImageIO returned null");
                throw new JposException(JposConst.JPOS_E_FAILURE, "Failed to load image: unsupported format");
            }

            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                logger.error("Image has invalid dimensions: {}x{}", image.getWidth(), image.getHeight());
                throw new JposException(JposConst.JPOS_E_FAILURE,
                        "Failed to load image: invalid dimensions " + image.getWidth() + "x" + image.getHeight());
            }

            printBitmap(image, item.getOptions());

        } catch (IOException e) {
            logger.error("Error processing image: {}", e.getMessage());
            throw new JposException(JposConst.JPOS_E_FAILURE, "Failed to process image: " + e.getMessage());
        }
    }

    /**
     * Print bitmap image
     */
    private void printBitmap(BufferedImage image, PrintRequest.PrintItemOptions options) throws JposException {
        try {
            // Resize image if needed
            if (options != null && (options.getWidth() > 0 || options.getHeight() > 0)) {
                int width = options.getWidth() > 0 ? options.getWidth() : image.getWidth();
                int height = options.getHeight() > 0 ? options.getHeight() : image.getHeight();

                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                resized.getGraphics().drawImage(image, 0, 0, width, height, null);
                image = resized;
            }

            // Convert to byte array and print
            byte[] bitmapData = GraphUtils.convertToMonochromeBitmap(image);

            // Options: PTR_BM_LEFT (-1), PTR_BM_CENTER (-2), PTR_BM_RIGHT (-3)
            int alignment = POSPrinterConst.PTR_BM_CENTER;

            // Check if options specify alignment
            if (options != null && options.getAlignment() != null) {
                switch (options.getAlignment().toUpperCase()) {
                    case "LEFT":
                        alignment = POSPrinterConst.PTR_BM_LEFT;
                        break;
                    case "CENTER":
                        alignment = POSPrinterConst.PTR_BM_CENTER;
                        break;
                    case "RIGHT":
                        alignment = POSPrinterConst.PTR_BM_RIGHT;
                        break;
                }
            }

            printer.printMemoryBitmap(POSPrinterConst.PTR_S_RECEIPT, bitmapData,
                    POSPrinterConst.PTR_BMT_BMP, image.getWidth(),
                    alignment);

        } catch (Exception e) {
            logger.error("Error printing bitmap: {}", e.getMessage());
            throw new JposException(JposConst.JPOS_E_FAILURE, "Failed to print bitmap");
        }
    }

    /**
     * Print barcode
     */
    private void printBarcodeItem(PrintRequest.PrintItem item) throws JposException {
        try {
            // Default barcode parameters
            int symbology = POSPrinterConst.PTR_BCS_Code128;
            int height = 50;
            int width = POSPrinterConst.PTR_BC_TEXT_BELOW;
            int alignment = POSPrinterConst.PTR_BC_CENTER;
            int textPosition = POSPrinterConst.PTR_BC_TEXT_BELOW;

            printer.printBarCode(POSPrinterConst.PTR_S_RECEIPT, item.getContent(),
                    symbology, height, width, alignment, textPosition);

        } catch (JposException e) {
            logger.error("Error printing barcode: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Print separator line
     */
    private void printLineItem(PrintRequest.PrintItem item) throws JposException {
        String line = item.getContent();
        if (line == null || line.isEmpty()) {
            line = "----------------------------------------\n";
        } else if (!line.endsWith("\n")) {
            line += "\n";
        }

        printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, line);
    }

    /**
     * Cut paper
     */
    @Override
    public void cutPaper() throws JposException {
        if (!isReady()) {
            throw new JposException(JposConst.JPOS_E_OFFLINE, "Printer is not ready");
        }

        printerLock.lock();
        try {
            if (dummyMode) {
                logger.info("[DUMMY] Cut paper");
                return;
            }

            if (printer.getCapRecPapercut()) {
                printer.cutPaper(TM_T20IIIConstants.FULL_CUT_PERCENTAGE); // 100% cut
                logger.debug("Paper cut executed");
            } else {
                logger.warn("Printer does not support paper cutting");
            }
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Update printer status by querying device
     */
    public void updatePrinterStatus() {
        printerLock.lock();
        try {
            PrinterStatus newStatus = new PrinterStatus();

            if (dummyMode) {
                // Dummy mode status
                newStatus.setOnline(true);
                newStatus.setCoverOpen(false);
                newStatus.setPaperEmpty(false);
                newStatus.setPaperNearEnd(false);
                newStatus.setPowerState(1);
                newStatus.setError(false);
                newStatus.setErrorMessage("Running in dummy mode");
            } else if (state == PrinterState.READY && printer != null) {
                try {
                    newStatus.setOnline(printer.getDeviceEnabled());
                    newStatus.setCoverOpen(printer.getCoverOpen());
                    newStatus.setPaperEmpty(printer.getRecEmpty());
                    newStatus.setPaperNearEnd(printer.getRecNearEnd());
                    newStatus.setPowerState(printer.getPowerState());
                    newStatus.setError(false);
                    newStatus.setErrorMessage(null);
                } catch (JposException e) {
                    newStatus.setOnline(false);
                    newStatus.setError(true);
                    newStatus.setErrorMessage(e.getMessage());
                }
            } else {
                newStatus.setOnline(false);
                newStatus.setError(true);
                newStatus.setErrorMessage("Printer not in READY state: " + state);
            }

            newStatus.setLastUpdate(System.currentTimeMillis());

            // Check if status actually changed
            if (!newStatus.equals(currentStatus)) {
                currentStatus = newStatus;
                notifyStatusChanged("periodic_check");
                logger.debug("Status updated: {}", currentStatus);
            }

        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Check if printer is ready for operations (initialized + online + no errors)
     */
    @Override
    public boolean isReady() {
        return state == PrinterState.READY && currentStatus.isOnline() && !currentStatus.hasErrors();
    }

    /**
     * Get current printer status
     */
    @Override
    public PrinterStatus getStatus() {
        printerLock.lock();
        try {
            // Return a defensive copy to prevent external modification
            return new PrinterStatus(currentStatus);
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Close printer connection
     */
    @Override
    public void close() {
        printerLock.lock();
        try {
            if (dummyMode) {
                logger.info("Closing dummy printer");
                currentStatus.setOnline(false);
                return;
            }

            if (printer != null && state == PrinterState.READY) {
                cleanup();
                logger.info("Printer closed successfully");
            }

            currentStatus.setOnline(false);

        } finally {
            printerLock.unlock();
        }
    }

    // Event listener implementations
    @Override
    public void statusUpdateOccurred(StatusUpdateEvent e) {
        if (dummyMode) return;
        logger.debug("Status update event: {}", e.getStatus());
        updatePrinterStatus();
    }

    @Override
    public void errorOccurred(ErrorEvent e) {
        if (dummyMode) return;
        logger.error("Printer error occurred: {} - {}", e.getErrorCode(), e.getErrorCodeExtended());

        currentStatus.setError(true);
        currentStatus.setErrorMessage(String.format("Error %d: %s", e.getErrorCode(), e.getErrorCodeExtended()));

        notifyStatusChanged("error_event");
    }

    @Override
    public void directIOOccurred(DirectIOEvent e) {
        if (dummyMode) return;
        logger.debug("DirectIO event: eventNumber={}, data={}, object={}", e.getEventNumber(), e.getData(), e.getObject());
    }
}
