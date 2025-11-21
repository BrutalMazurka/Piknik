package pik.domain.ingenico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background service to monitor Ingenico card reader status periodically
 * Checks initialization state, TCP connections, and SAM module status
 * Broadcasts updates via SSE when status changes
 *
 * Important: This complements the RxJava event-driven subscriptions
 * by providing periodic polling to detect stuck states (e.g., STARTING)
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 21/11/2025
 */
public class IngenicoStatusMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(IngenicoStatusMonitorService.class);

    private final IngenicoService ingenicoService;
    private final Consumer<String> statusUpdateCallback;
    private ScheduledFuture<?> monitoringTask;
    private boolean monitoring = false;
    private final int checkIntervalMs;
    private IngenicoStatus lastStatus;

    public IngenicoStatusMonitorService(IngenicoService ingenicoService, Consumer<String> statusUpdateCallback, int checkIntervalMs) {
        this.ingenicoService = ingenicoService;
        this.statusUpdateCallback = statusUpdateCallback;
        this.checkIntervalMs = checkIntervalMs;
    }

    /**
     * Start status monitoring
     */
    public synchronized void startMonitoring(ScheduledExecutorService executor) {
        if (monitoring) {
            logger.warn("Ingenico status monitoring is already running");
            return;
        }
        logger.info("Starting Ingenico status monitoring (interval: {}ms)", checkIntervalMs);
        monitoring = true;

        // Initialize last status
        lastStatus = ingenicoService.getStatus();

        // Schedule periodic status checks
        monitoringTask = executor.scheduleWithFixedDelay(
                this::checkStatus,
                checkIntervalMs,  // Initial delay
                checkIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("Ingenico status monitoring started");
    }

    /**
     * Stop status monitoring
     */
    public synchronized void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        logger.info("Stopping Ingenico status monitoring");
        monitoring = false;

        if (monitoringTask != null) {
            monitoringTask.cancel(true);
            monitoringTask = null;
        }

        logger.info("Ingenico status monitoring stopped");
    }

    /**
     * Check Ingenico status and detect changes
     * This is important for detecting:
     * - Stuck initialization states (e.g., STARTING for extended period)
     * - Connection losses that don't fire RxJava events
     * - SAM module detection failures
     */
    private void checkStatus() {
        try {
            IngenicoStatus currentStatus = ingenicoService.getStatus();

            // Log status for debugging
            if (logger.isDebugEnabled()) {
                logger.debug("Ingenico status check: initState={}, initialized={}, operational={}, " +
                        "ifsfConnected={}, transitConnected={}, samDetected={}",
                    currentStatus.initState(), currentStatus.initialized(), currentStatus.isOperational(),
                    currentStatus.ifsfConnected(), currentStatus.transitConnected(), currentStatus.samDukDetected());
            }

            // Detect if initialization is stuck
            if (lastStatus != null &&
                !currentStatus.dummyMode() &&
                currentStatus.initState() == lastStatus.initState() &&
                currentStatus.initState() != EReaderInitState.DONE) {

                long timeSinceLastUpdate = currentStatus.lastUpdate() - lastStatus.lastUpdate();
                if (timeSinceLastUpdate == 0) {
                    // Status hasn't changed since last check
                    logger.warn("Ingenico reader appears stuck in {} state (no status update since last check)",
                        currentStatus.initState());
                }
            }

            // Check for status changes that should trigger SSE notification
            boolean statusChanged = lastStatus == null || hasSignificantChange(lastStatus, currentStatus);

            if (statusChanged) {
                logger.debug("Ingenico status changed, last update timestamp: {}", currentStatus.lastUpdate());
                // Note: Status changes are already broadcast by IngenicoService via RxJava subscriptions
                // This monitoring primarily serves to detect stuck states
            }

            lastStatus = currentStatus;

        } catch (Exception e) {
            logger.error("Error during Ingenico status check", e);

            // Send error notification via SSE
            String errorMessage = String.format(
                    "data: {\"error\": true, \"message\": \"Status check failed: %s\"}\n\n",
                    e.getMessage()
            );
            statusUpdateCallback.accept(errorMessage);
        }
    }

    /**
     * Check if there's a significant status change worth logging
     */
    private boolean hasSignificantChange(IngenicoStatus oldStatus, IngenicoStatus newStatus) {
        return oldStatus.initState() != newStatus.initState() ||
               oldStatus.initialized() != newStatus.initialized() ||
               oldStatus.isOperational() != newStatus.isOperational() ||
               oldStatus.ifsfConnected() != newStatus.ifsfConnected() ||
               oldStatus.transitConnected() != newStatus.transitConnected() ||
               oldStatus.samDukDetected() != newStatus.samDukDetected() ||
               oldStatus.error() != newStatus.error();
    }

    /**
     * Check if monitoring is active
     */
    public boolean isMonitoring() {
        return monitoring;
    }
}
