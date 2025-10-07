package pik.domain.thprinter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.PrinterConstants;
import pik.dal.PrinterConfig;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main application class for Epson Printer Controller
 * Provides REST API and SSE for printer management
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrinterControllerApp {
    private static final Logger logger = LoggerFactory.getLogger(PrinterControllerApp.class);

    private static final int DEFAULT_PORT = 8080;
    private final Gson gson;
    private final PrinterService printerService;
    private final StatusMonitorService statusMonitorService;
    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<String, Context> sseClients = new ConcurrentHashMap<>();

    public PrinterControllerApp() {
        // Initialize Gson with pretty printing
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        // Initialize services
        PrinterConfig config = loadConfiguration();
        int checkIntervalMs = Integer.parseInt(System.getProperty("monitor.status.interval", String.valueOf(PrinterConstants.STATUS_CHECK_INTERVAL)));
        this.printerService = new PrinterService(config, this::broadcastStatusUpdate);
        this.statusMonitorService = new StatusMonitorService(printerService, this::broadcastStatusUpdate, checkIntervalMs);
        this.executorService = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        logger.info("Starting Epson Printer Controller Application...");

        // Initialize printer service
        try {
            printerService.initialize();
            logger.info("Printer service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize printer service", e);
            // Continue running to allow configuration changes via API
        }

        // Start status monitoring in background
        statusMonitorService.startMonitoring(executorService);

        // Configure and start Javalin web server
        Javalin app = createJavalinApp();

        // Setup graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        logger.info("Epson Printer Controller started on port {}", DEFAULT_PORT);
    }

    private Javalin createJavalinApp() {
        Javalin app = Javalin.create(config -> {
            // Configure JSON mapper to use Gson
            config.jsonMapper(new JsonMapper() {
                @Override
                public String toJsonString(Object obj, Type type) {
                    return gson.toJson(obj, type);
                }

                @Override
                public <T> T fromJsonString(String json, Type targetType) {
                    return gson.fromJson(json, targetType);
                }
            });

            // Enable CORS for web clients
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(corsRule -> {
                    corsRule.anyHost();
                    corsRule.allowCredentials = false;
                });
            });

            // Enable request logging
            config.bundledPlugins.enableDevLogging();

            // Register routes within config
            config.router.apiBuilder(() -> {
                PrinterController controller = new PrinterController(printerService, sseClients);
                controller.registerRoutes();
            });
        });

        // Register exception handler
        app.exception(Exception.class, (exception, ctx) -> {
            logger.error("Unhandled exception", exception);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", exception.getMessage());
            ctx.status(500);
            ctx.json(errorResponse);
        });

        return app.start(DEFAULT_PORT);
    }

    private PrinterConfig loadConfiguration() {
        // Load configuration from system properties or default values
        String printerName = System.getProperty("printer.name", "TM-T20III");
        String printerIP = System.getProperty("printer.ip", "10.0.0.150");
        int printerPort = Integer.parseInt(System.getProperty("printer.port", String.valueOf(PrinterConstants.DEFAULT_PORT)));
        int connectionTimeout = Integer.parseInt(System.getProperty("printer.connectionTimeout", String.valueOf(PrinterConstants.DEFAULT_CONNECTION_TIMEOUT)));

        return new PrinterConfig(printerName, printerIP, printerPort, connectionTimeout);
    }

    private void broadcastStatusUpdate(String message) {
        logger.debug("Broadcasting status update: {}", message);

        // Send SSE message to all connected clients
        sseClients.entrySet().removeIf(entry -> {
            try {
                Context ctx = entry.getValue();
                // Write SSE formatted message
                ctx.result(message);
                return false; // Keep client
            } catch (Exception e) {
                logger.debug("Removing disconnected SSE client: {}", entry.getKey());
                return true; // Remove client
            }
        });
    }

    private void shutdown() {
        logger.info("Shutting down Epson Printer Controller...");

        try {
            statusMonitorService.stopMonitoring();
            executorService.shutdown();
            printerService.close();
            logger.info("Application shut down successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    // Inner class for error responses
    public static class ErrorResponse {
        private final String error;
        private final String message;
        private final long timestamp;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters for JSON serialization
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
