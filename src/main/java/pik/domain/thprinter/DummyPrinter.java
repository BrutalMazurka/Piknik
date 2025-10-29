package pik.domain.thprinter;

import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.PrinterConstants;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dummy Printer - Used when no physical printer is available
 * Ensures application remains functional even without hardware
 * @author Martin Sustik <sustik@herman.cz>
 * @since 29/10/2025
 */
public class DummyPrinter implements IPrinterService {
    private static final Logger logger = LoggerFactory.getLogger(DummyPrinter.class);

    private final List<IPrinterStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final PrinterStatus currentStatus;
    private boolean initialized = false;

    public DummyPrinter() {
        this.currentStatus = new PrinterStatus();
        this.currentStatus.setOnline(true); // Dummy is always "online"
        this.currentStatus.setError(false);
    }

    @Override
    public void initialize() throws JposException {
        logger.info("DummyPrinter initialized (no physical hardware)");
        initialized = true;
        currentStatus.setOnline(true);
        currentStatus.setLastUpdate(System.currentTimeMillis());
        notifyStatusChanged("initialization");
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isReady() {
        return initialized;
    }

    @Override
    public void printText(String text) throws JposException, InterruptedException {
        if (!initialized) {
            throw new JposException(0, "DummyPrinter not initialized");
        }
        logger.debug("DummyPrinter.printText: {}", text.substring(0, Math.min(50, text.length())));
        // Simulate print delay
        Thread.sleep(100);
    }

    @Override
    public void print(PrintRequest request) throws JposException {
        if (!initialized) {
            throw new JposException(0, "DummyPrinter not initialized");
        }

        logger.debug("DummyPrinter.print: Processing request with {} copies", request.getCopies());

        for (int copy = 0; copy < request.getCopies(); copy++) {
            if (request.getText() != null && !request.getText().isEmpty()) {
                logger.debug("  - Text: {}", request.getText().substring(0, Math.min(30, request.getText().length())));
            }

            if (request.getItems() != null) {
                for (PrintRequest.PrintItem item : request.getItems()) {
                    logger.debug("  - Item type: {}, content: {}",
                            item.getType(),
                            item.getContent() != null ? item.getContent().substring(0, Math.min(30, item.getContent().length())) : "null");
                }
            }

            if (request.isCutPaper()) {
                logger.debug("  - Cut paper");
            }
        }

        // Simulate processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void cutPaper() throws JposException {
        if (!initialized) {
            throw new JposException(0, "DummyPrinter not initialized");
        }
        logger.debug("DummyPrinter.cutPaper");
    }

    @Override
    public PrinterStatus getStatus() {
        return new PrinterStatus(currentStatus);
    }

    @Override
    public void close() {
        logger.info("DummyPrinter closed");
        initialized = false;
        currentStatus.setOnline(false);
    }

    /**
     * Add status listener
     */
    public void addStatusListener(IPrinterStatusListener listener) {
        if (listener != null && !statusListeners.contains(listener)) {
            statusListeners.add(listener);
            logger.debug("Added status listener to DummyPrinter: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Remove status listener
     */
    public void removeStatusListener(IPrinterStatusListener listener) {
        statusListeners.remove(listener);
        logger.debug("Removed status listener from DummyPrinter: {}", listener.getClass().getSimpleName());
    }

    /**
     * Notify all observers of status change
     */
    private void notifyStatusChanged(String source) {
        if (statusListeners.isEmpty()) {
            return;
        }

        PrinterStatusEvent event = new PrinterStatusEvent(new PrinterStatus(currentStatus), source);

        for (IPrinterStatusListener listener : statusListeners) {
            try {
                listener.onStatusChanged(event);
            } catch (Exception e) {
                logger.error("Error notifying DummyPrinter listener {}: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Check if this is a dummy printer
     */
    public boolean isDummy() {
        return true;
    }

    /**
     * Update printer status manually (for testing or simulation)
     */
    public void updatePrinterStatus() {
        currentStatus.setLastUpdate(System.currentTimeMillis());
        notifyStatusChanged("manual_update");
    }

    /**
     * Simulate printer events for testing
     */
    public void simulateEvent(String eventType) {
        logger.debug("DummyPrinter simulating event: {}", eventType);

        switch (eventType.toLowerCase()) {
            case "paper_empty":
                currentStatus.setPaperEmpty(true);
                currentStatus.setError(true);
                break;
            case "paper_near_end":
                currentStatus.setPaperNearEnd(true);
                break;
            case "cover_open":
                currentStatus.setCoverOpen(true);
                break;
            case "reset":
                currentStatus.setPaperEmpty(false);
                currentStatus.setPaperNearEnd(false);
                currentStatus.setCoverOpen(false);
                currentStatus.setError(false);
                currentStatus.setErrorMessage(null);
                break;
        }

        currentStatus.setLastUpdate(System.currentTimeMillis());
        notifyStatusChanged("simulation_" + eventType);
    }

    /**
     * Run a demonstration (logs actions)
     */
    public void runDemo() {
        if (!initialized) {
            logger.info("Please initialize DummyPrinter first");
            return;
        }

        try {
            logger.info("\n--- DummyPrinter Demo ---");

            printText("=== DEMO PARAGON ===\n");
            Thread.sleep(PrinterConstants.DEMO_STEP_DELAY_MS);

            printText("Položka 1: 10,00 Kč\n");
            Thread.sleep(PrinterConstants.DEMO_STEP_DELAY_MS);

            printText("Položka 2: 42,00 Kč\n");
            Thread.sleep(PrinterConstants.DEMO_STEP_DELAY_MS);

            printText("Celkem asi tak: 127,50 Kč\n");
            Thread.sleep(PrinterConstants.DEMO_STEP_DELAY_MS);

            cutPaper();
            Thread.sleep(PrinterConstants.DEMO_STEP_DELAY_MS);

            logger.info("Demo complete!");

        } catch (Exception e) {
            logger.error("Demo interrupted: {}", e.getMessage());
        }
    }
}
