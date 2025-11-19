package pik.dal;

/**
 * Type-safe configuration for server
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public record ServerConfig(int port, String host, int statusCheckInterval, boolean monitoringEnabled,
                           int threadPoolSize, StartupMode startupMode) {
    public ServerConfig(int port, String host, int statusCheckInterval,
                        boolean monitoringEnabled, int threadPoolSize) {
        this(port, host, statusCheckInterval, monitoringEnabled, threadPoolSize, StartupMode.LENIENT);
    }

    @Override
    public String toString() {
        return String.format("ServerConfiguration{port=%d, host='%s', monitoring=%s, startupMode=%s}",
                port, host, monitoringEnabled, startupMode);
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
        if (startupMode == null) {
            throw new ConfigurationException("Startup mode cannot be null");
        }
    }

}
