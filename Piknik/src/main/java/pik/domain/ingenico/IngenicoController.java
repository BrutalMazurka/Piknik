package pik.domain.ingenico;

import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.IntegratedController;
import pik.domain.SSEClient;
import pik.domain.ingenico.cardread.CardReadOrchestrator;
import pik.domain.ingenico.cardread.CardReadSession;
import pik.domain.ingenico.cardread.dto.CardReadRequest;
import pik.domain.ingenico.cardread.dto.CardReadStartResponse;
import pik.domain.ingenico.cardread.dto.CardReadStatusResponse;
import pik.domain.ingenico.unlock.SamUnlockOrchestrator;
import pik.domain.ingenico.unlock.UnlockSession;
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
    private final SamUnlockOrchestrator unlockOrchestrator;
    private final CardReadOrchestrator cardReadOrchestrator;
    private final Gson gson;

    public IngenicoController(IngenicoService ingenicoService,
                              IntegratedController integratedController,
                              SamUnlockOrchestrator unlockOrchestrator,
                              CardReadOrchestrator cardReadOrchestrator) {
        this.ingenicoService = ingenicoService;
        this.integratedController = integratedController;
        this.unlockOrchestrator = unlockOrchestrator;
        this.cardReadOrchestrator = cardReadOrchestrator;
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

            // Async unlock endpoints
            path("/unlock", () -> {
                post("/start", this::startUnlock);
                get("/status/{sessionId}", this::getUnlockStatus);
            });

            // Card reading endpoints
            path("/card", () -> {
                post("/read", this::startCardRead);
                get("/read/{sessionId}", this::getCardReadStatus);
                delete("/read/{sessionId}", this::cancelCardRead);
            });
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
     * Start SAM unlock process (async)
     * POST /api/ingenico/unlock/start
     * Body: { "pin": "123456" }
     * Returns: { "success": true, "message": "...", "data": { "sessionId": "..." } }
     */
    private void startUnlock(Context ctx) {
        try {
            UnlockRequest request = ctx.bodyAsClass(UnlockRequest.class);

            if (request == null || request.pin() == null) {
                ctx.status(400).json(ApiResponse.error("PIN is required"));
                return;
            }

            // Start unlock process
            String sessionId = unlockOrchestrator.startUnlock(request.pin());

            // Return 202 Accepted with session ID
            UnlockStartResponse response = new UnlockStartResponse(sessionId);
            ctx.status(202).json(ApiResponse.success(
                "Unlock started - please tap card",
                response
            ));

            logger.info("SAM unlock started with session ID: {}", sessionId);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid unlock request: {}", e.getMessage());
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Invalid state for unlock: {}", e.getMessage());
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error starting SAM unlock", e);
            ctx.status(500).json(ApiResponse.error("Failed to start unlock: " + e.getMessage()));
        }
    }

    /**
     * Get unlock session status
     * GET /api/ingenico/unlock/status/{sessionId}
     * Returns: { "success": true, "data": { "sessionId": "...", "status": "...", ... } }
     */
    private void getUnlockStatus(Context ctx) {
        try {
            String sessionId = ctx.pathParam("sessionId");
            UnlockSession session = unlockOrchestrator.getSessionStatus(sessionId);

            if (session == null) {
                ctx.status(404).json(ApiResponse.error("Session not found"));
                return;
            }

            // Convert to DTO
            UnlockStatusResponse response = new UnlockStatusResponse(
                session.getSessionId(),
                session.getStatus().name(),
                session.getErrorMessage(),
                session.getUpdatedAt()
            );

            ctx.json(ApiResponse.success(response));

        } catch (Exception e) {
            logger.error("Error getting unlock status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get status: " + e.getMessage()));
        }
    }

    /**
     * Start card reading process (async)
     * POST /api/ingenico/card/read
     * Body: { "readSchema": "FULL", "timeout": 30000 }
     * Returns: { "success": true, "message": "...", "data": { "sessionId": "..." } }
     */
    private void startCardRead(Context ctx) {
        try {
            CardReadRequest request = ctx.bodyAsClass(CardReadRequest.class);

            if (request == null) {
                request = new CardReadRequest(); // Use defaults
            }

            // Start card read process
            String sessionId = cardReadOrchestrator.startCardRead(
                    request.getReadSchemaOrDefault(),
                    request.getTimeoutOrDefault()
            );

            // Return 202 Accepted with session ID
            CardReadStartResponse response = new CardReadStartResponse(sessionId);
            ctx.status(202).json(ApiResponse.success(
                    "Card reading started - please tap card",
                    response
            ));

            logger.info("Card read started with session ID: {}", sessionId);

        } catch (IllegalStateException e) {
            logger.warn("Invalid state for card read: {}", e.getMessage());
            ctx.status(503).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error starting card read", e);
            ctx.status(500).json(ApiResponse.error("Failed to start card read: " + e.getMessage()));
        }
    }

    /**
     * Get card read session status
     * GET /api/ingenico/card/read/{sessionId}
     * Returns: { "success": true, "data": { "sessionId": "...", "status": "...", "cardData": {...} } }
     */
    private void getCardReadStatus(Context ctx) {
        try {
            String sessionId = ctx.pathParam("sessionId");
            CardReadSession session = cardReadOrchestrator.getSessionStatus(sessionId);

            if (session == null) {
                ctx.status(404).json(ApiResponse.error("Session not found"));
                return;
            }

            // Convert to DTO
            CardReadStatusResponse response = CardReadStatusResponse.fromSession(session);

            ctx.json(ApiResponse.success(response));

        } catch (Exception e) {
            logger.error("Error getting card read status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get status: " + e.getMessage()));
        }
    }

    /**
     * Cancel card reading session
     * DELETE /api/ingenico/card/read/{sessionId}
     * Returns: { "success": true, "message": "Session cancelled" }
     */
    private void cancelCardRead(Context ctx) {
        try {
            String sessionId = ctx.pathParam("sessionId");
            boolean cancelled = cardReadOrchestrator.cancelCardRead(sessionId);

            if (!cancelled) {
                ctx.status(404).json(ApiResponse.error("Session not found or already completed"));
                return;
            }

            ctx.json(ApiResponse.success("Card read session cancelled"));
            logger.info("Card read session {} cancelled", sessionId);

        } catch (Exception e) {
            logger.error("Error cancelling card read", e);
            ctx.status(500).json(ApiResponse.error("Failed to cancel session: " + e.getMessage()));
        }
    }

    /**
     * Unlock request DTO
     */
    public record UnlockRequest(String pin) {}

    /**
     * Unlock start response DTO
     */
    public record UnlockStartResponse(String sessionId) {}

    /**
     * Unlock status response DTO
     */
    public record UnlockStatusResponse(
        String sessionId,
        String status,
        String errorMessage,
        long updatedAt
    ) {}

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
