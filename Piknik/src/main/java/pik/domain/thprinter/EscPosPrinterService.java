package pik.domain.thprinter;

import com.fazecast.jSerialComm.SerialPort;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.image.*;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.EPrinterType;
import pik.common.TM_T20IIIConstants;
import pik.dal.PrinterConfig;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
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

    // Use CP852 (DOS Latin 2) for Czech and other Central European languages
    // This code page is widely supported by ESC/POS printers
    // Supports: Czech, Slovak, Polish, Hungarian, etc.
    private static final Charset PRINTER_CHARSET = Charset.forName("cp852");

    private final PrinterConfig config;
    private final List<IPrinterStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock printerLock = new ReentrantLock();
    private final Gson gson = new Gson();

    private Socket socket;
    private SerialPort serialPort;
    private OutputStream outputStream;      // Wrapped by EscPos library (for printing)
    private OutputStream rawOutputStream;   // Direct stream (for status queries)
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
                rawOutputStream = createOutputStream();
                logger.debug("Raw output stream created");

                // Keep raw stream for status queries, wrap for printing
                outputStream = rawOutputStream;
                transitionTo(PrinterState.OPENED);

                // Create EscPos instance (wraps the stream)
                escpos = new EscPos(outputStream);
                logger.debug("EscPos instance created (stream is now wrapped)");

                // Set character code table to CP852 for Czech/Central European characters
                escpos.setCharacterCodeTable(EscPos.CharacterCodeTable.CP852_Latin2);
                logger.debug("Character code table set to CP852 (Latin 2)");
                transitionTo(PrinterState.ENABLED);

                // Initialize printer
                escpos.initializePrinter();
                logger.debug("Printer initialized");

                // Send additional ESC/POS command to ensure code page is selected
                configureCharacterEncoding();

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
     * Uses direct socket connection instead of TcpIpOutputStream to avoid "pipe broken" errors
     */
    private OutputStream createOutputStream() throws IOException {
        return switch (config.connectionType()) {
            case NETWORK -> {
                logger.info("Connecting to network printer: {}:{}", config.ipAddress(), config.networkPort());
                // Use direct Socket connection instead of TcpIpOutputStream
                // This avoids the "Read end dead" / "Pipe broken" errors from TcpIpOutputStream's internal piping
                socket = new Socket(config.ipAddress(), config.networkPort());
                socket.setKeepAlive(true);
                socket.setSoTimeout(config.connectionTimeout());
                logger.debug("Socket connected successfully to {}:{}", config.ipAddress(), config.networkPort());
                yield socket.getOutputStream();
            }
            case USB -> {
                logger.info("Connecting to USB/Serial printer: {} at {} baud", config.comPort(), config.baudRate());
                // Use jSerialComm to open serial port
                serialPort = SerialPort.getCommPort(config.comPort());
                serialPort.setBaudRate(config.baudRate());
                serialPort.setNumDataBits(8);
                serialPort.setNumStopBits(1);
                serialPort.setParity(SerialPort.NO_PARITY);

                if (!serialPort.openPort()) {
                    throw new IOException("Failed to open serial port: " + config.comPort());
                }

                yield serialPort.getOutputStream();
            }
            case NONE -> throw new IOException("Cannot create output stream for NONE connection type");
        };
    }

    /**
     * Configure character encoding for proper display of Czech and Central European characters
     * Sets code page to Windows-1250 (CP1250) which supports: č, ř, š, ž, ý, á, í, é, etc.
     */
    private void configureCharacterEncoding() throws IOException {
        try {
            // ESC t n - Select character code table
            // n = 18: Code page 852 (Latin 2) - better hardware support
            // This supports Czech, Slovak, Polish, Hungarian characters
            outputStream.write(new byte[]{0x1B, 0x74, 0x12}); // ESC t 18 (CP852)
            outputStream.flush();
            logger.debug("Character encoding configured to CP852 (Latin 2)");
        } catch (IOException e) {
            logger.warn("Failed to configure character encoding: {}", e.getMessage());
            // Non-critical - continue without proper encoding
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

            // Note: outputStream and rawOutputStream point to same object
            // Close via outputStream if EscPos was created, otherwise via rawOutputStream
            if (outputStream != null) {
                try {
                    outputStream.close();
                    logger.debug("Output stream closed");
                } catch (Exception e) {
                    logger.debug("Error closing output stream: {}", e.getMessage());
                }
                outputStream = null;
                rawOutputStream = null;  // Same object, already closed
            } else if (rawOutputStream != null) {
                try {
                    rawOutputStream.close();
                    logger.debug("Raw output stream closed");
                } catch (Exception e) {
                    logger.debug("Error closing raw output stream: {}", e.getMessage());
                }
                rawOutputStream = null;
            }

            if (socket != null) {
                try {
                    socket.close();
                    logger.debug("Socket closed");
                } catch (Exception e) {
                    logger.debug("Error closing socket: {}", e.getMessage());
                }
                socket = null;
            }

            if (serialPort != null) {
                try {
                    serialPort.closePort();
                    logger.debug("Serial port closed");
                } catch (Exception e) {
                    logger.debug("Error closing serial port: {}", e.getMessage());
                }
                serialPort = null;
            }

        } catch (Exception e) {
            logger.error("Unexpected error during cleanup", e);
        } finally {
            transitionTo(PrinterState.UNINITIALIZED);
        }
    }

    /**
     * Check if the current connection to the printer is healthy
     * @return true if connection is healthy, false otherwise
     */
    private boolean isConnectionHealthy() {
        if (dummyMode) {
            return true;
        }

        try {
            if (socket != null) {
                // Check if socket is connected and not closed
                if (socket.isClosed() || !socket.isConnected()) {
                    logger.debug("Socket is closed or not connected");
                    return false;
                }

                // Try to check if remote end is still reachable
                // Send Keep-Alive probe by checking socket status
                if (socket.isInputShutdown() || socket.isOutputShutdown()) {
                    logger.debug("Socket input or output is shut down");
                    return false;
                }

                return true;
            } else if (serialPort != null) {
                // Check if serial port is still open
                return serialPort.isOpen();
            }

            // No valid connection
            return false;

        } catch (Exception e) {
            logger.debug("Exception while checking connection health: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Attempt to reconnect to the printer
     * @throws IOException if reconnection fails
     */
    private void attemptReconnection() throws IOException {
        logger.info("Attempting to reconnect to printer...");

        // Clean up existing connection
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                logger.debug("Error closing old socket: {}", e.getMessage());
            }
            socket = null;
        }

        if (serialPort != null) {
            try {
                serialPort.closePort();
            } catch (Exception e) {
                logger.debug("Error closing old serial port: {}", e.getMessage());
            }
            serialPort = null;
        }

        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (Exception e) {
                logger.debug("Error closing old output stream: {}", e.getMessage());
            }
            outputStream = null;
            rawOutputStream = null;  // Same object
        }

        if (escpos != null) {
            try {
                escpos.close();
            } catch (Exception e) {
                logger.debug("Error closing old EscPos: {}", e.getMessage());
            }
            escpos = null;
        }

        // Attempt to create new connection
        try {
            rawOutputStream = createOutputStream();
            outputStream = rawOutputStream;  // Both point to same stream
            escpos = new EscPos(outputStream);

            // Reconfigure character encoding
            escpos.setCharacterCodeTable(EscPos.CharacterCodeTable.CP852_Latin2);
            configureCharacterEncoding();

            logger.info("Successfully reconnected to printer");
        } catch (IOException e) {
            logger.error("Failed to reconnect to printer: {}", e.getMessage());
            // Clean up partial initialization
            if (escpos != null) {
                try { escpos.close(); } catch (Exception ex) {}
                escpos = null;
            }
            if (outputStream != null) {
                try { outputStream.close(); } catch (Exception ex) {}
                outputStream = null;
                rawOutputStream = null;
            }
            throw e;
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

        // Apply line spacing if specified using raw ESC/POS commands
        if (options != null && options.getLineSpacing() != 30) {
            // ESC 3 n - Set line spacing to n/180 inch
            outputStream.write(new byte[]{0x1B, 0x33, (byte) options.getLineSpacing()});
        }

        escpos.write(text);
        if (!text.endsWith("\n")) {
            escpos.feed(1);
        }

        // Reset line spacing to default using raw ESC/POS commands
        if (options != null && options.getLineSpacing() != 30) {
            outputStream.write(new byte[]{0x1B, 0x33, 0x1E}); // Reset to 30
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

            // Apply font size (map 1-8 to Style.FontSize enum)
            if (opts.getFontSize() > TM_T20IIIConstants.MIN_FONT_SIZE) {
                int size = Math.min(opts.getFontSize(), TM_T20IIIConstants.MAX_FONT_SIZE);
                // Map 1-8 to Style.FontSize enum values
                Style.FontSize fontSize = switch (size) {
                    case 1 -> Style.FontSize._1;
                    case 2 -> Style.FontSize._2;
                    case 3 -> Style.FontSize._3;
                    case 4 -> Style.FontSize._4;
                    case 5 -> Style.FontSize._5;
                    case 6 -> Style.FontSize._6;
                    case 7 -> Style.FontSize._7;
                    case 8 -> Style.FontSize._8;
                    default -> Style.FontSize._1;
                };
                style.setFontSize(fontSize, fontSize);
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

            // STEP 3: Convert BufferedImage to CoffeeImage using Bitonal algorithm
            CoffeeImageImpl coffeeImage = new CoffeeImageImpl(image);

            // Apply dithering algorithm (BitonalOrderedDither for better quality)
            Bitonal algorithm = new BitonalOrderedDither();
            EscPosImage escposImage = new EscPosImage(coffeeImage, algorithm);

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

            // STEP 5: Create wrapper and print
            RasterBitImageWrapper imageWrapper = new RasterBitImageWrapper();
            imageWrapper.setJustification(justification);

            // Print the image
            escpos.write(imageWrapper, escposImage);

            logger.debug("Bitmap printed successfully");

        } catch (Exception e) {
            logger.error("Error printing bitmap: {}", e.getMessage(), e);
            throw new IOException("Failed to print bitmap: " + e.getMessage());
        }
    }

    /**
     * Print barcode using raw ESC/POS commands
     */
    private void printBarcodeItem(PrintRequest.PrintItem item) throws IOException {
        try {
            String barcodeData = item.getContent();

            // Center align
            outputStream.write(new byte[]{0x1B, 0x61, 0x01}); // ESC a 1 (center)

            // Set barcode height (default 162 dots = ~50mm)
            outputStream.write(new byte[]{0x1D, 0x68, 0x50}); // GS h 80

            // Set barcode width (default 3)
            outputStream.write(new byte[]{0x1D, 0x77, 0x03}); // GS w 3

            // Set HRI position (2 = below barcode)
            outputStream.write(new byte[]{0x1D, 0x48, 0x02}); // GS H 2

            // Print Code128 barcode
            // GS k 73 n data (73 = CODE128)
            // Use CP852 encoding for barcode data
            byte[] data = barcodeData.getBytes(PRINTER_CHARSET);
            outputStream.write(0x1D); // GS
            outputStream.write(0x6B); // k
            outputStream.write(73);   // CODE128
            outputStream.write(data.length); // length
            outputStream.write(data); // data

            // Reset alignment to left
            outputStream.write(new byte[]{0x1B, 0x61, 0x00}); // ESC a 0

            outputStream.flush();
            logger.debug("Barcode printed: {}", barcodeData);

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
     * Update printer status by querying device using ESC/POS DLE EOT commands
     */
    public void updatePrinterStatus() {
        printerLock.lock();
        try {
            PrinterStatus newStatus = new PrinterStatus();

            if (dummyMode) {
                // Dummy mode status
                newStatus.setOnline(true);
                newStatus.setReady(true);
                newStatus.setCoverOpen(false);
                newStatus.setPaperEmpty(false);
                newStatus.setPaperNearEnd(false);
                newStatus.setPowerState(1);
                newStatus.setError(false);
                newStatus.setErrorMessage("Running in dummy mode");
                newStatus.setDummyMode(true);
            } else if (state == PrinterState.READY && escpos != null) {
                try {
                    // First check if connection is still healthy
                    if (!isConnectionHealthy()) {
                        logger.warn("Connection to printer is not healthy, attempting reconnect...");
                        attemptReconnection();
                    }

                    // Query printer status using DLE EOT commands
                    queryPrinterStatus(newStatus);
                } catch (Exception e) {
                    logger.error("Failed to query printer status: {}", e.getMessage());
                    // Mark printer as offline when status query fails
                    newStatus.setOnline(false);
                    newStatus.setReady(false);
                    newStatus.setError(true);
                    newStatus.setErrorMessage("Printer communication failed: " + e.getMessage());
                    newStatus.setPowerState(0);

                    // Attempt immediate reconnection on connection errors
                    // When printer turns off/on, socket becomes stale with "Connection reset by peer"
                    if (e instanceof IOException) {
                        try {
                            logger.info("Connection error detected, attempting immediate reconnection...");
                            attemptReconnection();
                            logger.info("Reconnection successful - status will be updated on next check");
                        } catch (Exception reconnectError) {
                            logger.error("Reconnection failed: {}", reconnectError.getMessage());
                            logger.debug("Will retry reconnection on next status check");
                        }
                    }
                }
                newStatus.setDummyMode(false);
            } else {
                newStatus.setOnline(false);
                newStatus.setReady(false);
                newStatus.setError(true);
                newStatus.setPowerState(0);

                // Handle inconsistent state: READY but escpos is null (from failed reconnection)
                if (state == PrinterState.READY && escpos == null) {
                    logger.info("Detected READY state with null escpos (failed reconnection), retrying...");
                    try {
                        attemptReconnection();
                        logger.info("Reconnection successful - status will be updated on next check");
                        newStatus.setErrorMessage("Reconnecting to printer...");
                    } catch (Exception reconnectError) {
                        logger.error("Reconnection attempt failed: {}", reconnectError.getMessage());
                        newStatus.setErrorMessage("Printer disconnected: " + reconnectError.getMessage());
                    }
                } else {
                    newStatus.setErrorMessage("Printer not in READY state: " + state);
                }

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
     * Query printer status using ESC/POS commands
     * Uses GS a (ASB) for online/offline and cover detection (more reliable than DLE EOT for TM-T20III)
     * Uses DLE EOT only for paper sensors and error details
     */
    private void queryPrinterStatus(PrinterStatus status) throws IOException {
        try {
            // Query online/offline and cover status using GS a (ASB)
            // ASB is more reliable than DLE EOT for TM-T20III
            // This is CRITICAL - if ASB fails, we bail out
            queryStatusASB(status);

            // DLE EOT queries are BEST-EFFORT only (not critical)
            // TM-T20III often stops responding to certain DLE EOT commands after events like cover open
            // If these fail, we log and continue rather than marking printer offline

            // Query 2: Offline status (DLE EOT 2) - SKIP COVER BIT, use other bits
            try {
                byte offlineStatusByte = queryRealTimeStatus((byte) 0x02);
                parseOfflineStatusNoCover(offlineStatusByte, status);
            } catch (IOException e) {
                logger.debug("DLE EOT 2 query failed (non-critical), skipping: {}", e.getMessage());
            }

            // Query 3: Error status (DLE EOT 3)
            try {
                byte errorStatusByte = queryRealTimeStatus((byte) 0x03);
                parseErrorStatus(errorStatusByte, status);
            } catch (IOException e) {
                logger.debug("DLE EOT 3 query failed (non-critical), skipping: {}", e.getMessage());
            }

            // Query 4: Paper sensor status (DLE EOT 4)
            try {
                byte paperStatusByte = queryRealTimeStatus((byte) 0x04);
                parsePaperStatus(paperStatusByte, status);
            } catch (IOException e) {
                logger.debug("DLE EOT 4 query failed (non-critical), skipping: {}", e.getMessage());
            }

            // Calculate ready status: can printer accept print jobs?
            // Ready = online (network reachable) + cover closed + paper available + no errors
            boolean ready = status.isOnline()
                         && !status.isCoverOpen()
                         && !status.isPaperEmpty()
                         && !status.isError();
            status.setReady(ready);

            // Set power state based on final online status
            if (status.isOnline() && !status.isError()) {
                status.setPowerState(1);
            } else {
                status.setPowerState(0);
            }

            logger.trace("Status calculation: online={}, ready={}, coverOpen={}, paperEmpty={}, error={}",
                       status.isOnline(), status.isReady(), status.isCoverOpen(),
                       status.isPaperEmpty(), status.isError());

        } catch (IOException e) {
            // Only ASB failures reach here (DLE EOT failures are caught above)
            logger.error("I/O error during status query: {}", e.getMessage());
            status.setOnline(false);
            status.setReady(false);
            status.setError(true);
            status.setErrorMessage("Communication error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Query printer status using GS a (Automatic Status Back)
     * This is more reliable than DLE EOT for online/offline and cover detection on TM-T20III
     *
     * ASB Byte 1 bit mapping (per TM-T20III spec):
     * Bit 3: 0=Online | 1=Offline
     * Bit 5: 0=Cover closed | 1=Cover open
     *
     * @param status PrinterStatus object to update
     * @throws IOException if communication fails
     */
    private void queryStatusASB(PrinterStatus status) throws IOException {
        try {
            // Step 1: Disable ASB first to ensure clean state
            rawOutputStream.write(0x1D); // GS
            rawOutputStream.write(0x61); // a
            rawOutputStream.write(0);    // n=0 (disable)
            rawOutputStream.flush();
            Thread.sleep(50); // Brief pause to ensure command is processed

            // Step 2: Clear input buffer of any stale data
            if (socket != null) {
                InputStream inputStream = socket.getInputStream();
                int available = inputStream.available();
                if (available > 0) {
                    byte[] discard = new byte[available];
                    inputStream.read(discard);
                    logger.trace("Cleared {} stale bytes from input buffer", available);
                }
            } else if (serialPort != null) {
                int available = serialPort.bytesAvailable();
                if (available > 0) {
                    byte[] discard = new byte[available];
                    serialPort.readBytes(discard, available);
                    logger.trace("Cleared {} stale bytes from input buffer", available);
                }
            }

            // Step 3: Enable ASB to get current status
            // GS a n where n = 79 enables all statuses (bits 0,1,2,3,6)
            rawOutputStream.write(0x1D); // GS
            rawOutputStream.write(0x61); // a
            rawOutputStream.write(79);   // n (enable all)
            rawOutputStream.flush();

            logger.trace("Sent GS a 79 to enable ASB");

            // Wait for 4-byte ASB response
            Thread.sleep(100);

            // Read 4-byte ASB status
            byte[] asbResponse = new byte[4];
            int bytesRead = 0;

            if (socket != null) {
                InputStream inputStream = socket.getInputStream();
                int available = inputStream.available();

                if (available >= 4) {
                    bytesRead = inputStream.read(asbResponse, 0, 4);
                }
            } else if (serialPort != null) {
                int available = serialPort.bytesAvailable();
                if (available >= 4) {
                    bytesRead = serialPort.readBytes(asbResponse, 4);
                }
            }

            if (bytesRead == 4) {
                byte byte1 = asbResponse[0];

                // Validate ASB response pattern (should be 0xxx1xx0 per spec)
                // Bits 0, 4, 7 should be: 0, 1, 0 respectively (bit 1 varies with status)
                boolean validPattern = ((byte1 & 0x01) == 0) &&  // bit 0 = 0
                                     ((byte1 & 0x10) != 0) &&  // bit 4 = 1
                                     ((byte1 & 0x80) == 0);    // bit 7 = 0

                if (!validPattern) {
                    logger.warn("ASB: Invalid response pattern byte1=0x{} (expected 0xxx1xx0 pattern, bits 0/4/7 fixed)",
                               String.format("%02X", byte1));
                    throw new IOException("Invalid ASB response pattern - input buffer may be corrupted");
                }

                // Parse byte 1, bit 3 for online/offline status
                boolean offline = (byte1 & 0x08) != 0;
                status.setOnline(!offline);

                // Parse byte 1, bit 5 for cover status
                boolean coverOpen = (byte1 & 0x20) != 0;
                status.setCoverOpen(coverOpen);

                // Log combined status
                logger.debug("ASB: byte1=0x{}, online={}, cover={}",
                           String.format("%02X", byte1),
                           !offline ? "yes" : "no",
                           coverOpen ? "open" : "closed");

                // Set error state based on cover and online status
                if (coverOpen) {
                    // Cover is open
                    // We successfully got ASB response, so printer IS network-reachable
                    // (we just communicated with it!)
                    logger.info("Cover detected as OPEN - overriding online status to TRUE (got ASB response)");
                    status.setOnline(true);  // Network reachable (we got ASB response)
                    status.setError(true);
                    status.setErrorMessage("Cover open (printer reachable)");
                    logger.debug("Cover open but printer is network-reachable (got ASB response)");
                } else if (offline) {
                    // Offline but cover closed - some other issue
                    logger.info("ASB reports OFFLINE with cover closed");
                    status.setOnline(false);
                    status.setError(true);
                    status.setErrorMessage("Printer offline");
                } else {
                    // Online and cover closed - normal state
                    logger.debug("Printer online and cover closed - normal state");
                    status.setOnline(true);
                }
            } else {
                logger.info("ASB: No response or incomplete response ({} bytes) - testing network reachability", bytesRead);

                // No ASB response - printer may be offline, cover open, or disconnected
                // TM-T20III stops responding to commands when cover is open
                // To distinguish between "cover open" and "powered off", test network reachability

                boolean networkReachable = false;
                if (config.printerType() == EPrinterType.NETWORK && socket != null && !socket.isClosed()) {
                    // Socket is already connected - printer IS network reachable
                    // (we just can't get ASB response, likely because cover is open)
                    logger.info("ASB: No response but TCP socket is connected - printer likely has cover open");
                    networkReachable = true;
                }

                if (networkReachable) {
                    // Printer is network reachable but not responding to commands
                    // Most likely cause: cover is open
                    logger.info("ASB: Setting Online=Yes (network reachable), Ready=No (not responding to commands)");
                    status.setOnline(true);  // Network reachable
                    status.setCoverOpen(true);  // Assume cover open (most common reason for no response)
                    status.setError(true);
                    status.setErrorMessage("Cover open (printer reachable but not responding)");
                } else {
                    // Printer is truly offline (not network reachable)
                    logger.info("ASB: Setting Online=No (not network reachable)");
                    status.setOnline(false);
                    status.setCoverOpen(false);  // Can't determine cover state
                    status.setError(true);
                    status.setErrorMessage("Printer offline (not responding)");
                }

                // Throw exception to stop further queries (DLE EOT will also fail)
                throw new IOException("Printer not responding to ASB query - cover may be open or printer disconnected");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ASB query interrupted", e);
        } finally {
            // ALWAYS disable ASB, even on error, to prevent async transmissions
            try {
                rawOutputStream.write(0x1D); // GS
                rawOutputStream.write(0x61); // a
                rawOutputStream.write(0);    // n=0 (disable)
                rawOutputStream.flush();
                logger.trace("Sent GS a 0 to disable ASB");
            } catch (Exception e) {
                logger.debug("Failed to disable ASB (connection may be broken): {}", e.getMessage());
            }
        }
    }

    /**
     * Send DLE EOT command and read response byte
     * DLE EOT n (0x10 0x04 n)
     *
     * @param statusType Type of status to query (1-4)
     * @return Status byte response from printer
     * @throws IOException if communication fails
     */
    private byte queryRealTimeStatus(byte statusType) throws IOException {
        try {
            // Verify connection before attempting I/O
            if (socket != null && (socket.isClosed() || !socket.isConnected())) {
                throw new IOException("Socket is closed or not connected");
            }

            // Send DLE EOT n through RAW stream (not EscPos-wrapped stream)
            // This is critical - EscPos library might buffer/intercept wrapped stream
            rawOutputStream.write(0x10); // DLE
            rawOutputStream.write(0x04); // EOT
            rawOutputStream.write(statusType); // n (1-4)
            rawOutputStream.flush();

            logger.trace("Sent DLE EOT {} via raw stream", statusType);

            // Read 1 byte response with timeout
            // According to ESC/POS spec, printer should respond immediately to DLE EOT
            try {
                // Wait for response (increased from 50ms to 200ms for reliability)
                Thread.sleep(200);

                if (socket != null) {
                    InputStream inputStream = socket.getInputStream();

                    // Check if data is available
                    int available = inputStream.available();
                    if (available > 0) {
                        int byteRead = inputStream.read();
                        if (byteRead == -1) {
                            throw new IOException("End of stream reached - printer disconnected");
                        }
                        logger.debug("DLE EOT {} response: 0x{}", statusType, String.format("%02X", (byte)byteRead));
                        return (byte) byteRead;
                    } else {
                        // No data available after waiting 200ms
                        // According to ESC/POS spec, printer should respond immediately to DLE EOT
                        // No response indicates printer is off, disconnected, or not responding
                        logger.warn("DLE EOT {} no response - printer may be offline", statusType);
                        throw new IOException("No response to DLE EOT " + statusType + " - printer offline or not responding");
                    }

                } else if (serialPort != null) {
                    int available = serialPort.bytesAvailable();
                    if (available > 0) {
                        byte[] buffer = new byte[1];
                        int bytesRead = serialPort.readBytes(buffer, 1);
                        if (bytesRead <= 0) {
                            throw new IOException("Failed to read from serial port");
                        }
                        logger.debug("DLE EOT {} response: 0x{}", statusType, String.format("%02X", buffer[0]));
                        return buffer[0];
                    } else {
                        logger.warn("DLE EOT {} no response - printer may be offline", statusType);
                        throw new IOException("No response to DLE EOT " + statusType + " - printer offline or not responding");
                    }
                } else {
                    throw new IOException("No valid connection (socket and serialPort are both null)");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Status query interrupted", e);
            }

        } catch (IOException e) {
            logger.error("Failed to query real-time status type {}: {}", statusType, e.getMessage());
            throw e;
        }
    }

    /**
     * Check if printer is network-reachable by testing TCP connection to ESC/POS port
     * This is used to distinguish "cover open" from "printer powered off/disconnected"
     *
     * @param host Printer IP address
     * @param port Printer port (typically 9100 for ESC/POS)
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if port is reachable (printer is powered on and connected), false otherwise
     */
    private boolean isNetworkReachable(String host, int port, int timeoutMs) {
        try {
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(host, port), timeoutMs);
            testSocket.close();
            logger.trace("Network reachability check: {}:{} is reachable", host, port);
            return true;
        } catch (IOException e) {
            logger.trace("Network reachability check: {}:{} is not reachable ({})", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * Parse printer status byte (DLE EOT 1)
     * Per TM-T20III spec:
     * Bit 2: Drawer kick-out connector pin 3 status
     * Bit 3: 0=Online | 1=Offline
     * Bit 5: 0=Not waiting for recovery | 1=Waiting for recovery
     * Bit 6: 0=Feed button not pressed | 1=Feed button pressed
     */
    private void parsePrinterStatus(byte statusByte, PrinterStatus status) {
        // Bit 3: Online/Offline status
        boolean offline = (statusByte & 0x08) != 0;
        if (offline) {
            status.setOnline(false);
            status.setError(true);
            status.setErrorMessage("Printer offline");
            logger.debug("Printer status: offline");
        }
    }

    /**
     * Parse offline status byte (DLE EOT 2) - WITHOUT cover bit
     * Cover detection is handled by GS a (ASB) which is more reliable
     * Per TM-T20III spec:
     * Bit 2: 0=Cover closed | 1=Cover open (IGNORED - use ASB instead)
     * Bit 3: 0=No paper feed | 1=Paper feed active
     * Bit 5: 0=Paper available | 1=Paper end detected
     * Bit 6: 0=No error | 1=Error occurred
     */
    private void parseOfflineStatusNoCover(byte statusByte, PrinterStatus status) {
        // Bit 2: Cover open - IGNORED (unreliable on TM-T20III, use ASB instead)
        // Cover detection is handled by queryCoverStatusASB()

        // Bit 5: Paper end (NOT bit 6!)
        boolean paperEnd = (statusByte & 0x20) != 0;
        if (paperEnd) {
            status.setPaperEmpty(true);
            // Note: Paper empty does NOT mean printer is offline (network unreachable)
            // Online status is determined by ASB query only
            status.setError(true);
            status.setErrorMessage("Paper end");
            logger.debug("Offline status: paper end detected");
        }

        // Bit 6: Error occurred
        boolean errorOccurred = (statusByte & 0x40) != 0;
        if (errorOccurred) {
            status.setError(true);
            if (status.getErrorMessage() == null) {
                status.setErrorMessage("Offline error");
            }
            logger.debug("Offline status: error occurred");
        }
    }

    /**
     * Parse error status byte (DLE EOT 3)
     * Per TM-T20III spec:
     * Bit 2: 0=No recoverable error | 1=Recoverable error
     * Bit 3: 0=No autocutter error | 1=Autocutter error
     * Bit 5: 0=No unrecoverable error | 1=Unrecoverable error
     * Bit 6: 0=No auto-recoverable error | 1=Auto-recoverable error
     */
    private void parseErrorStatus(byte statusByte, PrinterStatus status) {
        // Bit 2: Recoverable error (e.g., high head temp)
        boolean recoverableError = (statusByte & 0x04) != 0;

        // Bit 3: Autocutter error
        boolean autocutterError = (statusByte & 0x08) != 0;

        // Bit 5: Unrecoverable error (e.g., ROM/RAM error)
        boolean unrecoverableError = (statusByte & 0x20) != 0;

        // Bit 6: Auto-recoverable error
        boolean autoRecoverableError = (statusByte & 0x40) != 0;

        if (recoverableError || autocutterError || unrecoverableError || autoRecoverableError) {
            status.setError(true);
            // Note: Printer errors do NOT mean printer is offline (network unreachable)
            // Online status is determined by ASB query only

            String errorType;
            if (unrecoverableError) {
                errorType = "Unrecoverable";
            } else if (autocutterError) {
                errorType = "Autocutter";
            } else if (autoRecoverableError) {
                errorType = "Auto-recoverable";
            } else {
                errorType = "Recoverable";
            }

            status.setErrorMessage(errorType + " printer error detected");
            logger.error("Error status: {} error detected", errorType);
        }
    }

    /**
     * Parse paper sensor status byte (DLE EOT 4)
     * Bit 2,3: Paper near end sensor - 00=paper adequate, 11=paper near end
     * Bit 5,6: Paper end sensor - 00=paper present, 11=paper not present
     */
    private void parsePaperStatus(byte statusByte, PrinterStatus status) {
        // Bits 2-3: Paper near end sensor
        int paperNearEndBits = (statusByte >> 2) & 0x03;
        boolean paperNearEnd = (paperNearEndBits == 0x03);
        status.setPaperNearEnd(paperNearEnd);
        if (paperNearEnd) {
            logger.debug("Paper status: paper near end");
        }

        // Bits 5-6: Paper end sensor
        int paperEndBits = (statusByte >> 5) & 0x03;
        boolean paperEnd = (paperEndBits == 0x03);
        if (paperEnd) {
            status.setPaperEmpty(true);
            status.setError(true);
            status.setErrorMessage("Paper end");
            logger.debug("Paper status: paper end");
        }
    }

    /**
     * Check if printer is ready for operations
     * Returns true if printer is ready to accept print jobs (online + cover closed + paper available + no errors)
     */
    @Override
    public boolean isReady() {
        return state == PrinterState.READY && currentStatus.isReady();
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
