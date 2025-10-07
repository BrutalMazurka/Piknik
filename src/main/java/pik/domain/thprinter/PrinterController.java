package pik.domain.thprinter;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * REST API Controller for printer operations
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrinterController {
    private static final Logger logger = LoggerFactory.getLogger(PrinterController.class);

    private PrinterService printerService;
    private ConcurrentHashMap<String, Context> sseClients =  new ConcurrentHashMap<>();

    public PrinterController(PrinterService printerService, ConcurrentHashMap<String, Context> sseClients) {
        this.printerService = printerService;
        this.sseClients = sseClients;
    }

    /**
     * Register all REST API routes
     */
    public void registerRoutes() {
        path("/api", () -> {
            path("/printer", () -> {
                get("/status", getStatus);
                get("/health", healthCheck);
                post("/print", printContent);
                post("/print/text", printText);
                post("/cut", cutPaper);
                post("/test", testPrint);
            });

            path("/events", () -> {
                get("/status", sseStatusUpdates);
            });
        });

        // Static routes for documentation or web interface
        path("/", () -> {
            get("", ctx -> ctx.redirect("/docs"));
            get("/docs", serveDocs);
        });
    }

    /**
     * Get current printer status
     */
    private final Handler getStatus = ctx -> {
        try {
            PrinterStatus status = printerService.getStatus();
            ctx.json(ApiResponse.success(status));

        } catch (Exception e) {
            logger.error("Error getting printer status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get printer status: " + e.getMessage()));
        }
    };

    /**
     * Health check endpoint
     */
    private final Handler healthCheck = ctx -> {
        boolean isHealthy = printerService.isReady();
        PrinterStatus status = printerService.getStatus();

        HealthCheckResponse health = new HealthCheckResponse(
                isHealthy,
                status.isOnline(),
                !status.hasErrors(),
                !status.hasWarnings(),
                status.getErrorMessage()
        );

        if (isHealthy) {
            ctx.json(ApiResponse.success("Printer is healthy", health));
        } else {
            ApiResponse<HealthCheckResponse> response = new ApiResponse<>(
                    false,
                    "Printer is not healthy",
                    health
            );
            ctx.status(503).json(response);
        }
    };

    /**
     * Print structured content
     */
    private final Handler printContent = ctx -> {
        try {
            PrintRequest printRequest = ctx.bodyAsClass(PrintRequest.class);

            if (printRequest == null) {
                ctx.status(400).json(ApiResponse.error("Invalid print request"));
                return;
            }

            logger.info("Received print request for {} copies", printRequest.getCopies());

            printerService.print(printRequest);

            ctx.json(ApiResponse.success("Print job completed successfully"));

        } catch (JposException e) {
            logger.error("JavaPOS error during printing: {} - {}", e.getErrorCode(), e.getMessage());
            ctx.status(500).json(ApiResponse.error("Print failed: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error during printing", e);
            ctx.status(500).json(ApiResponse.error("Print failed: " + e.getMessage()));
        }
    };

    /**
     * Print simple text
     */
    private final Handler printText = ctx -> {
        try {
            String text = ctx.body();

            if (text == null || text.trim().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("Text content is required"));
                return;
            }

            logger.info("Received text print request");

            printerService.printText(text);

            ctx.json(ApiResponse.success("Text printed successfully"));

        } catch (JposException e) {
            logger.error("JavaPOS error during text printing: {} - {}", e.getErrorCode(), e.getMessage());
            ctx.status(500).json(ApiResponse.error("Print failed: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error during text printing", e);
            ctx.status(500).json(ApiResponse.error("Print failed: " + e.getMessage()));
        }
    };

    /**
     * Cut paper
     */
    private final Handler cutPaper = ctx -> {
        try {
            printerService.cutPaper();
            ctx.json(ApiResponse.success("Paper cut successfully"));

        } catch (JposException e) {
            logger.error("JavaPOS error during paper cut: {} - {}", e.getErrorCode(), e.getMessage());
            ctx.status(500).json(ApiResponse.error("Paper cut failed: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error during paper cut", e);
            ctx.status(500).json(ApiResponse.error("Paper cut failed: " + e.getMessage()));
        }
    };

    /**
     * Test print functionality
     */
    private final Handler testPrint = ctx -> {
        try {
            String testContent = "=== PRINTER TEST ===\n" +
                    "Date: " + new java.util.Date() + "\n" +
                    "Status: WORKING\n" +
                    "==================\n\n";

            printerService.printText(testContent);
            printerService.cutPaper();

            ctx.json(ApiResponse.success("Test print completed"));

        } catch (Exception e) {
            logger.error("Test print failed", e);
            ctx.status(500).json(ApiResponse.error("Test print failed: " + e.getMessage()));
        }
    };

    /**
     * Server-Sent Events for real-time status updates
     */
    private final Handler sseStatusUpdates = ctx -> {
        String clientId = UUID.randomUUID().toString();

        logger.info("New SSE client connected: {}", clientId);

        ctx.contentType("text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Access-Control-Allow-Origin", "*");

        // Add client to SSE clients map
        sseClients.put(clientId, ctx);
        // Disconnect handler
        ctx.req().getAsyncContext().addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                sseClients.remove(clientId);
                logger.info("SSE client disconnected: {}", clientId);
            }
            @Override
            public void onStartAsync(AsyncEvent event) {}
            @Override
            public void onError(AsyncEvent event) {}
            @Override
            public void onTimeout(AsyncEvent event) {}
        });

        // Send initial status
        try {
            PrinterStatus status = printerService.getStatus();
            String statusJson = ctx.jsonMapper().toJsonString(status, PrinterStatus.class);
            ctx.result("data: " + statusJson + "\n\n");

            // Keep connection alive - this handler will remain active
            // Clients will be removed from map when broadcast fails
            logger.debug("SSE client {} initialized with current status", clientId);

        } catch (Exception e) {
            logger.error("Error sending initial status to SSE client {}", clientId, e);
            sseClients.remove(clientId);
        }
    };

    /**
     * Serve API documentation
     */
    private final Handler serveDocs = ctx -> {
        ctx.html(generateApiDocs());
    };

    /**
     * Generate simple API documentation HTML
     */
    private String generateApiDocs() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Epson Printer Controller API</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    h1, h2 { color: #333; }
                    .endpoint { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
                    .method { font-weight: bold; color: #007acc; }
                    .path { font-family: monospace; background: #e8e8e8; padding: 2px 6px; }
                    pre { background: #f0f0f0; padding: 10px; border-radius: 3px; overflow-x: auto; }
                </style>
            </head>
            <body>
                <h1>Epson Printer Controller API</h1>
                
                <h2>Endpoints</h2>
                
                <div class="endpoint">
                    <span class="method">GET</span> <span class="path">/api/printer/status</span>
                    <p>Get current printer status</p>
                </div>
                
                <div class="endpoint">
                    <span class="method">GET</span> <span class="path">/api/printer/health</span>
                    <p>Health check endpoint</p>
                </div>
                
                <div class="endpoint">
                    <span class="method">POST</span> <span class="path">/api/printer/print</span>
                    <p>Print structured content (JSON)</p>
                    <pre>{
  "text": "Hello World",
  "items": [
    {
      "type": "TEXT",
      "content": "Sample text",
      "options": {
        "bold": true,
        "alignment": "CENTER"
      }
    }
  ],
  "cutPaper": true,
  "copies": 1
}</pre>
                </div>
                
                <div class="endpoint">
                    <span class="method">POST</span> <span class="path">/api/printer/print/text</span>
                    <p>Print plain text (raw body)</p>
                </div>
                
                <div class="endpoint">
                    <span class="method">POST</span> <span class="path">/api/printer/cut</span>
                    <p>Cut paper</p>
                </div>
                
                <div class="endpoint">
                    <span class="method">POST</span> <span class="path">/api/printer/test</span>
                    <p>Test print functionality</p>
                </div>
                
                <div class="endpoint">
                    <span class="method">GET</span> <span class="path">/api/events/status</span>
                    <p>Server-Sent Events for real-time status updates</p>
                </div>
                
                <h2>Status Monitoring</h2>
                <p>Connect to <code>/api/events/status</code> to receive real-time printer status updates via Server-Sent Events.</p>
                
                <h2>Example Usage</h2>
                <pre>
// Get status
curl http://localhost:8080/api/printer/status

// Print text
curl -X POST -d "Hello World!" http://localhost:8080/api/printer/print/text

// Test print
curl -X POST http://localhost:8080/api/printer/test

// Listen to status updates (JavaScript)
const eventSource = new EventSource('/api/events/status');
eventSource.onmessage = function(event) {
    const status = JSON.parse(event.data);
    console.log('Printer status:', status);
};
                </pre>
            </body>
            </html>
            """;
    }

    /**
     * Health check response DTO
     */
    public static class HealthCheckResponse {
        private final boolean healthy;
        private final boolean online;
        private final boolean noErrors;
        private final boolean noWarnings;
        private final String message;

        public HealthCheckResponse(boolean healthy, boolean online, boolean noErrors,
                                   boolean noWarnings, String message) {
            this.healthy = healthy;
            this.online = online;
            this.noErrors = noErrors;
            this.noWarnings = noWarnings;
            this.message = message;
        }

        // Getters for JSON serialization
        public boolean isHealthy() { return healthy; }
        public boolean isOnline() { return online; }
        public boolean isNoErrors() { return noErrors; }
        public boolean isNoWarnings() { return noWarnings; }
        public String getMessage() { return message; }
    }
}
