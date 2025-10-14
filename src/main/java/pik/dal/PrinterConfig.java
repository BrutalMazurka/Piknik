package pik.dal;

/**
 * Type-safe configuration for printer
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class PrinterConfig {
    private final String name;
    private final String ipAddress;
    private final int port;
    private final int connectionTimeout;

    public PrinterConfig(String name, String ipAddress, int port, int connectionTimeout) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
    }

    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public int getConnectionTimeout() { return connectionTimeout; }

    public String getLogicalName() {
        return name.replaceAll("[^A-Za-z0-9]", "_");
    }

    public String getConnectionString() {
        return String.format("TYPE=Ethernet;IP=%s;PORT=%d;TIMEOUT=%d",
                ipAddress, port, connectionTimeout);
    }

    @Override
    public String toString() {
        return String.format("PrinterConfiguration{name='%s', ip='%s', port=%d, timeout=%d}",
                name, ipAddress, port, connectionTimeout);
    }

    /**
     * Validate configuration
     */
    public void validate() throws ConfigurationException {
        if (name == null || name.trim().isEmpty()) {
            throw new ConfigurationException("Printer name cannot be empty");
        }
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new ConfigurationException("Printer IP address cannot be empty");
        }
        if (port < 1 || port > 65535) {
            throw new ConfigurationException("Printer port must be between 1 and 65535");
        }
        if (connectionTimeout < 1000) {
            throw new ConfigurationException("Connection timeout must be at least 1000ms");
        }
    }
}
