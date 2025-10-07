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

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * Integrated application managing both Thermal Printer and VFD Display
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class IntegratedControllerApp {
    private static final Logger logger = LoggerFactory.getLogger(IntegratedControllerApp.class);

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

    /**
     * Constructor accepting configurations (no circular dependency)
     * @param printerConfig Printer configuration
     * @param vfdConfig VFD configuration
     * @param serverConfig Server configuration
     */
    public IntegratedControllerApp(PrinterConfig printerConfig,
                                   VFDConfig vfdConfig,
                                   ServerConfig serverConfig) {
        this.serverPort = serverConfig.getPort();

        // Initialize Gson
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        // Initialize services
        this.printerService = new PrinterService(printerConfig, this::broadcastStatusUpdate);
        this.printerStatusMonitor = new StatusMonitorService(printerService, this::broadcastStatusUpdate, serverConfig.getStatusCheckInterval());
        this.vfdService = new VFDService(vfdConfig, this::broadcastStatusUpdate);
        this.executorService = Executors.newScheduledThreadPool(3);

        logger.info("IntegratedControllerApp initialized");
    }

    /**
     * Start the integrated application
     */
    public void start() {
        logger.info("Starting Integrated Printer & VFD Controller Application...");

        // Initialize printer service
        try {
            printerService.initialize();
            logger.info("Printer service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize printer service", e);
        }

        // Initialize VFD service
        try {
            vfdService.initialize();
            if (vfdService.isDummyMode()) {
                logger.warn("VFD service running in dummy mode - physical display not available");
            } else {
                logger.info("VFD service initialized successfully");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize VFD service", e);
        }

        // Start monitoring
        if (printerStatusMonitor != null) {
            printerStatusMonitor.startMonitoring(executorService);
        }

        // Create and start web server
        Javalin app = createJavalinApp();

        // Setup graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        logger.info("Integrated Controller started on port {}", serverPort);
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
    private void broadcastStatusUpdate(String message) {
        logger.debug("Broadcasting status update: {}", message);

        sseClients.entrySet().removeIf(entry -> {
            try {
                Context ctx = entry.getValue();
                ctx.result(message);
                return false; // Keep client
            } catch (Exception e) {
                logger.debug("Removing disconnected SSE client: {}", entry.getKey());
                return true; // Remove client
            }
        });
    }

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
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Integrated Printer & VFD Controller API</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; }
                    h1 { color: #333; border-bottom: 3px solid #007acc; padding-bottom: 10px; }
                    h2 { color: #007acc; margin-top: 30px; }
                    .endpoint { background: #f9f9f9; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #007acc; }
                    .method { font-weight: bold; color: #007acc; margin-right: 10px; }
                    .path { font-family: monospace; background: #e8e8e8; padding: 3px 8px; border-radius: 3px; }
                    pre { background: #f0f0f0; padding: 15px; border-radius: 5px; overflow-x: auto; }
                    .section { margin: 30px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🖨️ Integrated Printer & VFD Controller API</h1>
                    <p>Unified REST API for Epson TM-T20III Printer and FV-2030B VFD Display control</p>
                    
                    <div class="section">
                        <h2>Printer Endpoints</h2>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/printer/status</span>
                            <p>Get current printer status</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/printer/health</span>
                            <p>Printer health check</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/printer/print</span>
                            <p>Print structured content (JSON)</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/printer/print/text</span>
                            <p>Print plain text</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/printer/cut</span>
                            <p>Cut paper</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/printer/test</span>
                            <p>Test print</p>
                        </div>
                    </div>
                    
                    <div class="section">
                        <h2>VFD Display Endpoints</h2>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/vfd/status</span>
                            <p>Get current VFD status</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/vfd/health</span>
                            <p>VFD health check</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/vfd/display</span>
                            <p>Display text on VFD</p>
                            <pre>{"text": "Hello World"}</pre>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/vfd/clear</span>
                            <p>Clear VFD display</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/vfd/brightness</span>
                            <p>Set brightness (0-100)</p>
                            <pre>{"brightness": 80}</pre>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">POST</span> <span class="path">/api/vfd/reconnect</span>
                            <p>Attempt to reconnect to physical display</p>
                        </div>
                    </div>
                    
                    <div class="section">
                        <h2>Real-time Monitoring</h2>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/events/status</span>
                            <p>Server-Sent Events for printer status updates</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method">GET</span> <span class="path">/api/vfd/events/status</span>
                            <p>Server-Sent Events for VFD status updates</p>
                        </div>
                    </div>
                    
                    <div class="section">
                        <h2>Example Usage</h2>
                        <pre>
# Print receipt and display total
curl -X POST -d "Receipt Content" http://localhost:8080/api/printer/print/text
curl -X POST -H "Content-Type: application/json" \\
  -d '{"text":"Total: $25.00"}' \\
  http://localhost:8080/api/vfd/display

# Check health
curl http://localhost:8080/api/printer/health
curl http://localhost:8080/api/vfd/health

# Monitor status (JavaScript)
const printerEvents = new EventSource('/api/events/status');
printerEvents.onmessage = (e) => console.log('Printer:', JSON.parse(e.data));

const vfdEvents = new EventSource('/api/vfd/events/status');
vfdEvents.onmessage = (e) => console.log('VFD:', JSON.parse(e.data));
                        </pre>
                    </div>
                </div>
            </body>
            </html>
            """;
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
