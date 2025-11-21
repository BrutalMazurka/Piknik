package pik.domain.vfd;

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
 * REST Controller for VFD display management
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class VFDController {
    private static final Logger logger = LoggerFactory.getLogger(VFDController.class);

    private final IVFDService vfdService;
    private final IntegratedController integratedController;
    private final Gson gson;

    public VFDController(VFDService vfdService, IntegratedController integratedController) {
        this.vfdService = vfdService;
        this.integratedController = integratedController;
        this.gson = new Gson();
    }

    /**
     * Register all VFD REST API routes
     */
    public void registerRoutes() {
        path("/api/vfd", () -> {
            get("/status", this::getStatus);
            get("/health", this::healthCheck);
            get("/info", this::getInfo);
            post("/display", this::displayText);
            post("/clear", this::clearDisplay);
            post("/cursor/position", this::setCursorPosition);
            post("/cursor/home", this::homeCursor);
            post("/cursor/show", this::showCursor);
            post("/brightness", this::setBrightness);
            post("/command", this::sendCustomCommand);
            post("/demo", this::runDemo);
            post("/reconnect", this::reconnectDisplay);
        });

        path("/api/vfd/events", () -> {
            sse("/status", this::sseStatusUpdates);  // Changed to use sse() instead of get()
        });
    }

    /**
     * Get current VFD status
     */
    private void getStatus(Context ctx) {
        try {
            VFDStatus status = vfdService.getStatus();
            ctx.json(ApiResponse.success(status));
        } catch (Exception e) {
            logger.error("Error getting VFD status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get VFD status: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     * Reports actual device status:
     * - Online: Device is configured for hardware and is connected
     * - Offline: Device is configured for hardware but is not connected
     * - Dummy: Device is configured as NONE in application.properties
     */
    private void healthCheck(Context ctx) {
        VFDStatus status = vfdService.getStatus();
        boolean isHealthy = vfdService.isReady();

        // Create health check response with clear status indication
        String message;
        if (status.isDummyMode()) {
            message = "VFD is running in dummy mode (configured as NONE)";
        } else if (isHealthy && status.isConnected()) {
            message = "VFD is healthy and online";
        } else if (!status.isConnected()) {
            message = "VFD is offline (hardware unavailable)";
        } else {
            message = "VFD has errors";
        }

        if (isHealthy) {
            ctx.json(ApiResponse.success(message, status));
        } else {
            ApiResponse<VFDStatus> response = new ApiResponse<>(
                    false,
                    message,
                    status
            );
            ctx.status(503).json(response);
        }
    }

    /**
     * Get display information
     */
    private void getInfo(Context ctx) {
        try {
            String info = vfdService.getDisplayInfo();
            ctx.json(ApiResponse.success(info));
        } catch (Exception e) {
            logger.error("Error getting display info", e);
            ctx.status(500).json(ApiResponse.error("Failed to get display info: " + e.getMessage()));
        }
    }

    /**
     * Display text on VFD
     */
    private void displayText(Context ctx) {
        try {
            VFDDisplayRequest request = ctx.bodyAsClass(VFDDisplayRequest.class);

            if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("Text content is required"));
                return;
            }

            logger.info("Displaying text on VFD: {}", request.getText());
            vfdService.displayText(request.getText());

            ctx.json(ApiResponse.success("Text displayed successfully"));

        } catch (Exception e) {
            logger.error("Error displaying text on VFD", e);
            ctx.status(500).json(ApiResponse.error("Display failed: " + e.getMessage()));
        }
    }

    /**
     * Clear display
     */
    private void clearDisplay(Context ctx) {
        try {
            vfdService.clearDisplay();
            ctx.json(ApiResponse.success("Display cleared successfully"));
        } catch (Exception e) {
            logger.error("Error clearing VFD display", e);
            ctx.status(500).json(ApiResponse.error("Clear failed: " + e.getMessage()));
        }
    }

    /**
     * Set cursor position
     */
    private void setCursorPosition(Context ctx) {
        try {
            VFDDisplayRequest request = ctx.bodyAsClass(VFDDisplayRequest.class);

            if (request == null || request.getRow() == null || request.getCol() == null) {
                ctx.status(400).json(ApiResponse.error("Row and column are required"));
                return;
            }

            vfdService.setCursorPosition(request.getRow(), request.getCol());
            ctx.json(ApiResponse.success("Cursor position set successfully"));

        } catch (Exception e) {
            logger.error("Error setting cursor position", e);
            ctx.status(500).json(ApiResponse.error("Set cursor position failed: " + e.getMessage()));
        }
    }

    /**
     * Home cursor
     */
    private void homeCursor(Context ctx) {
        try {
            vfdService.homeCursor();
            ctx.json(ApiResponse.success("Cursor homed successfully"));
        } catch (Exception e) {
            logger.error("Error homing cursor", e);
            ctx.status(500).json(ApiResponse.error("Home cursor failed: " + e.getMessage()));
        }
    }

    /**
     * Show or hide cursor
     */
    private void showCursor(Context ctx) {
        try {
            VFDDisplayRequest request = ctx.bodyAsClass(VFDDisplayRequest.class);

            if (request == null || request.getShowCursor() == null) {
                ctx.status(400).json(ApiResponse.error("showCursor boolean is required"));
                return;
            }

            vfdService.showCursor(request.getShowCursor());
            ctx.json(ApiResponse.success("Cursor visibility set successfully"));

        } catch (Exception e) {
            logger.error("Error setting cursor visibility", e);
            ctx.status(500).json(ApiResponse.error("Show cursor failed: " + e.getMessage()));
        }
    }

    /**
     * Set brightness
     */
    private void setBrightness(Context ctx) {
        try {
            VFDDisplayRequest request = ctx.bodyAsClass(VFDDisplayRequest.class);

            if (request == null || request.getBrightness() == null) {
                ctx.status(400).json(ApiResponse.error("Brightness value is required"));
                return;
            }

            vfdService.setBrightness(request.getBrightness());
            ctx.json(ApiResponse.success("Brightness set successfully"));

        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error setting brightness", e);
            ctx.status(500).json(ApiResponse.error("Set brightness failed: " + e.getMessage()));
        }
    }

    /**
     * Send custom command
     */
    private void sendCustomCommand(Context ctx) {
        try {
            VFDDisplayRequest request = ctx.bodyAsClass(VFDDisplayRequest.class);

            if (request == null || request.getCustomCommand() == null || request.getCustomCommand().trim().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("Custom command is required"));
                return;
            }

            vfdService.sendCustomCommand(request.getCustomCommand());
            ctx.json(ApiResponse.success("Custom command sent successfully"));

        } catch (Exception e) {
            logger.error("Error sending custom command", e);
            ctx.status(500).json(ApiResponse.error("Custom command failed: " + e.getMessage()));
        }
    }

    /**
     * Run demo
     */
    private void runDemo(Context ctx) {
        try {
            vfdService.runDemo();
            ctx.json(ApiResponse.success("Demo completed successfully"));
        } catch (Exception e) {
            logger.error("Error running VFD demo", e);
            ctx.status(500).json(ApiResponse.error("Demo failed: " + e.getMessage()));
        }
    }

    /**
     * Server-Sent Events for real-time VFD status updates
     */
    private void sseStatusUpdates(SseClient client) {
        String clientId = "vfd_" + UUID.randomUUID();

        logger.info("New VFD SSE client connecting: {}", clientId);

        // Check client limit
        if (integratedController.getVFDSSEClients().size() >= pik.common.ServerConstants.SSE_MAX_CLIENTS) {
            logger.warn("Maximum SSE client limit reached, rejecting VFD connection");
            client.sendEvent("error", "Maximum number of SSE clients reached");
            client.close();
            return;
        }

        // Create and register our SSE client wrapper
        SSEClient wrappedClient = new SSEClient(clientId, client);
        integratedController.getVFDSSEClients().put(clientId, wrappedClient);

        logger.info("VFD SSE client registered: {} (total clients: {})", clientId, integratedController.getVFDSSEClients().size());

        // Send initial status
        try {
            VFDStatus status = vfdService.getStatus();
            String statusJson = gson.toJson(status);
            logger.debug("Sending initial VFD status: {}", statusJson);
            client.sendEvent("status", statusJson);
            logger.info("VFD SSE client {} initialized with current status", clientId);
        } catch (Exception e) {
            logger.error("Error sending initial VFD status to SSE client {}", clientId, e);
            integratedController.getVFDSSEClients().remove(clientId);
            client.close();
            return;
        }

        // ⭐ CRITICAL FIX: Keep the connection alive
        // Set up the close handler (note: this was already there, keeping it)
        client.onClose(() -> {
            logger.info("VFD SSE client {} closed", clientId);
            integratedController.getVFDSSEClients().remove(clientId);
        });

        // ⭐ Keep connection open by calling keepAlive()
        client.keepAlive();

        logger.debug("VFD SSE client {} connection kept alive", clientId);
    }

    /**
     * Reconnect the display
     */
    private void reconnectDisplay(Context ctx) {
        try {
            boolean success = vfdService.attemptReconnect();
            if (success) {
                ctx.json(ApiResponse.success("VFD reconnected successfully"));
            } else {
                ctx.status(503).json(ApiResponse.error("Reconnection failed"));
            }
        } catch (Exception e) {
            logger.error("Error reconnecting to VFD", e);
            ctx.status(500).json(ApiResponse.error("Reconnection failed: " + e.getMessage()));
        }
    }
}
