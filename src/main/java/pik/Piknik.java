package pik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.ConfigurationService;
import pik.dal.PrinterConfig;
import pik.dal.ServerConfig;
import pik.dal.VFDConfig;
import pik.domain.IntegratedController;
import pik.domain.StartupException;

/**
 * Main entry point for Piknik POS Controller Application
 * @author Martin Sustik <sustik@herman.cz>
 * @since 24/09/2025
 */
public class Piknik {
    private static final Logger logger = LoggerFactory.getLogger(Piknik.class);

    public static void main(String[] args) {
        logger.info("Starting Piknik POS Controller Application...");

        try {
            // Load all configurations from DAL
            ConfigurationService configService = new ConfigurationService();

            PrinterConfig printerConfig = configService.getPrinterConfiguration();
            VFDConfig vfdConfig = configService.getVFDConfiguration();
            ServerConfig serverConf = configService.getServerConfiguration();

            logger.info("Configuration loaded successfully");
            logger.debug("Printer: {}", printerConfig);
            logger.debug("VFD: {}", vfdConfig);
            logger.debug("Server: {}", serverConf);

            // Create and start integrated application
            IntegratedController app = new IntegratedController(
                    printerConfig,
                    vfdConfig,
                    serverConf
            );

            // Start with configured startup mode
            app.start();

        } catch (StartupException e) {
            logger.error("Application startup failed:");
            logger.error("  Mode: {}", e.getMode());
            logger.error("  Failed services: {}", e.getFailedServices().size());
            for (var result : e.getFailedServices()) {
                logger.error("    - {}", result);
            }
            System.exit(1);

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }

}
