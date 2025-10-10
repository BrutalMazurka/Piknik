package pik.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.ServerConstants;
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
    private final int serverPort;
    private Javalin javalinApp;

    // Printer components
    private final PrinterService printerService;
    private final StatusMonitorService printerStatusMonitor;

    // VFD components
    private final VFDService vfdService;

    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, SSEClient> sseClients = new ConcurrentHashMap<>();
    private volatile boolean shutdownHookRegistered = false;
    private volatile boolean printerInitialized = false;

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
        this.serverPort = serverConfig.getPort();

        // Initialize Gson
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Initialize services
        this.printerService = new PrinterService(printerConfig);
        this.vfdService = new VFDService(vfdConfig);
        this.printerStatusMonitor = new StatusMonitorService(printerService, this::broadcastToSSE, serverConfig.getStatusCheckInterval());
        this.executorService = Executors.newScheduledThreadPool(serverConfig.getThreadPoolSize());

        // Register observers AFTER construction
        setupStatusListeners();

        logger.info("IntegratedControllerApp initialized");
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

        // A place for more observers here:
        // Metrics collector, Alert system, Audit log, ...
    }

    /**
     * Broadcast message to all SSE clients
     * @param message The message to broadcast
     */
    private void broadcastToSSE(String message) {
        int clientCount = sseClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} SSE clients", clientCount);

        int successCount = 0;
        int failureCount = 0;

        // Send to all clients and track failures
        for (var entry : sseClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                // Will be cleaned up by the periodic cleanup task
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
            if (client.needsHeartbeat(ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
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
        long now = System.currentTimeMillis();

        for (var entry : sseClients.entrySet()) {
            SSEClient client = entry.getValue();

            // Remove if inactive or stale
            if (!client.isActive() || client.isStale(ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
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
     * @param clientId Unique client ID
     * @param ctx Javalin context
     * @return true if client was registered, false if rejected (too many clients)
     */
    public boolean registerSSEClient(String clientId, Context ctx) {
        // Check if we've reached max clients
        if (sseClients.size() >= ServerConstants.SSE_MAX_CLIENTS) {
            logger.warn("Maximum SSE client limit reached ({}), rejecting new connection from {}",
                    ServerConstants.SSE_MAX_CLIENTS, ctx.ip());
            return false;
        }

        SSEClient client = new SSEClient(clientId, ctx);
        sseClients.put(clientId, client);

        logger.info("SSE client registered: {} (total clients: {})", client, sseClients.size());

        return true;
    }

    /**
     * Unregister an SSE client
     * @param clientId Client ID to unregister
     */
    public void unregisterSSEClient(String clientId) {
        SSEClient client = sseClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("SSE client unregistered: {} (total clients: {})", client, sseClients.size());
        }
    }

    /**
     * Get SSE clients map (for controllers)
     * @return Map of SSE clients
     */
    public ConcurrentHashMap<String, SSEClient> getSSEClients() {
        return sseClients;
    }

    /**
     * Start the integrated application
     */
    public void start() {
        logger.info("Starting Integrated Printer & VFD Controller Application...");

        boolean printerReady = false;
        boolean vfdReady = false;

        try {
            // Initialize printer
            try {
                printerService.initialize();
                printerInitialized = true;
                printerReady = true;
                logger.info("Printer service initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize printer service", e);
            }

            // Initialize VFD
            try {
                vfdService.initialize();
                vfdReady = true;
                if (vfdService.isDummyMode()) {
                    logger.warn("VFD running in dummy mode");
                } else {
                    logger.info("VFD service initialized successfully");
                }
            } catch (Exception e) {
                logger.error("Failed to initialize VFD service", e);
            }

            // Start monitoring only if printer is ready
            if (printerReady && printerStatusMonitor != null) {
                printerStatusMonitor.startMonitoring(executorService);
            }

            // Start SSE management tasks
            startSSEManagementTasks();

            // Create web server
            javalinApp = createJavalinApp();

            // Setup shutdown hook
            registerShutdownHook();

        } catch (Exception e) {
            logger.error("Fatal error during startup", e);
            shutdown();  // Cleanup on failure
            throw e;
        }
    }

    /**
     * Start SSE management background tasks
     */
    private void startSSEManagementTasks() {
        // Cleanup task - remove stale clients periodically
        sseCleanupTask = executorService.scheduleWithFixedDelay(
                this::cleanupStaleSSEClients,
                ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE cleanup task started (interval: {}ms)", ServerConstants.SSE_CLEANUP_INTERVAL_MS);

        // Heartbeat task - keep connections alive
        sseHeartbeatTask = executorService.scheduleWithFixedDelay(
                this::sendHeartbeatToAllClients,
                ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE heartbeat task started (interval: {}ms)", ServerConstants.SSE_HEARTBEAT_INTERVAL_MS);
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
            // Configure JSON mapper to use Gson
            config.jsonMapper(createGsonMapper());

            // Enable CORS
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(corsRule -> {
                    corsRule.anyHost();
                    corsRule.allowCredentials = false;
                });
            });

            // Enable request logging
            config.bundledPlugins.enableDevLogging();

            // Register routes
            config.router.apiBuilder(() -> {
                // Root redirect
                get("/", ctx -> ctx.redirect("/docs"));

                // Combined documentation
                get("/docs", ctx -> ctx.html(generateCombinedDocs()));

                // Printer routes
                PrinterController printerController = new PrinterController(printerService, this);
                printerController.registerRoutes();

                // VFD routes
                VFDController vfdController = new VFDController(vfdService, this);
                vfdController.registerRoutes();
            });
        });

        // Global exception handler
        javalinApp.exception(Exception.class, (exception, ctx) -> {
            logger.error("Unhandled exception", exception);
            ErrorResponse errorResponse = new ErrorResponse(
                    "Internal server error",
                    exception.getMessage()
            );
            ctx.status(500).json(errorResponse);
        });

        return javalinApp.start(serverPort);
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
            // Stop SSE management tasks
            if (sseCleanupTask != null) {
                sseCleanupTask.cancel(false);
            }
            if (sseHeartbeatTask != null) {
                sseHeartbeatTask.cancel(false);
            }

            // Close all SSE clients
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
            // Wait for executor shutdown or force the shutdown
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
