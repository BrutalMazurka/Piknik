package pik.domain;

import com.google.gson.Gson;
import com.google.inject.Injector;
import epis5.ingenico.transit.IIngenicoTransitApp;
import epis5.ingenico.transit.prot.TransitHeartBeatOutputter;
import epis5.ingenico.transit.prot.TransitProtCtrl;
import epis5.ingenico.transit.prot.TransitProtProxy;
import epis5.ingenicoifsf.IIngenicoIEmvTerminal;
import epis5.ingenicoifsf.prot.IfsfHeartBeatOutputter;
import epis5.ingenicoifsf.prot.IfsfProtCtrl;
import epis5.ingenicoifsf.prot.IfsfProtProxy;
import jCommons.comm.proxy.IOAccessProxySett;
import jCommons.logging.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.ELogger;
import pik.dal.*;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.IngenicoService;
import pik.domain.io.IOGeneral;
import pik.domain.orchestration.SSEManager;
import pik.domain.orchestration.ServiceOrchestrator;
import pik.domain.orchestration.ShutdownManager;
import pik.domain.orchestration.WebServerManager;
import pik.domain.thprinter.PrinterService;
import pik.domain.thprinter.StatusMonitorService;
import pik.domain.vfd.VFDService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Integrated application managing Thermal Printer, VFD Display, and Ingenico Reader
 * Orchestrates startup, monitoring, web server, SSE, and shutdown
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class IntegratedController {
    private static final Logger logger = LoggerFactory.getLogger(IntegratedController.class);

    private final ServerConfig serverConfig;
    private final IOGeneral ioGeneral;

    // Services
    private final PrinterService printerService;
    private final VFDService vfdService;
    private final IngenicoService ingenicoService;
    private final StatusMonitorService printerStatusMonitor;

    // Managers
    private final ServiceOrchestrator serviceOrchestrator;
    private final SSEManager sseManager;
    private final WebServerManager webServerManager;
    private final ShutdownManager shutdownManager;

    private final ScheduledExecutorService executorService;

    // Ingenico Card Reader Protocol Controllers
    private final IfsfProtCtrl ifsfProtCtrl;
    private final IfsfProtProxy ifsfProtProxy;
    private final IfsfProtProxy ifsfDevProxyProtProxy;
    private final TransitProtCtrl transitProtCtrl;
    private final TransitProtProxy transitProtProxy;
    private final pik.domain.io.control.IoCtrl ioCtrl;
    private final jCommons.master.MasterLoop masterLoop;

    /**
     * Constructor accepting configurations (no circular dependency)
     * @param printerConfig Printer configuration
     * @param vfdConfig VFD configuration
     * @param ingenicoConfig Ingenico reader configuration
     * @param serverConfig Server configuration
     */
    public IntegratedController(PrinterConfig printerConfig, VFDConfig vfdConfig, IngenicoConfig ingenicoConfig, ServerConfig serverConfig, Injector injector) {
        ILoggerFactory loggerFactory = injector.getInstance(ILoggerFactory.class);

        this.serverConfig = serverConfig;

        // Initialize services (migrated to ESC/POS Coffee library)
        this.printerService = new PrinterService(printerConfig);
        this.vfdService = new VFDService(vfdConfig);
        this.ingenicoService = new IngenicoService(ingenicoConfig, injector.getInstance(IngenicoReaderDevice.class));

        this.executorService = Executors.newScheduledThreadPool(serverConfig.threadPoolSize());
        this.ioGeneral = injector.getInstance(IOGeneral.class);

        // Initialize protocol controllers with registrations
        IOAccessProxySett proxySett;
        proxySett = new IOAccessProxySett(loggerFactory.get(ELogger.INGENICO_IFSF), "IfsfProtProxy");
        ifsfProtProxy = new IfsfProtProxy(proxySett, ioGeneral.getIfsfTcpServerAccess());
        ifsfDevProxyProtProxy = new IfsfProtProxy(proxySett, ioGeneral.getIfsfDevProxyTcpServerAccess());
        ifsfProtCtrl = new IfsfProtCtrl(ifsfProtProxy, ifsfDevProxyProtProxy,
                injector.getInstance(IIngenicoIEmvTerminal.class),
                new IfsfHeartBeatOutputter(ioGeneral.getIfsfTcpServerAccess()));
        ifsfProtCtrl.setLogAll(true);

        proxySett = new IOAccessProxySett(loggerFactory.get(ELogger.INGENICO_TRANSIT), "TransitProtProxy");
        transitProtProxy = new TransitProtProxy(proxySett, ioGeneral.getTransitTcpServerAccess());
        transitProtCtrl = new TransitProtCtrl(transitProtProxy,
                injector.getInstance(IIngenicoTransitApp.class),
                new TransitHeartBeatOutputter(ioGeneral.getTransitTcpServerAccess()));

        // Create child injector with protocol outputters bound
        // This allows the registration builders to inject the services with their dependencies
        Injector childInjector = injector.createChildInjector(new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                bind(epis5.ingenicoifsf.prot.IIfsfProtMsgOutputter.class).toInstance(ifsfProtCtrl);
                bind(epis5.ingenico.transit.prot.ITransitProtMsgOutputter.class).toInstance(transitProtCtrl);
            }
        });

        // Register Ingenico apps with TCP servers to receive connection events
        IngenicoReaderDevice readerDevice = injector.getInstance(IngenicoReaderDevice.class);
        readerDevice.getIfsfApp().registerToTcpServer(ioGeneral.getIfsfTcpServerAccess());
        readerDevice.getTransitApp().registerToTcpServer(ioGeneral.getTransitTcpServerAccess());

        // Build and register protocol control services (periodic checkers)
        pik.domain.ingenico.ifsf.service.IfsfProtCtrlRegistrationBuilder ifsfRegBuilder =
                new pik.domain.ingenico.ifsf.service.IfsfProtCtrlRegistrationBuilder(childInjector);
        ifsfProtCtrl.init(ifsfRegBuilder.build());

        pik.domain.ingenico.transit.service.TransitProtCtrlRegistrationBuilder transitRegBuilder =
                new pik.domain.ingenico.transit.service.TransitProtCtrlRegistrationBuilder(childInjector);
        transitProtCtrl.init(transitRegBuilder.build());

        // Open protocol proxies to enable message handling
        ifsfProtProxy.open();
        ifsfDevProxyProtProxy.open();
        transitProtProxy.open();

        // Initialize IoCtrl for other periodic checkers (not Ingenico services)
        this.ioCtrl = new pik.domain.io.control.IoCtrl(childInjector);
        this.ioCtrl.init(new pik.domain.io.control.IoCtrlRegistrationBuilder(childInjector).build());

        // Create MasterLoop to run protocol controllers (like EVK)
        this.masterLoop = new jCommons.master.MasterLoop(loggerFactory.get(ELogger.APP), 2, "MasterLoop_prot");

        // Register protocol proxies and controllers with MasterLoop
        // When MasterLoop runs, it calls run() on each registered Runnable
        // The controllers' run() methods call periodicalCheck() on their registered services
        masterLoop.registerRunnable(ifsfProtProxy);
        masterLoop.registerRunnable(ifsfProtCtrl);
        masterLoop.registerRunnable(transitProtProxy);
        masterLoop.registerRunnable(transitProtCtrl);

        logger.info("Protocol controllers registered with MasterLoop");

        // Initialize managers
        this.sseManager = new SSEManager();
        this.printerStatusMonitor = new StatusMonitorService(printerService, sseManager::broadcastToPrinterSSE, serverConfig.statusCheckInterval());
        this.serviceOrchestrator = new ServiceOrchestrator(printerService, vfdService, ingenicoService, printerStatusMonitor, ioGeneral);
        this.webServerManager = new WebServerManager(serverConfig, printerService, vfdService, ingenicoService, this);
        this.shutdownManager = new ShutdownManager(sseManager, webServerManager, printerStatusMonitor, executorService, ioGeneral,
                printerService, vfdService, ingenicoService, ifsfProtProxy, ifsfDevProxyProtProxy, transitProtProxy, ifsfProtCtrl, transitProtCtrl, ioCtrl, masterLoop);

        // Register observers AFTER manager construction
        setupStatusListeners();

        logger.info("IntegratedController initialized with startup mode: {}", serverConfig.startupMode());
    }

    /**
     * Setup observers for status changes
     */
    private void setupStatusListeners() {
        Gson compactGson = webServerManager.getCompactGson();

        // Printer status observer for SSE - broadcast to PRINTER clients only
        printerService.addStatusListener(event -> {
            String statusJson = compactGson.toJson(event.getStatus());
            String sseMessage = "event: status\ndata: " + statusJson + "\n\n";
            sseManager.broadcastToPrinterSSE(sseMessage);
            logger.debug("Broadcast printer status update to printer SSE clients");
        });

        // VFD status observer for SSE - broadcast to VFD clients only
        vfdService.addStatusListener(event -> {
            String statusJson = compactGson.toJson(event.getStatus());
            String sseMessage = "event: status\ndata: " + statusJson + "\n\n";
            sseManager.broadcastToVFDSSE(sseMessage);
            logger.debug("Broadcast VFD status update to VFD SSE clients");
        });

        // Ingenico status observer for SSE - broadcast to Ingenico clients only
        ingenicoService.addStatusListener(event -> {
            String statusJson = compactGson.toJson(event.getStatus());
            String sseMessage = "event: status\ndata: " + statusJson + "\n\n";
            sseManager.broadcastToIngenicoSSE(sseMessage);
            logger.debug("Broadcast Ingenico status update to Ingenico SSE clients");
        });
    }

    /**
     * Start the integrated application with default startup mode from configuration
     * @throws StartupException if startup requirements are not met
     */
    public void start() throws StartupException {
        start(serverConfig.startupMode());
    }

    /**
     * Start the integrated application with specified startup mode
     * @param mode Startup mode to use
     * @throws StartupException if startup requirements are not met
     */
    public void start(StartupMode mode) throws StartupException {
        logger.info("========================================");
        logger.info("Starting Integrated Controller");
        logger.info("Startup Mode: {}", mode);
        logger.info("========================================");

        try {
            // Step 1: Initialize all services
            List<ServiceInitializationResult> results = serviceOrchestrator.initializeAllServices();

            // Step 2: Evaluate startup success based on mode
            serviceOrchestrator.evaluateStartupRequirements(mode, results);

            // Step 3: Start MasterLoop to run protocol controllers
            masterLoop.start();
            logger.info("MasterLoop started - protocol controllers are now running");

            // Step 4: Start monitoring for successfully initialized services
            serviceOrchestrator.startMonitoring(executorService);

            // Step 5: Start SSE management
            sseManager.startSSEManagementTasks(executorService);

            // Step 6: Start web server
            webServerManager.start();

            // Step 7: Register shutdown hook
            shutdownManager.registerShutdownHook();

            // Step 8: Log final status
            serviceOrchestrator.logStartupSummary(results, serverConfig.port(), serverConfig.host());

        } catch (StartupException e) {
            logger.error("Startup failed: {}", e.getMessage());
            shutdownManager.shutdown();  // Cleanup on failure
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during startup", e);
            shutdownManager.shutdown();
            throw new StartupException("Unexpected startup failure", mode, new ArrayList<>(serviceOrchestrator.getInitializationResults().values()));
        }
    }

    /**
     * Get initialization results (for testing or status endpoints)
     * @return Map of service name to initialization result
     */
    public Map<String, ServiceInitializationResult> getInitializationResults() {
        return serviceOrchestrator.getInitializationResults();
    }

    /**
     * Get printer SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getPrinterSSEClients() {
        return sseManager.getPrinterSSEClients();
    }

    /**
     * Get VFD SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getVFDSSEClients() {
        return sseManager.getVFDSSEClients();
    }

    /**
     * Get Ingenico SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getIngenicoSSEClients() {
        return sseManager.getIngenicoSSEClients();
    }

    /**
     * Unregister a printer SSE client
     */
    public void unregisterPrinterSSEClient(String clientId) {
        sseManager.unregisterPrinterSSEClient(clientId);
    }

    /**
     * Unregister a VFD SSE client
     */
    public void unregisterVFDSSEClient(String clientId) {
        sseManager.unregisterVFDSSEClient(clientId);
    }

    /**
     * Unregister an Ingenico SSE client
     */
    public void unregisterIngenicoSSEClient(String clientId) {
        sseManager.unregisterIngenicoSSEClient(clientId);
    }

    /**
     * Error response DTO
     */
    public static class ErrorResponse {
        private final String error;
        private final String message;
        private final long timestamp;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
