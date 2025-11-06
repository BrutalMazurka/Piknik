package pik.domain.thprinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background service to monitor printer status periodically
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class StatusMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(StatusMonitorService.class);

    private final PrinterService printerService;
    private final Consumer<String> statusUpdateCallback;
    private ScheduledFuture<?> monitoringTask;
    private boolean monitoring = false;         // No need to be volatile because startMonitoring() is synchronized
    private final int checkIntervalMs;

    public StatusMonitorService(PrinterService printerService, Consumer<String> statusUpdateCallback, int checkIntervalMs) {
        this.printerService = printerService;
        this.statusUpdateCallback = statusUpdateCallback;
        this.checkIntervalMs = checkIntervalMs;
    }

    /**
     * Start status monitoring
     */
    public synchronized void startMonitoring(ScheduledExecutorService executor) {
        if (monitoring) {
            logger.warn("Status monitoring is already running");
            return;
        }
        logger.info("Starting printer status monitoring");
        monitoring = true;

        // Schedule periodic status checks
        monitoringTask = executor.scheduleWithFixedDelay(
                this::checkStatus,
                0,
                checkIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("Status monitoring started");
    }

    /**
     * Stop status monitoring
     */
    public synchronized void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        logger.info("Stopping printer status monitoring");
        monitoring = false;

        if (monitoringTask != null) {
            monitoringTask.cancel(true);
            monitoringTask = null;
        }

        logger.info("Status monitoring stopped");
    }

    /**
     * Check printer status and send updates
     */
    private void checkStatus() {
        try {
            printerService.updatePrinterStatus();
        } catch (Exception e) {
            logger.error("Error during status check", e);

            // Send error notification via SSE
            String errorMessage = String.format(
                    "data: {\"error\": true, \"message\": \"Status check failed: %s\"}\n\n",
                    e.getMessage()
            );
            statusUpdateCallback.accept(errorMessage);
        }
    }

    /**
     * Check if monitoring is active
     */
    public boolean isMonitoring() {
        return monitoring;
    }
}
