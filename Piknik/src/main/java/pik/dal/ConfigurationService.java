package pik.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.EDisplayType;
import pik.common.EPrinterType;
import pik.common.EReaderType;
import pik.common.EVFDConnectionType;
import pik.common.TM_T20IIIConstants;
import pik.common.ServerConstants;

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
    private IngenicoConfig ingenicoConfig;

    public ConfigurationService() throws ConfigurationException {
        this(new ConfigurationLoader());
    }

    public ConfigurationService(ConfigurationLoader loader) throws ConfigurationException {
        this.loader = loader;
        this.printerConfig = loadPrinterConfiguration();
        this.vfdConfig = loadVFDConfiguration();
        this.serverConfig = loadServerConfiguration();
        this.ingenicoConfig = loadIngenicoConfiguration();
    }

    /**
     * Load printer configuration with support for multiple connection types
     */
    private PrinterConfig loadPrinterConfiguration() throws ConfigurationException {
        String name = loader.getString("printer.name", "TM-T20III");
        int timeout = loader.getInt("printer.connection.timeout", TM_T20IIIConstants.DEFAULT_CONNECTION_TIMEOUT);

        // Determine connection type
        String connectionTypeStr = loader.getString("printer.connection.type", "USB");
        EPrinterType connectionType;
        try {
            connectionType = EPrinterType.valueOf(connectionTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid printer connection type '{}', defaulting to USB", connectionTypeStr);
            connectionType = EPrinterType.USB;
        }

        PrinterConfig config;

        switch (connectionType) {
            case NETWORK:
                // Network/Ethernet connection
                String ip = loader.getString("printer.ip", "10.0.0.150");
                int networkPort = loader.getInt("printer.network.port", TM_T20IIIConstants.DEFAULT_PORT);
                config = PrinterConfig.network(name, ip, networkPort, timeout);
                logger.info("Configured NETWORK printer: {} at {}:{}", name, ip, networkPort);
                break;

            case USB:
                // USB/Serial COM port connection
                String comPort = loader.getString("printer.port", "COM14");
                int baudRate = loader.getInt("printer.baud", 9600);
                config = PrinterConfig.usb(name, comPort, baudRate, timeout);
                logger.info("Configured USB printer: {} at {} ({}baud)", name, comPort, baudRate);
                break;

            case NONE:
                // Dummy mode - no physical printer
                config = PrinterConfig.dummy(name);
                logger.info("Configured DUMMY printer: {}", name);
                break;

            default:
                throw new ConfigurationException("Unsupported connection type: " + connectionType);
        }

        config.validate();
        return config;
    }

    /**
     * Load VFD configuration
     */
    private VFDConfig loadVFDConfiguration() throws ConfigurationException {
        String connectionTypeStr = loader.getString("vfd.connection.type", "USB");
        EVFDConnectionType connectionType;
        try {
            connectionType = EVFDConnectionType.valueOf(connectionTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid VFD connection type '{}', defaulting to USB", connectionTypeStr);
            connectionType = EVFDConnectionType.USB;
        }

        VFDConfig config;

        switch (connectionType) {
            case USB:
                String typeStr = loader.getString("vfd.type", "FV_2030B");
                String port = loader.getString("vfd.port", "COM3");
                int baudRate = loader.getInt("vfd.baud", 9600);

                EDisplayType displayType;
                try {
                    displayType = EDisplayType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid VFD display type '{}', using FV_2030B", typeStr);
                    displayType = EDisplayType.FV_2030B;
                }

                config = VFDConfig.usb(displayType, port, baudRate);
                logger.info("Configured USB VFD: displayType={}, port={}, baud={}",
                        displayType, port, baudRate);
                break;

            case NONE:
                config = VFDConfig.dummy();
                logger.info("Configured DUMMY VFD");
                break;

            default:
                throw new ConfigurationException("Unsupported VFD connection type: " + connectionType);
        }

        config.validate();
        return config;
    }

    /**
     * Load server configuration
     */
    private ServerConfig loadServerConfiguration() throws ConfigurationException {
        int port = loader.getInt("server.port", ServerConstants.SERVER_PORT);
        String host = loader.getString("server.host", ServerConstants.SERVER_IP);
        int interval = loader.getInt("monitor.status.interval", TM_T20IIIConstants.STATUS_CHECK_INTERVAL);
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
     * Load Ingenico card reader configuration
     */
    private IngenicoConfig loadIngenicoConfiguration() throws ConfigurationException {
        String connectionTypeStr = loader.getString("ingenico.connection.type", "NONE");
        EReaderType connectionType;
        try {
            connectionType = EReaderType.valueOf(connectionTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid reader connection type '{}', defaulting to NONE (Dummy)", connectionTypeStr);
            connectionType = EReaderType.NONE;
        }

        IngenicoConfig config;

        switch (connectionType) {
            case NETWORK:
                String readerIp = loader.getString("IngenicoReaderIPAddress", "192.168.40.10");
                int ifsfPort = loader.getInt("IfsfTcpServerPort", 12710);
                int ifsfDevProxyPort = loader.getInt("IfsfDevProxyTcpServerPort", 20007);
                int transitPort = loader.getInt("IngenicoTransitTcpServerPort", 63855);
                config = IngenicoConfig.network(readerIp, ifsfPort, ifsfDevProxyPort, transitPort);
                logger.info("Configured NETWORK Ingenico reader: readerIP={}, ifsfPort={}, transitPort={}",
                        readerIp, ifsfPort, transitPort);
                break;

            case NONE:
                config = IngenicoConfig.dummy();
                logger.info("Configured DUMMY Ingenico reader");
                break;

            default:
                throw new ConfigurationException("Unsupported reader connection type: " + connectionType);
        }

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
     * Get Ingenico reader configuration
     */
    public IngenicoConfig getIngenicoConfiguration() {
        return ingenicoConfig;
    }

    /**
     * Reload configuration
     */
    public void reload() throws ConfigurationException {
        logger.info("Reloading configuration...");
        this.printerConfig = loadPrinterConfiguration();
        this.vfdConfig = loadVFDConfiguration();
        this.serverConfig = loadServerConfiguration();
        this.ingenicoConfig = loadIngenicoConfiguration();

        logger.info("Configuration reloaded successfully");
        logger.info("Printer: {}", printerConfig);
        logger.info("VFD: {}", vfdConfig);
        logger.info("Server: {}", serverConfig);
        logger.info("Ingenico: {}", ingenicoConfig);
    }
}
