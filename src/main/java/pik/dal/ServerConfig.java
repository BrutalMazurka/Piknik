package pik.dal;

/**
 * Type-safe configuration for server
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class ServerConfig {
    private final int port;
    private final String host;
    private final int statusCheckInterval;
    private final boolean monitoringEnabled;

    public ServerConfig(int port, String host, int statusCheckInterval, boolean monitoringEnabled) {
        this.port = port;
        this.host = host;
        this.statusCheckInterval = statusCheckInterval;
        this.monitoringEnabled = monitoringEnabled;
    }

    public int getPort() { return port; }
    public String getHost() { return host; }
    public int getStatusCheckInterval() { return statusCheckInterval; }
    public boolean isMonitoringEnabled() { return monitoringEnabled; }

    @Override
    public String toString() {
        return String.format("ServerConfiguration{port=%d, host='%s', monitoring=%s}",
                port, host, monitoringEnabled);
    }

    /**
     * Validate configuration
     */
    public void validate() throws ConfigurationException {
        if (port < 1 || port > 65535) {
            throw new ConfigurationException("Server port must be between 1 and 65535");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new ConfigurationException("Server host cannot be empty");
        }
        if (statusCheckInterval < 1000) {
            throw new ConfigurationException("Status check interval must be at least 1000ms");
        }
    }
}
