package pik.domain.vfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background service to monitor VFD display status periodically
 * Checks connection status and broadcasts updates via SSE
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 21/11/2025
 */
public class VFDStatusMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(VFDStatusMonitorService.class);

    private final VFDService vfdService;
    private final Consumer<String> statusUpdateCallback;
    private ScheduledFuture<?> monitoringTask;
    private boolean monitoring = false;
    private final int checkIntervalMs;

    public VFDStatusMonitorService(VFDService vfdService, Consumer<String> statusUpdateCallback, int checkIntervalMs) {
        this.vfdService = vfdService;
        this.statusUpdateCallback = statusUpdateCallback;
        this.checkIntervalMs = checkIntervalMs;
    }

    /**
     * Start status monitoring
     */
    public synchronized void startMonitoring(ScheduledExecutorService executor) {
        if (monitoring) {
            logger.warn("VFD status monitoring is already running");
            return;
        }
        logger.info("Starting VFD status monitoring (interval: {}ms)", checkIntervalMs);
        monitoring = true;

        // Schedule periodic status checks
        monitoringTask = executor.scheduleWithFixedDelay(
                this::checkStatus,
                0,
                checkIntervalMs,
                TimeUnit.MILLISECONDS
        );

        logger.info("VFD status monitoring started");
    }

    /**
     * Stop status monitoring
     */
    public synchronized void stopMonitoring() {
        if (!monitoring) {
            return;
        }

        logger.info("Stopping VFD status monitoring");
        monitoring = false;

        if (monitoringTask != null) {
            monitoringTask.cancel(true);
            monitoringTask = null;
        }

        logger.info("VFD status monitoring stopped");
    }

    /**
     * Check VFD status and send updates if status changed
     */
    private void checkStatus() {
        try {
            // Get current status
            VFDStatus status = vfdService.getStatus();

            // Note: VFD doesn't have an updateStatus() method like printer,
            // so we just broadcast current status periodically
            // Status changes are typically triggered by operations (display, clear, etc.)

            logger.debug("VFD status check: connected={}, dummyMode={}, error={}",
                status.isConnected(), status.isDummyMode(), status.isError());

        } catch (Exception e) {
            logger.error("Error during VFD status check", e);

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
