package pik.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.PrinterConfig;
import pik.dal.ServerConfig;
import pik.dal.VFDConfig;
import pik.domain.thprinter.PrinterController;
import pik.domain.thprinter.PrinterService;
import pik.domain.thprinter.StatusMonitorService;
import pik.domain.vfd.VFDController;
import pik.domain.vfd.VFDService;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * Integrated application managing both Thermal Printer and VFD Display
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class IntegratedController {
    private static final Logger logger = LoggerFactory.getLogger(IntegratedController.class);

    private final Gson gson;
    private final ServerConfig serverConfig;
    private Javalin javalinApp;

    // Printer components
    private final PrinterService printerService;
    private final StatusMonitorService printerStatusMonitor;

    // VFD components
    private final VFDService vfdService;

    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, SSEClient> sseClients = new ConcurrentHashMap<>();
    private volatile boolean shutdownHookRegistered = false;

    // Initialization results
    private final Map<String, ServiceInitializationResult> initializationResults = new LinkedHashMap<>();

    // SSE management
    private ScheduledFuture<?> sseCleanupTask;
    private ScheduledFuture<?> sseHeartbeatTask;

    /**
     * Constructor accepting configurations (no circular dependency)
     * @param printerConfig Printer configuration
     * @param vfdConfig VFD configuration
     * @param serverConfig Server configuration
     */
    public IntegratedController(PrinterConfig printerConfig, VFDConfig vfdConfig, ServerConfig serverConfig) {
        this.serverConfig = serverConfig;

        // Initialize Gson
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Initialize services
        this.printerService = new PrinterService(printerConfig);
        this.vfdService = new VFDService(vfdConfig);
        this.printerStatusMonitor = new StatusMonitorService(
                printerService,
                this::broadcastToSSE,
                serverConfig.getStatusCheckInterval()
        );
        this.executorService = Executors.newScheduledThreadPool(serverConfig.getThreadPoolSize());

        // Register observers AFTER construction
        setupStatusListeners();

        logger.info("IntegratedController initialized with startup mode: {}", serverConfig.getStartupMode());
    }

    // Setup observers
    private void setupStatusListeners() {
        // Printer status observer for SSE
        printerService.addStatusListener(event -> {
            String statusJson = gson.toJson(event.getStatus());
            broadcastToSSE("data: " + statusJson + "\n\n");
        });

        // VFD status observer for SSE
        vfdService.addStatusListener(event -> {
            String statusJson = gson.toJson(event.getStatus());
            broadcastToSSE("data: " + statusJson + "\n\n");
        });
    }

    /**
     * Start the integrated application with default startup mode from configuration
     * @throws StartupException if startup requirements are not met
     */
    public void start() throws StartupException {
        start(serverConfig.getStartupMode());
    }

    /**
     * Start the integrated application with specified startup mode
     * @param mode Startup mode to use
     * @throws StartupException if startup requirements are not met
     */
    public void start(StartupMode mode) throws StartupException {
        logger.info("========================================");
        logger.info("Starting Integrated Printer & VFD Controller");
        logger.info("Startup Mode: {}", mode);
        logger.info("========================================");

        try {
            // Step 1: Initialize all services
            List<ServiceInitializationResult> results = initializeAllServices();

            // Step 2: Evaluate startup success based on mode
            evaluateStartupRequirements(mode, results);

            // Step 3: Start monitoring for successfully initialized services
            startMonitoring();

            // Step 4: Start SSE management
            startSSEManagementTasks();

            // Step 5: Start web server
            startWebServer();

            // Step 6: Register shutdown hook
            registerShutdownHook();

            // Step 7: Log final status
            logStartupSummary(results);

        } catch (StartupException e) {
            logger.error("Startup failed: {}", e.getMessage());
            shutdown();  // Cleanup on failure
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during startup", e);
            shutdown();
            throw new StartupException("Unexpected startup failure", mode, new ArrayList<>(initializationResults.values()));
        }
    }

    /**
     * Initialize all services and track results
     * @return List of initialization results
     */
    private List<ServiceInitializationResult> initializeAllServices() {
        List<ServiceInitializationResult> results = new ArrayList<>();

        // Initialize printer service
        ServiceInitializationResult printerResult = initializePrinterService();
        results.add(printerResult);
        initializationResults.put("printer", printerResult);

        // Initialize VFD service
        ServiceInitializationResult vfdResult = initializeVFDService();
        results.add(vfdResult);
        initializationResults.put("vfd", vfdResult);

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

            logger.info("✓ Printer service initialized successfully in {}ms", duration);
            return ServiceInitializationResult.success("Printer", duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            logger.error("✗ Printer service initialization failed after {}ms: {}",
                    duration, e.getMessage());
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

            logger.error("✗ VFD service initialization failed after {}ms: {}",
                    duration, e.getMessage());
            logger.debug("VFD initialization error details", e);

            return ServiceInitializationResult.failure("VFD", e, duration);
        }
    }

    /**
     * Evaluate if startup requirements are met based on mode
     * @param mode Startup mode
     * @param results Initialization results
     * @throws StartupException if requirements not met
     */
    private void evaluateStartupRequirements(StartupMode mode, List<ServiceInitializationResult> results)
            throws StartupException {

        long successfulServices = results.stream()
                .filter(ServiceInitializationResult::isSuccess)
                .count();

        long totalServices = results.size();

        logger.info("Service initialization complete: {}/{} services successful",
                successfulServices, totalServices);

        switch (mode) {
            case STRICT:
                if (successfulServices != totalServices) {
                    String message = String.format(
                            "STRICT mode requires all services to initialize. " +
                                    "Only %d/%d services initialized successfully.",
                            successfulServices, totalServices
                    );
                    throw new StartupException(message, mode, results);
                }
                logger.info("✓ STRICT mode requirement met: all services initialized");
                break;

            case LENIENT:
                if (successfulServices == 0) {
                    String message = "LENIENT mode requires at least one service to initialize. " +
                            "All services failed to initialize.";
                    throw new StartupException(message, mode, results);
                }
                if (successfulServices < totalServices) {
                    logger.warn("⚠ LENIENT mode: {}/{} services initialized (some services unavailable)",
                            successfulServices, totalServices);
                } else {
                    logger.info("✓ LENIENT mode requirement met: all services initialized");
                }
                break;

            case PERMISSIVE:
                if (successfulServices == 0) {
                    logger.warn("⚠ PERMISSIVE mode: No services initialized - running in degraded mode");
                } else if (successfulServices < totalServices) {
                    logger.info("⚠ PERMISSIVE mode: {}/{} services initialized",
                            successfulServices, totalServices);
                } else {
                    logger.info("✓ PERMISSIVE mode: all services initialized");
                }
                break;
        }
    }

    /**
     * Start monitoring for initialized services
     */
    private void startMonitoring() {
        // Start printer monitoring if printer initialized
        ServiceInitializationResult printerResult = initializationResults.get("printer");
        if (printerResult != null && printerResult.isSuccess()) {
            logger.info("Starting printer status monitoring...");
            printerStatusMonitor.startMonitoring(executorService);
        } else {
            logger.info("Printer monitoring skipped (printer not initialized)");
        }
    }

    /**
     * Start web server
     */
    private void startWebServer() {
        logger.info("Starting web server on port {}...", serverConfig.getPort());
        javalinApp = createJavalinApp();
        logger.info("✓ Web server started successfully");
    }

    /**
     * Log startup summary
     */
    private void logStartupSummary(List<ServiceInitializationResult> results) {
        logger.info("========================================");
        logger.info("Startup Complete - Application Status:");
        logger.info("========================================");

        for (ServiceInitializationResult result : results) {
            logger.info(result.toString());
        }

        logger.info("Web Server: RUNNING on port {}", serverConfig.getPort());
        logger.info("API Documentation: http://{}:{}/docs",
                serverConfig.getHost(), serverConfig.getPort());
        logger.info("========================================");
    }

    /**
     * Get initialization results (for testing or status endpoints)
     * @return Map of service name to initialization result
     */
    public Map<String, ServiceInitializationResult> getInitializationResults() {
        return Collections.unmodifiableMap(initializationResults);
    }

    /**
     * Broadcast message to all SSE clients
     */
    private void broadcastToSSE(String message) {
        int clientCount = sseClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} SSE clients", clientCount);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : sseClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("Broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Send heartbeat to all SSE clients to keep connections alive
     */
    private void sendHeartbeatToAllClients() {
        if (sseClients.isEmpty()) {
            return;
        }

        logger.trace("Sending heartbeat to {} SSE clients", sseClients.size());

        for (SSEClient client : sseClients.values()) {
            if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                if (!client.sendHeartbeat()) {
                    logger.debug("Heartbeat failed for client: {}", client);
                }
            }
        }
    }

    /**
     * Clean up stale SSE clients
     */
    private void cleanupStaleSSEClients() {
        if (sseClients.isEmpty()) {
            return;
        }

        logger.debug("Running SSE client cleanup check ({} active clients)", sseClients.size());

        int removedCount = 0;

        for (var entry : sseClients.entrySet()) {
            SSEClient client = entry.getValue();

            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} SSE client: {} (connected for {}ms, {} messages sent)",
                        reason, client.getClientId(), client.getConnectionDuration(), client.getMessagesSent());

                client.close();
                sseClients.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} stale SSE clients, {} remaining", removedCount, sseClients.size());
        }
    }

    /**
     * Register a new SSE client
     */
    public boolean registerSSEClient(String clientId, Context ctx) {
        if (sseClients.size() >= pik.common.ServerConstants.SSE_MAX_CLIENTS) {
            logger.warn("Maximum SSE client limit reached ({}), rejecting new connection from {}",
                    pik.common.ServerConstants.SSE_MAX_CLIENTS, ctx.ip());
            return false;
        }

        SSEClient client = new SSEClient(clientId, ctx);
        sseClients.put(clientId, client);

        logger.info("SSE client registered: {} (total clients: {})", client, sseClients.size());

        return true;
    }

    /**
     * Unregister an SSE client
     */
    public void unregisterSSEClient(String clientId) {
        SSEClient client = sseClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("SSE client unregistered: {} (total clients: {})", client, sseClients.size());
        }
    }

    /**
     * Get SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getSSEClients() {
        return sseClients;
    }

    /**
     * Start SSE management background tasks
     */
    private void startSSEManagementTasks() {
        sseCleanupTask = executorService.scheduleWithFixedDelay(
                this::cleanupStaleSSEClients,
                pik.common.ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                pik.common.ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE cleanup task started");

        sseHeartbeatTask = executorService.scheduleWithFixedDelay(
                this::sendHeartbeatToAllClients,
                pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE heartbeat task started");
    }

    private void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            shutdownHookRegistered = true;
        }
    }

    /**
     * Create and configure Javalin web application
     */
    private Javalin createJavalinApp() {
        javalinApp = Javalin.create(config -> {
            config.jsonMapper(createGsonMapper());

            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(corsRule -> {
                    corsRule.anyHost();
                    corsRule.allowCredentials = false;
                });
            });

            config.bundledPlugins.enableDevLogging();

            config.router.apiBuilder(() -> {
                get("/", ctx -> ctx.redirect("/docs"));
                get("/docs", ctx -> ctx.html(generateCombinedDocs()));

                PrinterController printerController = new PrinterController(printerService, this);
                printerController.registerRoutes();

                VFDController vfdController = new VFDController(vfdService, this);
                vfdController.registerRoutes();
            });
        });

        javalinApp.exception(Exception.class, (exception, ctx) -> {
            logger.error("Unhandled exception", exception);
            ErrorResponse errorResponse = new ErrorResponse(
                    "Internal server error",
                    exception.getMessage()
            );
            ctx.status(500).json(errorResponse);
        });

        return javalinApp.start(serverConfig.getPort());
    }

    /**
     * Create Gson JSON mapper
     */
    private JsonMapper createGsonMapper() {
        return new JsonMapper() {
            @Override
            public String toJsonString(Object obj, Type type) {
                return gson.toJson(obj, type);
            }

            @Override
            public <T> T fromJsonString(String json, Type targetType) {
                return gson.fromJson(json, targetType);
            }
        };
    }

    /**
     * Graceful shutdown
     */
    private void shutdown() {
        logger.info("Shutting down Integrated Controller...");

        try {
            if (sseCleanupTask != null) {
                sseCleanupTask.cancel(false);
            }
            if (sseHeartbeatTask != null) {
                sseHeartbeatTask.cancel(false);
            }

            logger.info("Closing {} SSE clients", sseClients.size());
            for (SSEClient client : sseClients.values()) {
                client.close();
            }
            sseClients.clear();

            if (javalinApp != null) {
                javalinApp.stop();
            }
            if (printerStatusMonitor != null) {
                printerStatusMonitor.stopMonitoring();
            }

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

            printerService.close();
            vfdService.close();
            logger.info("Application shut down successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    /**
     * Generate combined API documentation
     */
    private String generateCombinedDocs() {
        try (InputStream is = getClass()
                .getResourceAsStream("/html/api_docs.html")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load documentation", e);
            return "<h1>API documentation not available</h1>";
        }
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
