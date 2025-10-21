package pik.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.EDisplayType;
import pik.common.PrinterConstants;
import pik.common.ServerConstants;
import pik.domain.StartupMode;

/**
 * Main configuration service - entry point for all configuration needs
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class ConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationLoader loader;
    private PrinterConfig printerConfig;
    private VFDConfig vfdConfig;
    private ServerConfig serverConfig;

    public ConfigurationService() throws ConfigurationException {
        this(new ConfigurationLoader());
    }

    public ConfigurationService(ConfigurationLoader loader) throws ConfigurationException {
        this.loader = loader;
        this.printerConfig = loadPrinterConfiguration();
        this.vfdConfig = loadVFDConfiguration();
        this.serverConfig = loadServerConfiguration();
    }

    /**
     * Load printer configuration
     */
    private PrinterConfig loadPrinterConfiguration() throws ConfigurationException {
        String name = loader.getString("printer.name", "TM-T20III");
        String ip = loader.getString("printer.ip", "10.0.0.150");
        int port = loader.getInt("printer.port", PrinterConstants.DEFAULT_PORT);
        int timeout = loader.getInt("printer.connection.timeout", PrinterConstants.DEFAULT_CONNECTION_TIMEOUT);

        PrinterConfig config = new PrinterConfig(name, ip, port, timeout);
        config.validate();
        return config;
    }

    /**
     * Load VFD configuration
     */
    private VFDConfig loadVFDConfiguration() throws ConfigurationException {
        String typeStr = loader.getString("vfd.type", "FV_2030B");
        String port = loader.getString("vfd.port", "COM3");
        int baudRate = loader.getInt("vfd.baud", 9600);

        EDisplayType displayType;
        try {
            displayType = EDisplayType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid VFD type '{}', using FV_2030B", typeStr);
            displayType = EDisplayType.FV_2030B;
        }

        VFDConfig config = new VFDConfig(displayType, port, baudRate);
        config.validate();
        return config;
    }

    /**
     * Load server configuration
     */
    private ServerConfig loadServerConfiguration() throws ConfigurationException {
        int port = loader.getInt("server.port", ServerConstants.SERVER_PORT);
        String host = loader.getString("server.host", ServerConstants.SERVER_IP);
        int interval = loader.getInt("monitor.status.interval", PrinterConstants.STATUS_CHECK_INTERVAL);
        boolean enabled = loader.getBoolean("monitor.enabled", true);
        int threadPoolSize = loader.getInt("server.thread.pool", ServerConstants.THREAD_POOL_SIZE);

        // Load startup mode
        String modeStr = loader.getString("server.startup.mode", "LENIENT");
        StartupMode startupMode;
        try {
            startupMode = StartupMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid startup mode '{}', using LENIENT", modeStr);
            startupMode = StartupMode.LENIENT;
        }

        ServerConfig config = new ServerConfig(port, host, interval, enabled, threadPoolSize, startupMode);
        config.validate();
        return config;
    }

    /**
     * Get printer configuration
     */
    public PrinterConfig getPrinterConfiguration() {
        return printerConfig;
    }

    /**
     * Get VFD configuration
     */
    public VFDConfig getVFDConfiguration() {
        return vfdConfig;
    }

    /**
     * Get server configuration
     */
    public ServerConfig getServerConfiguration() {
        return serverConfig;
    }

    /**
     * Reload configuration
     */
    public void reload() throws ConfigurationException {
        logger.info("Reloading configuration...");
        this.printerConfig = loadPrinterConfiguration();
        this.vfdConfig = loadVFDConfiguration();
        this.serverConfig = loadServerConfiguration();

        logger.info("Configuration reloaded successfully");
        logger.info("Printer: {}", printerConfig);
        logger.info("VFD: {}", vfdConfig);
        logger.info("Server: {}", serverConfig);
    }

}
