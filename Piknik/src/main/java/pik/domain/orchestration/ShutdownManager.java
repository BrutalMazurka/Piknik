package pik.domain.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.ingenico.IngenicoService;
import pik.domain.io.IOGeneral;
import pik.domain.thprinter.EscPosPrinterService;
import pik.domain.thprinter.StatusMonitorService;
import pik.domain.vfd.VFDService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages graceful shutdown of the application
 * @author Martin Sustik <sustik@herman.cz>
 * @since 19/11/2025
 */
public class ShutdownManager {
    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);

    private final SSEManager sseManager;
    private final WebServerManager webServerManager;
    private final StatusMonitorService printerStatusMonitor;
    private final ScheduledExecutorService executorService;
    private final IOGeneral ioGeneral;
    private final EscPosPrinterService printerService;
    private final VFDService vfdService;
    private final IngenicoService ingenicoService;

    private volatile boolean shutdownHookRegistered = false;

    /**
     * Constructor
     * @param sseManager SSE manager
     * @param webServerManager Web server manager
     * @param printerStatusMonitor Printer status monitor
     * @param executorService Executor service
     * @param ioGeneral IO general
     * @param printerService Printer service
     * @param vfdService VFD service
     * @param ingenicoService Ingenico service
     */
    public ShutdownManager(
            SSEManager sseManager,
            WebServerManager webServerManager,
            StatusMonitorService printerStatusMonitor,
            ScheduledExecutorService executorService,
            IOGeneral ioGeneral,
            EscPosPrinterService printerService,
            VFDService vfdService,
            IngenicoService ingenicoService) {
        this.sseManager = sseManager;
        this.webServerManager = webServerManager;
        this.printerStatusMonitor = printerStatusMonitor;
        this.executorService = executorService;
        this.ioGeneral = ioGeneral;
        this.printerService = printerService;
        this.vfdService = vfdService;
        this.ingenicoService = ingenicoService;
    }

    /**
     * Register shutdown hook
     */
    public void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            shutdownHookRegistered = true;
        }
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        logger.info("Shutting down Integrated Controller...");

        try {
            // Stop SSE management tasks
            sseManager.stopSSEManagementTasks();

            // Deinitialize IO General
            ioGeneral.deinit();

            // Close all SSE clients
            sseManager.closeAllClients();

            // Stop web server
            webServerManager.stop();

            // Stop printer monitoring
            if (printerStatusMonitor != null) {
                printerStatusMonitor.stopMonitoring();
            }

            // Shutdown executor service
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.error("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Close services
            printerService.close();
            vfdService.close();
            ingenicoService.close();

            logger.info("Application shut down successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
}
