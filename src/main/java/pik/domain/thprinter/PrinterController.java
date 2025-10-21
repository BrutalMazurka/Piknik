package pik.domain.thprinter;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jpos.JposException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.IntegratedController;
import pik.domain.SSEClient;

import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * REST API Controller for printer operations
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrinterController {
    private static final Logger logger = LoggerFactory.getLogger(PrinterController.class);

    private final IPrinterService printerService;
    private final IntegratedController integratedController;

    public PrinterController(PrinterService printerService, IntegratedController integratedController) {
        this.printerService = printerService;
        this.integratedController = integratedController;
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
                sse("/status", this::sseStatusUpdates);  // Changed to use sse() instead of get()
            });
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

            validatePrintRequest(printRequest);

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

    private void validatePrintRequest(PrintRequest request) {
        if (request.getCopies() < 1 || request.getCopies() > 10) {
            throw new IllegalArgumentException("Copies must be between 1 and 10");
        }
        if (request.getItems() != null) {
            for (PrintRequest.PrintItem item : request.getItems()) {
                if (item.getType() == null || item.getType().trim().isEmpty()) {
                    throw new IllegalArgumentException("Item type cannot be empty");
                }
                if (item.getContent() == null || item.getContent().trim().isEmpty()) {
                    throw new IllegalArgumentException("Item content cannot be empty");
                }
            }
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
    private void sseStatusUpdates(SseClient client) {
        String clientId = "printer_" + UUID.randomUUID();

        logger.info("New printer SSE client connecting: {}", clientId);

        // Check client limit
        if (integratedController.getPrinterSSEClients().size() >= pik.common.ServerConstants.SSE_MAX_CLIENTS) {
            logger.warn("Maximum SSE client limit reached, rejecting printer connection");
            client.sendEvent("error", "Maximum number of SSE clients reached");
            client.close();
            return;
        }

        // Create and register our SSE client wrapper
        SSEClient wrappedClient = new SSEClient(clientId, client);
        integratedController.getPrinterSSEClients().put(clientId, wrappedClient);

        logger.info("Printer SSE client registered: {} (total clients: {})", clientId, integratedController.getPrinterSSEClients().size());

        // Send initial status
        try {
            PrinterStatus status = printerService.getStatus();
            String statusJson = new com.google.gson.Gson().toJson(status);
            logger.debug("Sending initial printer status: {}", statusJson);
            client.sendEvent("status", statusJson);
            logger.info("Printer SSE client {} initialized with current status", clientId);
        } catch (Exception e) {
            logger.error("Error sending initial status to SSE client {}", clientId, e);
            integratedController.getPrinterSSEClients().remove(clientId);
            client.close();
            return;
        }

        // ⭐ CRITICAL FIX: Keep the connection alive
        // Set up the close handler
        client.onClose(() -> {
            logger.info("Printer SSE client {} closed", clientId);
            integratedController.getPrinterSSEClients().remove(clientId);
        });

        // ⭐ Keep connection open by calling keepAlive()
        // This prevents Javalin from closing the connection when the handler returns
        client.keepAlive();

        logger.debug("Printer SSE client {} connection kept alive", clientId);
    }

    /**
         * Health check response DTO
         */
        public record HealthCheckResponse(boolean healthy, boolean online, boolean noErrors, boolean noWarnings,
                                          String message) {
    }
}
