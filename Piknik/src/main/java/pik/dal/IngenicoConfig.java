package pik.dal;

import pik.common.EReaderType;

/**
 * Type-safe configuration for Ingenico Card Reader
 * Supports Network connection to physical reader and Dummy mode for testing
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public record IngenicoConfig(
        String readerIpAddress,
        int ifsfTcpServerPort,
        int ifsfDevProxyTcpServerPort,
        int transitTcpServerPort,
        EReaderType connectionType) {

    /**
     * Factory method: Create network reader configuration
     */
    public static IngenicoConfig network(String readerIpAddress, int ifsfPort, int ifsfDevProxyPort, int transitPort) {
        return new IngenicoConfig(readerIpAddress, ifsfPort, ifsfDevProxyPort, transitPort, EReaderType.NETWORK);
    }

    /**
     * Factory method: Create dummy reader configuration (testing mode)
     */
    public static IngenicoConfig dummy() {
        return new IngenicoConfig("127.0.0.1", 0, 0, 0, EReaderType.NONE);
    }

    @Override
    public String toString() {
        return switch (connectionType) {
            case NETWORK -> String.format(
                    "IngenicoConfig{type=NETWORK, readerIP='%s', ifsfPort=%d, ifsfDevProxyPort=%d, transitPort=%d}",
                    readerIpAddress, ifsfTcpServerPort, ifsfDevProxyTcpServerPort, transitTcpServerPort);
            case NONE -> "IngenicoConfig{type=NONE (Dummy)}";
        };
    }

    /**
     * Validate configuration based on connection type
     */
    public void validate() throws ConfigurationException {
        if (connectionType == null) {
            throw new ConfigurationException("Reader connection type cannot be null");
        }

        switch (connectionType) {
            case NETWORK -> validateNetworkConfig();
            case NONE -> { /* No validation needed for dummy mode */ }
        }
    }

    private void validateNetworkConfig() throws ConfigurationException {
        if (readerIpAddress == null || readerIpAddress.trim().isEmpty()) {
            throw new ConfigurationException("Reader IP address cannot be empty for NETWORK connection");
        }

        validatePort("IFSF TCP Server", ifsfTcpServerPort);
        validatePort("IFSF DevProxy TCP Server", ifsfDevProxyTcpServerPort);
        validatePort("Transit TCP Server", transitTcpServerPort);
    }

    private void validatePort(String portName, int port) throws ConfigurationException {
        if (port < 1 || port > 65535) {
            throw new ConfigurationException(portName + " port must be between 1 and 65535");
        }
    }

    /**
     * Check if this is a dummy/simulated reader
     */
    public boolean isDummy() {
        return connectionType == EReaderType.NONE;
    }
}
