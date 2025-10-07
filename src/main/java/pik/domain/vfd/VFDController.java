package pik.domain.vfd;

import com.google.gson.Gson;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.thprinter.ApiResponse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * REST Controller for VFD display management
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
public class VFDController {
    private static final Logger logger = LoggerFactory.getLogger(VFDController.class);

    private final VFDService vfdService;
    private final ConcurrentHashMap<String, Context> sseClients;
    private final Gson gson;

    public VFDController(VFDService vfdService, ConcurrentHashMap<String, Context> sseClients) {
        this.vfdService = vfdService;
        this.sseClients = sseClients;
        this.gson = new Gson();
    }

    /**
     * Register all VFD REST API routes
     */
    public void registerRoutes() {
        path("/api/vfd", () -> {
            get("/status", getStatus);
            get("/health", healthCheck);
            get("/info", getInfo);
            post("/display", displayText);
            post("/clear", clearDisplay);
            post("/cursor/position", setCursorPosition);
            post("/cursor/home", homeCursor);
            post("/cursor/show", showCursor);
            post("/brightness", setBrightness);
            post("/command", sendCustomCommand);
            post("/demo", runDemo);
            post("/reconnect", reconnectDisplay);
        });

        path("/api/vfd/events", () -> {
            get("/status", sseStatusUpdates);
        });
    }

    /**
     * Get current VFD status
     */
    private final Handler getStatus = ctx -> {
        try {
            VFDStatus status = vfdService.getStatus();
            ctx.json(ApiResponse.success(status));
        } catch (Exception e) {
            logger.error("Error getting VFD status", e);
            ctx.status(500).json(ApiResponse.error("Failed to get VFD status: " + e.getMessage()));
        }
    };

    /**
     * Health check endpoint
     */
    private final Handler healthCheck = ctx -> {
        boolean isHealthy = vfdService.isReady();
        VFDStatus status = vfdService.getStatus();

        if (isHealthy) {
            ctx.json(ApiResponse.success("VFD is healthy", status));
        } else {
            ApiResponse<VFDStatus> response = new ApiResponse<>(
                    false,
                    "VFD is not healthy",
                    status
            );
            ctx.status(503).json(response);
        }
    };

    /**
     * Get display information
     */
    private final Handler getInfo = ctx -> {
        try {
            String info = vfdService.getDisplayInfo();
            ctx.json(ApiResponse.success(info));
        } catch (Exception e) {
            logger.error("Error getting display info", e);
            ctx.status(500).json(ApiResponse.error("Failed to get display info: " + e.getMessage()));
        }
    };

    /**
     * Display text on VFD
     */
    private final Handler displayText = ctx -> {
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
    };

    /**
     * Clear display
     */
    private final Handler clearDisplay = ctx -> {
        try {
            vfdService.clearDisplay();
            ctx.json(ApiResponse.success("Display cleared successfully"));
        } catch (Exception e) {
            logger.error("Error clearing VFD display", e);
            ctx.status(500).json(ApiResponse.error("Clear failed: " + e.getMessage()));
        }
    };

    /**
     * Set cursor position
     */
    private final Handler setCursorPosition = ctx -> {
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
    };

    /**
     * Home cursor
     */
    private final Handler homeCursor = ctx -> {
        try {
            vfdService.homeCursor();
            ctx.json(ApiResponse.success("Cursor homed successfully"));
        } catch (Exception e) {
            logger.error("Error homing cursor", e);
            ctx.status(500).json(ApiResponse.error("Home cursor failed: " + e.getMessage()));
        }
    };

    /**
     * Show or hide cursor
     */
    private final Handler showCursor = ctx -> {
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
    };

    /**
     * Set brightness
     */
    private final Handler setBrightness = ctx -> {
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
    };

    /**
     * Send custom command
     */
    private final Handler sendCustomCommand = ctx -> {
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
    };

    /**
     * Run demo
     */
    private final Handler runDemo = ctx -> {
        try {
            vfdService.runDemo();
            ctx.json(ApiResponse.success("Demo completed successfully"));
        } catch (Exception e) {
            logger.error("Error running VFD demo", e);
            ctx.status(500).json(ApiResponse.error("Demo failed: " + e.getMessage()));
        }
    };

    /**
     * Server-Sent Events for real-time VFD status updates
     */
    private final Handler sseStatusUpdates = ctx -> {
        String clientId = "vfd_" + UUID.randomUUID().toString();

        logger.info("New VFD SSE client connected: {}", clientId);

        ctx.contentType("text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Access-Control-Allow-Origin", "*");

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

        try {
            VFDStatus status = vfdService.getStatus();
            String statusJson = gson.toJson(status);
            ctx.result("data: " + statusJson + "\n\n");

            logger.debug("VFD SSE client {} initialized with current status", clientId);

        } catch (Exception e) {
            logger.error("Error sending initial VFD status to SSE client {}", clientId, e);
            sseClients.remove(clientId);
        }
    };

    /**
     * Reconnect the display
     */
    private final Handler reconnectDisplay = ctx -> {
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
    };
}
