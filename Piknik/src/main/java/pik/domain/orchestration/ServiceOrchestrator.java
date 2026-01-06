package pik.domain.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.StartupMode;
import pik.domain.ServiceInitializationResult;
import pik.domain.StartupException;
import pik.domain.ingenico.IngenicoService;
import pik.domain.io.IOGeneral;
import pik.domain.thprinter.PrinterService;
import pik.domain.thprinter.StatusMonitorService;
import pik.domain.vfd.VFDService;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Orchestrates service initialization and monitoring
 * @author Martin Sustik <sustik@herman.cz>
 * @since 19/11/2025
 */
public class ServiceOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(ServiceOrchestrator.class);

    private final PrinterService printerService;
    private final VFDService vfdService;
    private final IngenicoService ingenicoService;
    private final StatusMonitorService printerStatusMonitor;
    private final IOGeneral ioGeneral;

    private final Map<String, ServiceInitializationResult> initializationResults = new LinkedHashMap<>();

    /**
     * Constructor
     * @param printerService Printer service
     * @param vfdService VFD service
     * @param ingenicoService Ingenico service
     * @param printerStatusMonitor Printer status monitor
     * @param ioGeneral IO general
     */
    public ServiceOrchestrator(
            PrinterService printerService,
            VFDService vfdService,
            IngenicoService ingenicoService,
            StatusMonitorService printerStatusMonitor,
            IOGeneral ioGeneral) {
        this.printerService = printerService;
        this.vfdService = vfdService;
        this.ingenicoService = ingenicoService;
        this.printerStatusMonitor = printerStatusMonitor;
        this.ioGeneral = ioGeneral;
    }

    /**
     * Initialize all services and track results
     * @return List of initialization results
     */
    public List<ServiceInitializationResult> initializeAllServices() {
        List<ServiceInitializationResult> results = new ArrayList<>();

        // Initialize printer service
        ServiceInitializationResult printerResult = initializePrinterService();
        results.add(printerResult);
        initializationResults.put("printer", printerResult);

        // Initialize VFD service
        ServiceInitializationResult vfdResult = initializeVFDService();
        results.add(vfdResult);
        initializationResults.put("vfd", vfdResult);

        // Initialize Ingenico service
        ServiceInitializationResult ingenicoResult = initializeIngenicoService();
        results.add(ingenicoResult);
        initializationResults.put("ingenico", ingenicoResult);

        // Initialize IO General
        ioGeneral.init();

        return results;
    }

    /**
     * Initialize printer service
     * @return Initialization result
     */
    private ServiceInitializationResult initializePrinterService() {
        logger.info("Initializing Printer service...");
        long startTime = System.currentTimeMillis();

        try {
            printerService.initialize();
            long duration = System.currentTimeMillis() - startTime;

            if (printerService.isDummyMode()) {
                logger.warn("⚠ Printer service initialized in DUMMY mode in {}ms (no physical hardware)", duration);
                return ServiceInitializationResult.success("Printer (Dummy Mode)", duration);
            } else {
                logger.info("✓ Printer service initialized successfully in {}ms", duration);
                return ServiceInitializationResult.success("Printer", duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            logger.error("✗ Printer service initialization failed after {}ms: {}", duration, e.getMessage());
            logger.debug("Printer initialization error details", e);

            return ServiceInitializationResult.failure("Printer", e, duration);
        }
    }

    /**
     * Initialize VFD service
     * @return Initialization result
     */
    private ServiceInitializationResult initializeVFDService() {
        logger.info("Initializing VFD service...");
        long startTime = System.currentTimeMillis();

        try {
            vfdService.initialize();
            long duration = System.currentTimeMillis() - startTime;

            if (vfdService.isDummyMode()) {
                logger.warn("⚠ VFD service initialized in DUMMY mode in {}ms (no physical hardware)", duration);
                return ServiceInitializationResult.success("VFD (Dummy Mode)", duration);
            } else {
                logger.info("✓ VFD service initialized successfully in {}ms", duration);
                return ServiceInitializationResult.success("VFD", duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            logger.error("✗ VFD service initialization failed after {}ms: {}", duration, e.getMessage());
            logger.debug("VFD initialization error details", e);

            return ServiceInitializationResult.failure("VFD", e, duration);
        }
    }

    /**
     * Initialize Ingenico service
     * @return Initialization result
     */
    private ServiceInitializationResult initializeIngenicoService() {
        logger.info("Initializing Ingenico reader service...");
        long startTime = System.currentTimeMillis();

        try {
            ingenicoService.initialize();
            long duration = System.currentTimeMillis() - startTime;

            if (ingenicoService.isDummyMode()) {
                logger.warn("⚠ Ingenico service initialized in DUMMY mode in {}ms (no physical hardware)", duration);
                return ServiceInitializationResult.success("Ingenico (Dummy Mode)", duration);
            } else {
                logger.info("✓ Ingenico service initialized successfully in {}ms", duration);
                return ServiceInitializationResult.success("Ingenico", duration);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            logger.error("✗ Ingenico service initialization failed after {}ms: {}", duration, e.getMessage());
            logger.debug("Ingenico initialization error details", e);

            return ServiceInitializationResult.failure("Ingenico", e, duration);
        }
    }

    /**
     * Evaluate if startup requirements are met based on mode
     * @param mode Startup mode
     * @param results Initialization results
     * @throws StartupException if requirements not met
     */
    public void evaluateStartupRequirements(StartupMode mode, List<ServiceInitializationResult> results) throws StartupException {

        long successfulServices = results.stream().filter(ServiceInitializationResult::isSuccess).count();
        long totalServices = results.size();

        logger.info("Service initialization complete: {}/{} services successful",
                successfulServices, totalServices);

        switch (mode) {
            case STRICT:
                if (successfulServices != totalServices) {
                    String message = String.format(
                            "STRICT mode requires all services to initialize. " + "Only %d/%d services initialized successfully.",
                            successfulServices, totalServices
                    );
                    throw new StartupException(message, mode, results);
                }
                logger.info("✓ STRICT mode requirement met: all services initialized");
                break;

            case LENIENT:
                if (successfulServices == 0) {
                    String message = "LENIENT mode requires at least one service to initialize. " + "All services failed to initialize.";
                    throw new StartupException(message, mode, results);
                }
                if (successfulServices < totalServices) {
                    logger.warn("⚠ LENIENT mode: {}/{} services initialized (some services unavailable)", successfulServices, totalServices);
                } else {
                    logger.info("✓ LENIENT mode requirement met: all services initialized");
                }
                break;

            case PERMISSIVE:
                if (successfulServices == 0) {
                    logger.warn("⚠ PERMISSIVE mode: No services initialized - running in degraded mode");
                } else if (successfulServices < totalServices) {
                    logger.info("⚠ PERMISSIVE mode: {}/{} services initialized", successfulServices, totalServices);
                } else {
                    logger.info("✓ PERMISSIVE mode: all services initialized");
                }
                break;
        }
    }

    /**
     * Start monitoring for initialized services
     */
    public void startMonitoring(ScheduledExecutorService executorService) {
        // Start printer monitoring even if initialization failed
        // The monitoring loop will handle reconnection attempts
        ServiceInitializationResult printerResult = initializationResults.get("printer");
        if (printerResult != null) {
            if (printerResult.isSuccess()) {
                logger.info("Starting printer status monitoring (printer initialized)...");
            } else {
                logger.info("Starting printer status monitoring (printer failed init - will attempt reconnection)...");
            }
            printerStatusMonitor.startMonitoring(executorService);
        } else {
            logger.info("Printer monitoring skipped (printer service not found)");
        }
    }

    /**
     * Log startup summary
     */
    public void logStartupSummary(List<ServiceInitializationResult> results, int serverPort, String serverHost) {
        logger.info("========================================");
        logger.info("Startup Complete - Application Status:");
        logger.info("========================================");

        for (ServiceInitializationResult result : results) {
            logger.info(result.toString());
        }

        logger.info("Web Server: RUNNING on port {}", serverPort);
        logger.info("API Documentation: http://{}:{}/docs", serverHost, serverPort);
        logger.info("========================================");
    }

    /**
     * Get initialization results (for testing or status endpoints)
     * @return Map of service name to initialization result
     */
    public Map<String, ServiceInitializationResult> getInitializationResults() {
        return Collections.unmodifiableMap(initializationResults);
    }
}
