package pik.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    // ⭐ This pretty-printing Gson is for general use
    private final Gson gson;
    // ⭐ This compact Gson is specifically for SSE (no pretty printing!)
    private final Gson compactGson;
    private final ServerConfig serverConfig;
    private Javalin javalinApp;

    // Printer components
    private final PrinterService printerService;
    private final StatusMonitorService printerStatusMonitor;

    // VFD components
    private final VFDService vfdService;

    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, SSEClient> printerSSEClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SSEClient> vfdSSEClients = new ConcurrentHashMap<>();
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

        // ⭐ Initialize Gson with pretty printing (for API responses)
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        // ⭐ Initialize compact Gson for SSE messages (no newlines)
        this.compactGson = new Gson();

        // Initialize services
        this.printerService = new PrinterService(printerConfig);
        this.vfdService = new VFDService(vfdConfig);
        this.printerStatusMonitor = new StatusMonitorService(
                printerService,
                this::broadcastToPrinterSSE,
                serverConfig.statusCheckInterval()
        );
        this.executorService = Executors.newScheduledThreadPool(serverConfig.threadPoolSize());

        // Register observers AFTER construction
        setupStatusListeners();

        logger.info("IntegratedController initialized with startup mode: {}", serverConfig.startupMode());
    }

    // Setup observers
    private void setupStatusListeners() {
        // Printer status observer for SSE - broadcast to PRINTER clients only
        printerService.addStatusListener(event -> {
            // ⭐ Use compactGson instead of gson !
            String statusJson = compactGson.toJson(event.getStatus());
            // Send as properly formatted SSE message with event type
            String sseMessage = "event: status\ndata: " + statusJson + "\n\n";
            broadcastToPrinterSSE(sseMessage);
            logger.debug("Broadcast printer status update to printer SSE clients");
        });

        // VFD status observer for SSE - broadcast to VFD clients only
        vfdService.addStatusListener(event -> {
            // ⭐ Use compactGson instead of gson !
            String statusJson = compactGson.toJson(event.getStatus());
            // Send as properly formatted SSE message with event type
            String sseMessage = "event: status\ndata: " + statusJson + "\n\n";
            broadcastToVFDSSE(sseMessage);
            logger.debug("Broadcast VFD status update to VFD SSE clients");
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
        logger.info("Starting web server on port {}...", serverConfig.port());
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

        logger.info("Web Server: RUNNING on port {}", serverConfig.port());
        logger.info("API Documentation: http://{}:{}/docs",
                serverConfig.host(), serverConfig.port());
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
     * Broadcast message to printer SSE clients only
     */
    private void broadcastToPrinterSSE(String message) {
        int clientCount = printerSSEClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} printer SSE clients", clientCount);
        logger.debug("Message to broadcast: [{}]", message);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : printerSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to printer client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("Printer broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Broadcast message to VFD SSE clients only
     */
    private void broadcastToVFDSSE(String message) {
        int clientCount = vfdSSEClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} VFD SSE clients", clientCount);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : vfdSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to VFD client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("VFD broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Get printer SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getPrinterSSEClients() {
        return printerSSEClients;
    }

    /**
     * Get VFD SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getVFDSSEClients() {
        return vfdSSEClients;
    }

    /**
     * Send heartbeat to all SSE clients to keep connections alive
     */
    private void sendHeartbeatToAllClients() {
        // Printer clients
        if (!printerSSEClients.isEmpty()) {
            logger.trace("Sending heartbeat to {} printer SSE clients", printerSSEClients.size());
            for (SSEClient client : printerSSEClients.values()) {
                if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                    if (!client.sendHeartbeat()) {
                        logger.debug("Heartbeat failed for printer client: {}", client);
                    }
                }
            }
        }

        // VFD clients
        if (!vfdSSEClients.isEmpty()) {
            logger.trace("Sending heartbeat to {} VFD SSE clients", vfdSSEClients.size());
            for (SSEClient client : vfdSSEClients.values()) {
                if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                    if (!client.sendHeartbeat()) {
                        logger.debug("Heartbeat failed for VFD client: {}", client);
                    }
                }
            }
        }
    }

    /**
     * Clean up stale SSE clients
     */
    private void cleanupStaleSSEClients() {
        int totalClients = printerSSEClients.size() + vfdSSEClients.size();
        if (totalClients == 0) {
            return;
        }

        logger.debug("Running SSE client cleanup check ({} printer, {} VFD clients)", printerSSEClients.size(), vfdSSEClients.size());

        int removedCount = 0;

        // Clean printer clients
        for (var entry : printerSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} printer SSE client: {}", reason, client.getClientId());
                client.close();
                printerSSEClients.remove(entry.getKey());
                removedCount++;
            }
        }

        // Clean VFD clients
        for (var entry : vfdSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} VFD SSE client: {}", reason, client.getClientId());
                client.close();
                vfdSSEClients.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} stale SSE clients", removedCount);
        }
    }

    /**
     * Unregister a printer SSE client
     */
    public void unregisterPrinterSSEClient(String clientId) {
        SSEClient client = printerSSEClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("Printer SSE client unregistered: {} (total printer clients: {})", clientId, printerSSEClients.size());
        }
    }

    /**
     * Unregister a VFD SSE client
     */
    public void unregisterVFDSSEClient(String clientId) {
        SSEClient client = vfdSSEClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("VFD SSE client unregistered: {} (total VFD clients: {})", clientId, vfdSSEClients.size());
        }
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
                get("/test", ctx -> ctx.html(loadTestClient()));
                get("/testclient", ctx -> ctx.html(loadTestClient()));

                PrinterController printerController = new PrinterController(printerService, this);
                printerController.registerRoutes();

                VFDController vfdController = new VFDController(vfdService, this);
                vfdController.registerRoutes();
            });
        });

        javalinApp.exception(Exception.class, (exception, ctx) -> {
            logger.error("Unhandled exception", exception);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", exception.getMessage());
            ctx.status(500).json(errorResponse);
        });

        return javalinApp.start(serverConfig.port());
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
     * Load test client HTML/JS page
     */
    private String loadTestClient() {
        // Try external location first
        Path externalHtml = Paths.get("res/html/testclient.html");
        if (Files.exists(externalHtml)) {
            try {
                String content = Files.readString(externalHtml, StandardCharsets.UTF_8);
                logger.debug("Loaded test client from external file: {}", externalHtml);
                return content;
            } catch (IOException e) {
                logger.warn("Failed to load external test client: {}", e.getMessage());
            }
        }

        // Fallback to classpath (embedded in JAR)
        try (InputStream is = getClass().getResourceAsStream("/html/testclient.html")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Loaded test client from classpath");
                return content;
            }
        } catch (IOException e) {
            logger.error("Failed to load test client", e);
        }

        return "<h1>Test client not available</h1>";
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

            logger.info("Closing {} printer SSE clients, {} VFD SSE clients", printerSSEClients.size(), vfdSSEClients.size());

            for (SSEClient client : printerSSEClients.values()) {
                client.close();
            }
            printerSSEClients.clear();

            for (SSEClient client : vfdSSEClients.values()) {
                client.close();
            }
            vfdSSEClients.clear();

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
        // Try external location first
        Path externalHtml = Paths.get("resources/html/api_docs.html");
        if (Files.exists(externalHtml)) {
            try {
                String content = Files.readString(externalHtml, StandardCharsets.UTF_8);
                logger.debug("Loaded API docs from external file: {}", externalHtml);
                return content;
            } catch (IOException e) {
                logger.warn("Failed to load external API docs: {}", e.getMessage());
            }
        }

        // Fallback to classpath (embedded in JAR)
        try (InputStream is = getClass().getResourceAsStream("/html/api_docs.html")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Loaded API docs from classpath");
                return content;
            }
        } catch (IOException e) {
            logger.error("Failed to load API documentation", e);
        }

        return "<h1>API documentation not available</h1>";
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
