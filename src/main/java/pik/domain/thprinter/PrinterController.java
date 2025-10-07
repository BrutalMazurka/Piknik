package pik.domain.thprinter;

import io.javalin.http.Context;
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

    private final PrinterService printerService;
    private final ConcurrentHashMap<String, Context> sseClients;

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
                get("/status", this::getStatus);
                get("/health", this::healthCheck);
                post("/print", this::printContent);
                post("/print/text", this::printText);
                post("/cut", this::cutPaper);
                post("/test", this::testPrint);
            });

            path("/events", () -> {
                get("/status", this::sseStatusUpdates);
            });
        });

        // Static routes for documentation or web interface
        path("/", () -> {
            get("", ctx -> ctx.redirect("/docs"));
            get("/docs", this::serveDocs);
        });
    }

    /**
     * Get current printer status
     */
    private void getStatus(Context ctx) {
        try {
            PrinterStatus status = printerService.getStatus();
            ctx.json(ApiResponse.success(status));

        } catch (Exception e) {
            logger.error("Error getting printer status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get printer status: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    private void healthCheck(Context ctx) {
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
    }

    /**
     * Print structured content
     */
    private void printContent(Context ctx) {
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
    }

    /**
     * Print simple text
     */
    private void printText(Context ctx) {
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
    }

    /**
     * Cut paper
     */
    private void cutPaper(Context ctx) {
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
    }

    /**
     * Test print functionality
     */
    private void testPrint(Context ctx) {
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
    }

    /**
     * Server-Sent Events for real-time status updates
     */
    private void sseStatusUpdates(Context ctx) {
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
    }

    /**
     * Serve API documentation
     */
    private void serveDocs(Context ctx) {
        ctx.html(generateApiDocs());
    }

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
                    body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; }
                    h1, h2 { color: #333; }
                    h1 { border-bottom: 3px solid #007acc; padding-bottom: 10px; }
                    h2 { color: #007acc; margin-top: 30px; }
                    .endpoint { background: #f9f9f9; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #007acc; }
                    .method { font-weight: bold; color: #007acc; margin-right: 10px; }
                    .path { font-family: monospace; background: #e8e8e8; padding: 3px 8px; border-radius: 3px; }
                    pre { background: #f0f0f0; padding: 15px; border-radius: 5px; overflow-x: auto; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🖨️ Epson Printer Controller API</h1>
                    <p>REST API for Epson TM-T20III thermal printer control</p>
                    
                    <h2>Endpoints</h2>
                    
                    <div class="endpoint">
                        <span class="method">GET</span> <span class="path">/api/printer/status</span>
                        <p>Get current printer status including paper level, cover state, and errors</p>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">GET</span> <span class="path">/api/printer/health</span>
                        <p>Health check endpoint - returns 200 if printer is ready, 503 if not</p>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">POST</span> <span class="path">/api/printer/print</span>
                        <p>Print structured content with formatting options (JSON)</p>
                        <pre>{
  "text": "Hello World",
  "items": [
    {
      "type": "TEXT",
      "content": "Sample text",
      "options": {
        "bold": true,
        "alignment": "CENTER",
        "fontSize": 2
      }
    },
    {
      "type": "IMAGE",
      "content": "base64_or_file_path",
      "options": {
        "width": 200
      }
    },
    {
      "type": "BARCODE",
      "content": "123456789"
    },
    {
      "type": "LINE",
      "content": "================================"
    }
  ],
  "cutPaper": true,
  "copies": 1
}</pre>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">POST</span> <span class="path">/api/printer/print/text</span>
                        <p>Print plain text (send text as raw body)</p>
                        <pre>curl -X POST -d "Hello World!" http://localhost:8080/api/printer/print/text</pre>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">POST</span> <span class="path">/api/printer/cut</span>
                        <p>Cut paper (if printer supports paper cutting)</p>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">POST</span> <span class="path">/api/printer/test</span>
                        <p>Test print functionality - prints a test page</p>
                    </div>
                    
                    <div class="endpoint">
                        <span class="method">GET</span> <span class="path">/api/events/status</span>
                        <p>Server-Sent Events for real-time printer status updates</p>
                    </div>
                    
                    <h2>Status Monitoring</h2>
                    <p>Connect to <code>/api/events/status</code> to receive real-time printer status updates via Server-Sent Events.</p>
                    <p>Status updates include:</p>
                    <ul>
                        <li>Online/offline state</li>
                        <li>Paper empty/near end warnings</li>
                        <li>Cover open detection</li>
                        <li>Error conditions</li>
                    </ul>
                    
                    <h2>Example Usage</h2>
                    <pre>
// Get status
curl http://localhost:8080/api/printer/status

// Print text
curl -X POST -d "Hello World!" http://localhost:8080/api/printer/print/text

// Print structured content
curl -X POST -H "Content-Type: application/json" \\
  -d '{"items":[{"type":"TEXT","content":"Test","options":{"bold":true}}],"cutPaper":true}' \\
  http://localhost:8080/api/printer/print

// Test print
curl -X POST http://localhost:8080/api/printer/test

// Health check
curl http://localhost:8080/api/printer/health

// Listen to status updates (JavaScript)
const eventSource = new EventSource('/api/events/status');
eventSource.onmessage = function(event) {
    const status = JSON.parse(event.data);
    console.log('Printer status:', status);
    
    if (status.paperEmpty) {
        alert('Paper is empty!');
    }
    if (status.coverOpen) {
        alert('Cover is open!');
    }
};

// Close SSE connection when done
eventSource.close();
                    </pre>
                    
                    <h2>Response Format</h2>
                    <p>All endpoints return JSON responses with the following structure:</p>
                    <pre>{
  "success": true,
  "message": "Operation completed successfully",
  "data": { /* response data */ },
  "timestamp": 1234567890
}</pre>
                    
                    <h2>Error Handling</h2>
                    <p>Errors return appropriate HTTP status codes:</p>
                    <ul>
                        <li><strong>400</strong> - Bad request (invalid input)</li>
                        <li><strong>500</strong> - Internal server error (printer error)</li>
                        <li><strong>503</strong> - Service unavailable (printer offline)</li>
                    </ul>
                    
                    <p>Error response format:</p>
                    <pre>{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": 1234567890
}</pre>
                </div>
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
        public boolean isHealthy() {
            return healthy;
        }

        public boolean isOnline() {
            return online;
        }

        public boolean isNoErrors() {
            return noErrors;
        }

        public boolean isNoWarnings() {
            return noWarnings;
        }

        public String getMessage() {
            return message;
        }
    }
}
