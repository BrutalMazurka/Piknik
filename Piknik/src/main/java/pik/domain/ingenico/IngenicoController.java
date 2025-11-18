package pik.domain.ingenico;

import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.IntegratedController;
import pik.domain.SSEClient;
import pik.domain.thprinter.ApiResponse;

import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * REST API Controller for Ingenico Card Reader operations
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public class IngenicoController {
    private static final Logger logger = LoggerFactory.getLogger(IngenicoController.class);

    private final IIngenicoService ingenicoService;
    private final IntegratedController integratedController;
    private final Gson gson;

    public IngenicoController(IngenicoService ingenicoService, IntegratedController integratedController) {
        this.ingenicoService = ingenicoService;
        this.integratedController = integratedController;
        this.gson = new Gson();
    }

    /**
     * Register all Ingenico REST API routes
     */
    public void registerRoutes() {
        path("/api/ingenico", () -> {
            get("/status", this::getStatus);
            get("/health", this::healthCheck);
            get("/info", this::getInfo);
        });

        path("/api/ingenico/events", () -> {
            sse("/status", this::sseStatusUpdates);
        });
    }

    /**
     * Get current Ingenico reader status
     */
    private void getStatus(Context ctx) {
        try {
            IngenicoStatus status = ingenicoService.getStatus();
            ctx.json(ApiResponse.success(status));
        } catch (Exception e) {
            logger.error("Error getting Ingenico status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get Ingenico status: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    private void healthCheck(Context ctx) {
        boolean isHealthy = ingenicoService.isReady();
        IngenicoStatus status = ingenicoService.getStatus();

        HealthCheckResponse health = new HealthCheckResponse(
                isHealthy,
                status.isInitialized(),
                status.isOperational(),
                !status.hasErrors(),
                !status.hasWarnings(),
                status.getErrorMessage()
        );

        if (isHealthy) {
            ctx.json(ApiResponse.success("Ingenico reader is healthy", health));
        } else {
            ApiResponse<HealthCheckResponse> response = new ApiResponse<>(
                    false,
                    "Ingenico reader is not healthy",
                    health
            );
            ctx.status(503).json(response);
        }
    }

    /**
     * Get reader information
     */
    private void getInfo(Context ctx) {
        try {
            String info = ingenicoService.getReaderInfo();
            ctx.json(ApiResponse.success(info));
        } catch (Exception e) {
            logger.error("Error getting reader info", e);
            ctx.status(500).json(ApiResponse.error("Failed to get reader info: " + e.getMessage()));
        }
    }

    /**
     * Server-Sent Events for real-time Ingenico status updates
     */
    private void sseStatusUpdates(SseClient client) {
        String clientId = "ingenico_" + UUID.randomUUID();

        logger.info("New Ingenico SSE client connecting: {}", clientId);

        // Check client limit
        if (integratedController.getIngenicoSSEClients().size() >= pik.common.ServerConstants.SSE_MAX_CLIENTS) {
            logger.warn("Maximum SSE client limit reached, rejecting Ingenico connection");
            client.sendEvent("error", "Maximum number of SSE clients reached");
            client.close();
            return;
        }

        // Create and register our SSE client wrapper
        SSEClient wrappedClient = new SSEClient(clientId, client);
        integratedController.getIngenicoSSEClients().put(clientId, wrappedClient);

        logger.info("Ingenico SSE client registered: {} (total clients: {})",
                clientId, integratedController.getIngenicoSSEClients().size());

        // Send initial status
        try {
            IngenicoStatus status = ingenicoService.getStatus();
            String statusJson = gson.toJson(status);
            logger.debug("Sending initial Ingenico status: {}", statusJson);
            client.sendEvent("status", statusJson);
            logger.info("Ingenico SSE client {} initialized with current status", clientId);
        } catch (Exception e) {
            logger.error("Error sending initial Ingenico status to SSE client {}", clientId, e);
            integratedController.getIngenicoSSEClients().remove(clientId);
            client.close();
            return;
        }

        // Set up the close handler
        client.onClose(() -> {
            logger.info("Ingenico SSE client {} closed", clientId);
            integratedController.getIngenicoSSEClients().remove(clientId);
        });

        // Keep connection open by calling keepAlive()
        client.keepAlive();

        logger.debug("Ingenico SSE client {} connection kept alive", clientId);
    }

    /**
     * Health check response DTO
     */
    public record HealthCheckResponse(
            boolean healthy,
            boolean initialized,
            boolean operational,
            boolean noErrors,
            boolean noWarnings,
            String message
    ) {
    }
}
