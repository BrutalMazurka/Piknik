package pik.domain.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.ingenico.IngenicoService;
import pik.domain.io.IOGeneral;
import pik.domain.thprinter.PrinterService;
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
    private final PrinterService printerService;
    private final VFDService vfdService;
    private final IngenicoService ingenicoService;
    private final Object ifsfProtProxy;
    private final Object ifsfDevProxyProtProxy;
    private final Object transitProtProxy;
    private final Object ifsfProtCtrl;
    private final Object transitProtCtrl;
    private final Object ioCtrl;

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
     * @param ifsfProtProxy IFSF protocol proxy
     * @param ifsfDevProxyProtProxy IFSF device proxy
     * @param transitProtProxy Transit protocol proxy
     * @param ifsfProtCtrl IFSF protocol controller
     * @param transitProtCtrl Transit protocol controller
     * @param ioCtrl IO controller for periodic checkers
     */
    public ShutdownManager(
            SSEManager sseManager,
            WebServerManager webServerManager,
            StatusMonitorService printerStatusMonitor,
            ScheduledExecutorService executorService,
            IOGeneral ioGeneral,
            PrinterService printerService,
            VFDService vfdService,
            IngenicoService ingenicoService,
            Object ifsfProtProxy,
            Object ifsfDevProxyProtProxy,
            Object transitProtProxy,
            Object ifsfProtCtrl,
            Object transitProtCtrl,
            Object ioCtrl) {
        this.sseManager = sseManager;
        this.webServerManager = webServerManager;
        this.printerStatusMonitor = printerStatusMonitor;
        this.executorService = executorService;
        this.ioGeneral = ioGeneral;
        this.printerService = printerService;
        this.vfdService = vfdService;
        this.ingenicoService = ingenicoService;
        this.ifsfProtProxy = ifsfProtProxy;
        this.ifsfDevProxyProtProxy = ifsfDevProxyProtProxy;
        this.transitProtProxy = transitProtProxy;
        this.ifsfProtCtrl = ifsfProtCtrl;
        this.transitProtCtrl = transitProtCtrl;
        this.ioCtrl = ioCtrl;
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

            // Deinitialize IoCtrl (stops periodic checker loop)
            try {
                if (ioCtrl != null) {
                    ioCtrl.getClass().getMethod("deinit").invoke(ioCtrl);
                    logger.info("IoCtrl shut down");
                }
            } catch (Exception e) {
                logger.error("Error shutting down IoCtrl", e);
            }

            // Deinitialize Ingenico protocol controllers and proxies
            try {
                if (transitProtCtrl != null) {
                    transitProtCtrl.getClass().getMethod("deinit").invoke(transitProtCtrl);
                }
                if (transitProtProxy != null) {
                    transitProtProxy.getClass().getMethod("close").invoke(transitProtProxy);
                }
                if (ifsfProtCtrl != null) {
                    ifsfProtCtrl.getClass().getMethod("deinit").invoke(ifsfProtCtrl);
                }
                if (ifsfDevProxyProtProxy != null) {
                    ifsfDevProxyProtProxy.getClass().getMethod("close").invoke(ifsfDevProxyProtProxy);
                }
                if (ifsfProtProxy != null) {
                    ifsfProtProxy.getClass().getMethod("close").invoke(ifsfProtProxy);
                }
                logger.info("Ingenico protocol controllers and proxies shut down");
            } catch (Exception e) {
                logger.error("Error shutting down Ingenico protocol controllers", e);
            }

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
