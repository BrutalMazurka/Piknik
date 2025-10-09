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
    private final ConcurrentHashMap<String, Context> sseClients = new ConcurrentHashMap<>();
    private volatile boolean shutdownHookRegistered = false;
    private volatile boolean printerInitialized = false;

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

    private void broadcastToSSE(String message) {
        logger.debug("Broadcasting to {} SSE clients", sseClients.size());

        sseClients.entrySet().removeIf(entry -> {
            try {
                entry.getValue().result(message);
                return false;
            } catch (Exception e) {
                logger.debug("Removing disconnected SSE client: {}", entry.getKey());
                closeAsyncContext(entry.getValue());
                return true;
            }
        });
    }

    private void closeAsyncContext(Context ctx) {
        try {
            ctx.req().getAsyncContext().complete();
        } catch (Exception ignored) {}
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
                PrinterController printerController = new PrinterController(printerService, sseClients);
                printerController.registerRoutes();

                // VFD routes
                VFDController vfdController = new VFDController(vfdService, sseClients);
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
     * Broadcast status updates to all SSE clients
     */
    /*private void broadcastStatusUpdate(String message) {
        logger.debug("Broadcasting status update: {}", message);

        sseClients.entrySet().removeIf(entry -> {
            try {
                Context ctx = entry.getValue();
                ctx.result(message);
                return false;   // Keep client
            } catch (Exception e) {
                logger.debug("Removing disconnected SSE client: {}", entry.getKey());
                // Explicitly close the async context
                try {
                    entry.getValue().req().getAsyncContext().complete();
                } catch (Exception ignored) {
                }
                return true;    // Remove client
            }
        });
    }*/

    /**
     * Graceful shutdown
     */
    private void shutdown() {
        logger.info("Shutting down Integrated Controller...");

        try {
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
