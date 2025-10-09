package pik.domain.thprinter;

import io.javalin.http.Context;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    private final IPrinterService printerService;
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

    private boolean printerServiceReady(Context ctx) {
        if (!printerService.isInitialized()) {
            ctx.status(503).json(ApiResponse.error("Printer service not initialized"));
            return false;
        }

        if (!printerService.isReady()) {
            ctx.status(503).json(ApiResponse.error("Printer not ready: " + printerService.getStatus().getErrorMessage()));
            return false;
        }

        return true;
    }

    /**
     * Print structured content
     */
    private void printContent(Context ctx) {
        if (!printerServiceReady(ctx))
            return;

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
        if (!printerServiceReady(ctx))
            return;

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
        if (!printerServiceReady(ctx))
            return;

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
        if (!printerServiceReady(ctx))
            return;

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
        try (InputStream is = getClass()
                .getResourceAsStream("/html/printer_docs.html")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load documentation", e);
            return "<h1>Printer API documentation not available</h1>";
        }
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
