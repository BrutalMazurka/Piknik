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
            get("/config", this::getConfig);
            post("/test", this::testReader);
            get("/diagnostics", this::getDiagnostics);
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
     * Reports actual device status:
     * - Online: Device is fully operational (all connections established)
     * - Initializing: Device is connecting (partial connectivity or in init sequence)
     * - Online with errors: Device is connected but not operational (e.g., missing SAM DUK)
     * - Offline: Device failed to initialize (hardware unavailable)
     * - Dummy: Device is configured as NONE in application.properties
     */
    private void healthCheck(Context ctx) {
        IngenicoStatus status = ingenicoService.getStatus();
        boolean isHealthy = ingenicoService.isReady();

        // Create health check response with clear status indication
        String message;
        if (status.dummyMode()) {
            message = "Ingenico reader is running in dummy mode (configured as NONE)";
        } else if (status.isOperational()) {
            message = "Ingenico reader is online and fully operational";
        } else if (status.initState() != EReaderInitState.DONE) {
            // Still going through initialization sequence
            message = "Ingenico reader is initializing: " + status.initState().getDescription();
        } else if (status.initialized() && status.hasWarnings()) {
            // Initialization sequence complete but connections are still establishing
            StringBuilder details = new StringBuilder("Ingenico reader is initializing (partial connectivity): ");
            if (!status.ifsfConnected()) {
                details.append("IFSF not connected; ");
            } else if (!status.ifsfAppAlive()) {
                details.append("IFSF app not alive; ");
            }
            if (!status.transitConnected()) {
                details.append("Transit not connected; ");
            } else if (!status.transitAppAlive()) {
                details.append("Transit app not alive; ");
            }
            if (!status.samDukDetected()) {
                details.append("SAM DUK not detected; ");
            }
            message = details.toString().replaceAll("; $", "");
        } else if (status.initialized() && status.ifsfConnected() && status.transitConnected()) {
            // Reader is connected and initialized but not operational (e.g., SAM DUK missing)
            message = "Ingenico reader is online but not operational: " +
                      (status.errorMessage() != null ? status.errorMessage() : "Unknown error");
        } else {
            message = "Ingenico reader is offline (hardware unavailable)";
        }

        HealthCheckResponse health = new HealthCheckResponse(
                isHealthy,
                status.initialized(),
                status.isOperational(),
                !status.hasErrors(),
                !status.hasWarnings(),
                status.errorMessage()
        );

        // Return 200 OK for operational and initializing states
        // Return 503 for offline/failed states
        if (isHealthy || status.initState() != EReaderInitState.DONE || (status.initialized() && status.hasWarnings())) {
            ctx.json(ApiResponse.success(message, health));
        } else {
            ApiResponse<HealthCheckResponse> response = new ApiResponse<>(
                    false,
                    message,
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
     * Get reader configuration
     */
    private void getConfig(Context ctx) {
        try {
            IngenicoStatus status = ingenicoService.getStatus();

            ConfigResponse config = new ConfigResponse(
                    ingenicoService.isDummyMode() ? "NONE" : "NETWORK",
                    status.dummyMode(),
                    status.ifsfConnected(),
                    status.transitConnected(),
                    status.terminalId()
            );

            ctx.json(ApiResponse.success(config));
        } catch (Exception e) {
            logger.error("Error getting Ingenico configuration", e);
            ctx.status(500).json(ApiResponse.error("Failed to get configuration: " + e.getMessage()));
        }
    }

    /**
     * Test reader connectivity and diagnostics
     */
    private void testReader(Context ctx) {
        try {
            IngenicoStatus status = ingenicoService.getStatus();

            // Build test results
            TestResult testResult = new TestResult(
                    ingenicoService.isReady(),
                    status.initialized(),
                    status.ifsfConnected() && status.ifsfAppAlive(),
                    status.transitConnected() && status.transitAppAlive(),
                    status.samDukDetected(),
                    status.dummyMode() ? "Dummy mode - no physical reader" :
                        (status.isOperational() ? "All systems operational" : status.errorMessage())
            );

            if (testResult.success()) {
                ctx.json(ApiResponse.success("Reader test passed", testResult));
            } else {
                ApiResponse<TestResult> response = new ApiResponse<>(
                        false,
                        "Reader test failed",
                        testResult
                );
                ctx.status(503).json(response);
            }
        } catch (Exception e) {
            logger.error("Error testing Ingenico reader", e);
            ctx.status(500).json(ApiResponse.error("Failed to test reader: " + e.getMessage()));
        }
    }

    /**
     * Get detailed diagnostics information
     */
    private void getDiagnostics(Context ctx) {
        try {
            IngenicoStatus status = ingenicoService.getStatus();

            DiagnosticsResponse diagnostics = new DiagnosticsResponse(
                    status.initState().name(),
                    status.initState().getDescription(),
                    status.initialized(),
                    status.ifsfConnected(),
                    status.ifsfAppAlive(),
                    status.transitConnected(),
                    status.transitAppAlive(),
                    status.transitTerminalStatusCode(),
                    status.transitTerminalStatus(),
                    status.samDukDetected(),
                    status.samDukStatus(),
                    status.error(),
                    status.errorMessage(),
                    status.dummyMode(),
                    status.lastUpdate()
            );

            ctx.json(ApiResponse.success(diagnostics));
        } catch (Exception e) {
            logger.error("Error getting Ingenico diagnostics", e);
            ctx.status(500).json(ApiResponse.error("Failed to get diagnostics: " + e.getMessage()));
        }
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

    /**
     * Configuration response DTO
     */
    public record ConfigResponse(
            String connectionType,
            boolean dummyMode,
            boolean ifsfConnected,
            boolean transitConnected,
            String terminalId
    ) {
    }

    /**
     * Test result DTO
     */
    public record TestResult(
            boolean success,
            boolean initialized,
            boolean ifsfOperational,
            boolean transitOperational,
            boolean samDetected,
            String message
    ) {
    }

    /**
     * Diagnostics response DTO
     */
    public record DiagnosticsResponse(
            String initState,
            String initStateDescription,
            boolean initialized,
            boolean ifsfConnected,
            boolean ifsfAppAlive,
            boolean transitConnected,
            boolean transitAppAlive,
            int transitTerminalStatusCode,
            String transitTerminalStatus,
            boolean samDukDetected,
            String samDukStatus,
            boolean error,
            String errorMessage,
            boolean dummyMode,
            long lastUpdate
    ) {
    }
}
