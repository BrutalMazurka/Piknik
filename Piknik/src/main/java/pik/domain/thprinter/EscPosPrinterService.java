package pik.domain.thprinter;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.barcode.BarCode;
import com.github.anastaciocintra.escpos.image.BitonalOrderedDither;
import com.github.anastaciocintra.escpos.image.BitonalThreshold;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.escpos.image.RasterBitImageWrapper;
import com.github.anastaciocintra.output.TcpOutputStream;
import com.github.anastaciocintra.output.SerialOutputStream;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.TM_T20IIIConstants;
import pik.dal.PrinterConfig;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ESC/POS Coffee implementation of printer service
 * Supports direct ESC/POS printing without JavaPOS middleware
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/11/2025
 */
public class EscPosPrinterService implements IPrinterService {

    private static final Logger logger = LoggerFactory.getLogger(EscPosPrinterService.class);

    private final PrinterConfig config;
    private final List<IPrinterStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock printerLock = new ReentrantLock();
    private final Gson gson = new Gson();

    private OutputStream outputStream;
    private EscPos escpos;
    private PrinterStatus currentStatus;
    private volatile PrinterState state = PrinterState.UNINITIALIZED;
    private volatile boolean dummyMode = false;

    public EscPosPrinterService(PrinterConfig config) {
        this.config = config;
        this.currentStatus = new PrinterStatus();

        // If configured as NONE, start in dummy mode immediately
        if (config.isDummy()) {
            this.dummyMode = true;
            logger.info("Printer service configured for DUMMY mode");
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
     * Initialize the printer connection and setup
     */
    @Override
    public void initialize() throws IOException {
        printerLock.lock();
        try {
            logger.info("Initializing ESC/POS printer service with config: {}", config);

            // If already in dummy mode from config, just initialize as dummy
            if (config.isDummy()) {
                initializeDummyMode("Configured for dummy mode");
                return;
            }

            transitionTo(PrinterState.OPENING);

            try {
                // Create connection based on type
                outputStream = createOutputStream();
                logger.debug("Output stream created");
                transitionTo(PrinterState.OPENED);

                // Create EscPos instance
                escpos = new EscPos(outputStream);
                logger.debug("EscPos instance created");
                transitionTo(PrinterState.ENABLED);

                // Initialize printer
                escpos.initializePrinter();
                logger.debug("Printer initialized");

                // Update status
                updatePrinterStatus();

                transitionTo(PrinterState.READY);
                dummyMode = false;
                logger.info("ESC/POS printer service initialized successfully");

            } catch (IOException e) {
                logger.error("CRITICAL: Printer initialization failed in {} mode", config.connectionType());
                logger.error("  Error message: {}", e.getMessage());
                logger.error("  Change 'printer.connection.type=NONE' in application.properties to use dummy mode");

                // Clean up any partial initialization
                cleanup();

                // Set status to offline with error
                currentStatus.setOnline(false);
                currentStatus.setError(true);
                currentStatus.setErrorMessage("Printer hardware unavailable: " + e.getMessage());
                currentStatus.setLastUpdate(System.currentTimeMillis());
                currentStatus.setDummyMode(false);

                // Notify listeners about the error
                notifyStatusChanged("init_failed");

                // Re-throw the exception - do NOT fall back to dummy mode
                throw new RuntimeException("Failed to initialize printer in " + config.connectionType() +
                        " mode. Either fix the connection or set printer.connection.type=NONE for dummy mode.", e);
            }

        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Create output stream based on connection type
     */
    private OutputStream createOutputStream() throws IOException {
        return switch (config.connectionType()) {
            case NETWORK -> {
                logger.info("Connecting to network printer: {}:{}", config.ipAddress(), config.networkPort());
                yield new TcpOutputStream(config.ipAddress(), config.networkPort(), config.connectionTimeout());
            }
            case USB -> {
                logger.info("Connecting to USB/Serial printer: {} at {} baud", config.comPort(), config.baudRate());
                yield new SerialOutputStream(config.comPort(), config.baudRate());
            }
            case NONE -> throw new IOException("Cannot create output stream for NONE connection type");
        };
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

            if (dummyMode) {
                return;
            }

            if (escpos != null) {
                try {
                    escpos.close();
                    logger.debug("EscPos closed");
                } catch (Exception e) {
                    logger.debug("Error closing EscPos: {}", e.getMessage());
                }
                escpos = null;
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                    logger.debug("Output stream closed");
                } catch (Exception e) {
                    logger.debug("Error closing output stream: {}", e.getMessage());
                }
                outputStream = null;
            }

        } catch (Exception e) {
            logger.error("Unexpected error during cleanup", e);
        } finally {
            transitionTo(PrinterState.UNINITIALIZED);
        }
    }

    /**
     * Wait for output to complete
     * Note: ESC/POS is synchronous, so this just adds a small delay for buffer flushing
     */
    @Override
    public boolean waitForOutputComplete(long timeoutMs) {
        if (dummyMode) {
            return true;
        }

        try {
            // ESC/POS printing is synchronous, just ensure buffers are flushed
            if (outputStream != null) {
                outputStream.flush();
            }
            // Small delay to ensure data reaches printer
            Thread.sleep(100);
            return true;
        } catch (IOException e) {
            logger.warn("Error flushing output stream: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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
     * Print text content
     */
    @Override
    public void printText(String text) throws IOException, InterruptedException {
        if (!isReady()) {
            throw new IOException("Printer is not ready");
        }

        if (!printerLock.tryLock(config.connectionTimeout(), TimeUnit.MILLISECONDS)) {
            throw new IOException("Could not acquire printer lock within timeout");
        }

        try {
            if (dummyMode) {
                logger.info("[DUMMY] Print text: {}", text.substring(0, Math.min(100, text.length())));
                simulatePrintDelay();
                return;
            }

            logger.debug("Printing text: {}", text.substring(0, Math.min(50, text.length())));
            escpos.write(text);
            escpos.feed(1);
            outputStream.flush();
            logger.debug("Text printed successfully");
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Print structured content from PrintRequest
     */
    @Override
    public void print(PrintRequest request) throws IOException {
        if (!isReady()) {
            throw new IOException("Printer is not ready");
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
    private void printFormattedText(String text, PrintRequest.PrintOptions options) throws IOException {
        if (dummyMode) {
            logger.debug("[DUMMY] Print formatted text");
            return;
        }

        // Apply line spacing if specified
        if (options != null && options.getLineSpacing() != 30) {
            escpos.setLineSpacing(options.getLineSpacing());
        }

        escpos.write(text);
        if (!text.endsWith("\n")) {
            escpos.feed(1);
        }

        // Reset line spacing to default
        if (options != null && options.getLineSpacing() != 30) {
            escpos.setLineSpacing(30);
        }
    }

    /**
     * Print individual item based on type
     */
    private void printItem(PrintRequest.PrintItem item) throws IOException {
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
    private void printFormattedItem(PrintRequest.PrintItem item) throws IOException {
        PrintRequest.PrintItemOptions opts = item.getOptions();

        // Create style based on options
        Style style = new Style();

        if (opts != null) {
            // Apply text formatting
            if (opts.isBold()) {
                style.setBold(true);
            }
            if (opts.isUnderline()) {
                style.setUnderline(Style.Underline.OneDotThick);
            }

            // Apply alignment
            switch (opts.getAlignment().toUpperCase()) {
                case "CENTER":
                    style.setJustification(EscPosConst.Justification.Center);
                    break;
                case "RIGHT":
                    style.setJustification(EscPosConst.Justification.Right);
                    break;
                default:
                    style.setJustification(EscPosConst.Justification.Left_Default);
            }

            // Apply font size (ESC/POS uses width and height multipliers)
            if (opts.getFontSize() > TM_T20IIIConstants.MIN_FONT_SIZE) {
                int size = Math.min(opts.getFontSize(), TM_T20IIIConstants.MAX_FONT_SIZE);
                // Map 1-8 to width/height multipliers (0-7 in ESC/POS)
                int multiplier = size - 1;
                style.setFontSize(multiplier, multiplier);
            }
        }

        // Write with style
        escpos.write(style, item.getContent());

        if (!item.getContent().endsWith("\n")) {
            escpos.feed(1);
        }
    }

    /**
     * Print image from base64 data or file path
     */
    private void printImageItem(PrintRequest.PrintItem item) throws IOException {
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
                throw new IOException("Failed to load image: unsupported format");
            }

            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                logger.error("Image has invalid dimensions: {}x{}", image.getWidth(), image.getHeight());
                throw new IOException("Failed to load image: invalid dimensions " + image.getWidth() + "x" + image.getHeight());
            }

            printBitmap(image, item.getOptions());

        } catch (IOException e) {
            logger.error("Error processing image: {}", e.getMessage());
            throw new IOException("Failed to process image: " + e.getMessage());
        }
    }

    /**
     * Print bitmap image using escpos-coffee
     */
    private void printBitmap(BufferedImage image, PrintRequest.PrintItemOptions options) throws IOException {
        try {
            // STEP 1: Auto-scale to fit printer width (before any other processing)
            image = GraphUtils.autoScaleToFitWidth(image, TM_T20IIIConstants.MAX_PRINT_WIDTH_DOTS);

            // STEP 2: Apply manual resize if requested in options
            if (options != null && (options.getWidth() > 0 || options.getHeight() > 0)) {
                int width, height;

                // Calculate dimensions while maintaining aspect ratio
                if (options.getWidth() > 0 && options.getHeight() > 0) {
                    width = options.getWidth();
                    height = options.getHeight();
                } else if (options.getWidth() > 0) {
                    width = options.getWidth();
                    double aspectRatio = (double) image.getHeight() / image.getWidth();
                    height = (int) Math.round(width * aspectRatio);
                } else {
                    height = options.getHeight();
                    double aspectRatio = (double) image.getWidth() / image.getHeight();
                    width = (int) Math.round(height * aspectRatio);
                }

                // Limit manual width to max print width
                if (width > TM_T20IIIConstants.MAX_PRINT_WIDTH_DOTS) {
                    width = TM_T20IIIConstants.MAX_PRINT_WIDTH_DOTS;
                    double aspectRatio = (double) image.getHeight() / image.getWidth();
                    height = (int) Math.round(width * aspectRatio);
                }

                logger.debug("Manual resize from {}x{} to {}x{}", image.getWidth(), image.getHeight(), width, height);

                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(image, 0, 0, width, height, null);
                g2d.dispose();
                image = resized;
            }

            // STEP 3: Create EscPosImage
            EscPosImage escposImage = new EscPosImage(image, image.getWidth());

            // STEP 4: Determine alignment
            RasterBitImageWrapper.Justification justification = RasterBitImageWrapper.Justification.Center;
            if (options != null && options.getAlignment() != null) {
                switch (options.getAlignment().toUpperCase()) {
                    case "LEFT":
                        justification = RasterBitImageWrapper.Justification.Left_Default;
                        break;
                    case "CENTER":
                        justification = RasterBitImageWrapper.Justification.Center;
                        break;
                    case "RIGHT":
                        justification = RasterBitImageWrapper.Justification.Right;
                        break;
                }
            }

            logger.debug("Printing bitmap: {}x{} pixels, justification={}",
                    image.getWidth(), image.getHeight(), justification);

            // STEP 5: Create wrapper with dithering algorithm
            // Use BitonalOrderedDither for better quality (can also try BitonalThreshold)
            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
            imageWrapper.setJustification(justification);

            // Print the image using ordered dither algorithm
            escpos.write(imageWrapper, escposImage, new BitonalOrderedDither());

            logger.debug("Bitmap printed successfully");

        } catch (Exception e) {
            logger.error("Error printing bitmap: {}", e.getMessage(), e);
            throw new IOException("Failed to print bitmap: " + e.getMessage());
        }
    }

    /**
     * Print barcode
     */
    private void printBarcodeItem(PrintRequest.PrintItem item) throws IOException {
        try {
            BarCode barcode = new BarCode();

            // Configure barcode
            barcode.setJustification(EscPosConst.Justification.Center);
            barcode.setBarWidth(3);  // Default width
            barcode.setHeight(50);    // Default height
            barcode.setHRIPosition(BarCode.BarCodeHRIPosition.Below);
            barcode.setHRIFont(BarCode.BarCodeHRIFont.FontA);

            // Print Code128 barcode
            escpos.write(barcode, BarCode.BarCodeType.CODE128, item.getContent());

        } catch (Exception e) {
            logger.error("Error printing barcode: {}", e.getMessage());
            throw new IOException("Failed to print barcode: " + e.getMessage());
        }
    }

    /**
     * Print separator line
     */
    private void printLineItem(PrintRequest.PrintItem item) throws IOException {
        String line = item.getContent();
        if (line == null || line.isEmpty()) {
            line = "----------------------------------------";
        }

        escpos.write(line);
        escpos.feed(1);
    }

    /**
     * Cut paper
     */
    @Override
    public void cutPaper() throws IOException {
        if (!isReady()) {
            throw new IOException("Printer is not ready");
        }

        printerLock.lock();
        try {
            if (dummyMode) {
                logger.info("[DUMMY] Cut paper");
                return;
            }

            escpos.feed(3);  // Feed a few lines before cutting
            escpos.cut(EscPos.CutMode.FULL);
            outputStream.flush();
            logger.debug("Paper cut executed");
        } finally {
            printerLock.unlock();
        }
    }

    /**
     * Update printer status
     * Note: Basic implementation - will be enhanced with ESC/POS status queries in Phase 3
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
                newStatus.setDummyMode(true);
            } else if (state == PrinterState.READY && escpos != null) {
                // TODO Phase 3: Implement ESC/POS status queries (DLE EOT commands)
                // For now, assume connected = online
                newStatus.setOnline(true);
                newStatus.setCoverOpen(false);
                newStatus.setPaperEmpty(false);
                newStatus.setPaperNearEnd(false);
                newStatus.setPowerState(1);
                newStatus.setError(false);
                newStatus.setErrorMessage(null);
                newStatus.setDummyMode(false);
            } else {
                newStatus.setOnline(false);
                newStatus.setError(true);
                newStatus.setErrorMessage("Printer not in READY state: " + state);
                newStatus.setDummyMode(false);
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
     * Check if printer is ready for operations
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

            if (state == PrinterState.READY) {
                cleanup();
                logger.info("Printer closed successfully");
            }

            currentStatus.setOnline(false);

        } finally {
            printerLock.unlock();
        }
    }
}
