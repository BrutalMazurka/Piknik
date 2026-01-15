package pik.domain.orchestration;

import pik.domain.IntegratedController;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Injector;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.ServerConfig;
import pik.domain.ingenico.IngenicoController;
import pik.domain.ingenico.IngenicoService;
import pik.domain.thprinter.PrinterController;
import pik.domain.thprinter.PrinterService;
import pik.domain.vfd.VFDController;
import pik.domain.vfd.VFDService;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * Manages web server (Javalin) configuration and lifecycle
 * @author Martin Sustik <sustik@herman.cz>
 * @since 19/11/2025
 */
public class WebServerManager {
    private static final Logger logger = LoggerFactory.getLogger(WebServerManager.class);

    private final ServerConfig serverConfig;
    private final PrinterService printerService;
    private final VFDService vfdService;
    private final IngenicoService ingenicoService;
    private final IntegratedController controller;
    private final Injector injector;

    // ⭐ Pretty-printing Gson for general API use
    private final Gson gson;
    // ⭐ Compact Gson specifically for SSE (no pretty printing!)
    private final Gson compactGson;

    private Javalin javalinApp;

    /**
     * Constructor
     * @param serverConfig Server configuration
     * @param printerService Printer service
     * @param vfdService VFD service
     * @param ingenicoService Ingenico service
     * @param controller Integrated controller (for SSE callbacks)
     * @param injector Guice injector for dependency injection
     */
    public WebServerManager(
            ServerConfig serverConfig,
            PrinterService printerService,
            VFDService vfdService,
            IngenicoService ingenicoService,
            IntegratedController controller,
            Injector injector) {
        this.serverConfig = serverConfig;
        this.printerService = printerService;
        this.vfdService = vfdService;
        this.ingenicoService = ingenicoService;
        this.controller = controller;
        this.injector = injector;

        // ⭐ Initialize Gson with pretty printing (for API responses)
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        // ⭐ Initialize compact Gson for SSE messages (no newlines)
        this.compactGson = new Gson();
    }

    /**
     * Get compact Gson instance (for SSE)
     */
    public Gson getCompactGson() {
        return compactGson;
    }

    /**
     * Start web server
     */
    public void start() {
        logger.info("Starting web server on port {}...", serverConfig.port());
        javalinApp = createJavalinApp();
        logger.info("✓ Web server started successfully");
    }

    /**
     * Stop web server
     */
    public void stop() {
        if (javalinApp != null) {
            javalinApp.stop();
        }
    }

    /**
     * Create and configure Javalin web application
     */
    private Javalin createJavalinApp() {
        javalinApp = Javalin.create(config -> {
            config.jsonMapper(createGsonMapper());

            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(corsRule -> {
                    corsRule.anyHost();
                    corsRule.allowCredentials = false;
                });
            });

            config.bundledPlugins.enableDevLogging();

            config.router.apiBuilder(() -> {
                get("/", ctx -> ctx.redirect("/docs"));
                get("/docs", ctx -> ctx.html(generateCombinedDocs()));
                get("/test", ctx -> ctx.html(loadTestClient()));
                get("/testclient", ctx -> ctx.html(loadTestClient()));

                PrinterController printerController = new PrinterController(printerService, controller);
                printerController.registerRoutes();

                VFDController vfdController = new VFDController(vfdService, controller);
                vfdController.registerRoutes();

                // Get SamUnlockOrchestrator from injector
                pik.domain.ingenico.unlock.SamUnlockOrchestrator unlockOrchestrator =
                    injector.getInstance(pik.domain.ingenico.unlock.SamUnlockOrchestrator.class);

                IngenicoController ingenicoController = new IngenicoController(ingenicoService, controller, unlockOrchestrator);
                ingenicoController.registerRoutes();
            });
        });

        javalinApp.exception(Exception.class, (exception, ctx) -> {
            logger.error("Unhandled exception", exception);
            IntegratedController.ErrorResponse errorResponse = new IntegratedController.ErrorResponse("Internal server error", exception.getMessage());
            ctx.status(500).json(errorResponse);
        });

        return javalinApp.start(serverConfig.port());
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
     * Load test client HTML/JS page
     */
    private String loadTestClient() {
        // Try external location first
        Path externalHtml = Paths.get("res/html/testclient.html");
        if (Files.exists(externalHtml)) {
            try {
                String content = Files.readString(externalHtml, StandardCharsets.UTF_8);
                logger.debug("Loaded test client from external file: {}", externalHtml);
                return content;
            } catch (IOException e) {
                logger.warn("Failed to load external test client: {}", e.getMessage());
            }
        }

        // Fallback to classpath (embedded in JAR)
        try (InputStream is = getClass().getResourceAsStream("/html/testclient.html")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Loaded test client from classpath");
                return content;
            }
        } catch (IOException e) {
            logger.error("Failed to load test client", e);
        }

        return "<h1>Test client not available</h1>";
    }

    /**
     * Generate combined API documentation
     */
    private String generateCombinedDocs() {
        // Try external location first
        Path externalHtml = Paths.get("resources/html/api_docs.html");
        if (Files.exists(externalHtml)) {
            try {
                String content = Files.readString(externalHtml, StandardCharsets.UTF_8);
                logger.debug("Loaded API docs from external file: {}", externalHtml);
                return content;
            } catch (IOException e) {
                logger.warn("Failed to load external API docs: {}", e.getMessage());
            }
        }

        // Fallback to classpath (embedded in JAR)
        try (InputStream is = getClass().getResourceAsStream("/html/api_docs.html")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Loaded API docs from classpath");
                return content;
            }
        } catch (IOException e) {
            logger.error("Failed to load API documentation", e);
        }

        return "<h1>API documentation not available</h1>";
    }
}
