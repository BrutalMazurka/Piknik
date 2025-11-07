package pik.dal;

import pik.common.EPrinterType;

/**
 * Type-safe configuration for printer
 * Supports both Network (Ethernet) and USB/Serial (COM port) connections
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public record PrinterConfig(
        String name,
        String ipAddress,
        int networkPort,
        int connectionTimeout,
        String comPort,
        int baudRate,
        EPrinterType connectionType) {

    /**
     * Factory method: Create network printer configuration
     */
    public static PrinterConfig network(String name, String ipAddress, int networkPort, int connectionTimeout) {
        return new PrinterConfig(name, ipAddress, networkPort, connectionTimeout, null, 0, EPrinterType.NETWORK);
    }

    /**
     * Factory method: Create USB/Serial printer configuration
     */
    public static PrinterConfig usb(String name, String comPort, int baudRate, int connectionTimeout) {
        return new PrinterConfig(name, null, 0, connectionTimeout, comPort, baudRate, EPrinterType.USB);
    }

    /**
     * Factory method: Create dummy printer configuration
     */
    public static PrinterConfig dummy(String name) {
        return new PrinterConfig(name, null, 0, 10000, null, 0, EPrinterType.NONE);
    }

    public String getLogicalName() {
        return name.replaceAll("[^A-Za-z0-9]", "_");
    }

    public String getConnectionString() {
        return switch (connectionType) {
            case NETWORK -> String.format("TYPE=Ethernet;IP=%s;PORT=%d;TIMEOUT=%d",
                    ipAddress, networkPort, connectionTimeout);
            case USB -> String.format("TYPE=Serial;PORT=%s;BAUD=%d;TIMEOUT=%d",
                    comPort, baudRate, connectionTimeout);
            case NONE -> "TYPE=Dummy";
        };
    }

    @Override
    public String toString() {
        return switch (connectionType) {
            case NETWORK -> String.format("PrinterConfiguration{type=NETWORK, name='%s', ip='%s', port=%d, timeout=%d}",
                    name, ipAddress, networkPort, connectionTimeout);
            case USB -> String.format("PrinterConfiguration{type=USB, name='%s', comPort='%s', baud=%d, timeout=%d}",
                    name, comPort, baudRate, connectionTimeout);
            case NONE -> String.format("PrinterConfiguration{type=NONE (Dummy), name='%s'}", name);
        };
    }

    /**
     * Validate configuration based on connection type
     */
    public void validate() throws ConfigurationException {
        if (name == null || name.trim().isEmpty()) {
            throw new ConfigurationException("Printer name cannot be empty");
        }

        if (connectionType == null) {
            throw new ConfigurationException("Connection type cannot be null");
        }

        switch (connectionType) {
            case NETWORK -> validateNetworkConfig();
            case USB -> validateUSBConfig();
            case NONE -> { /* No validation needed for dummy mode */ }
        }

        if (connectionType != EPrinterType.NONE && connectionTimeout < 1000) {
            throw new ConfigurationException("Connection timeout must be at least 1000ms");
        }
    }

    private void validateNetworkConfig() throws ConfigurationException {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new ConfigurationException("Printer IP address cannot be empty for NETWORK connection");
        }
        if (networkPort < 1 || networkPort > 65535) {
            throw new ConfigurationException("Network port must be between 1 and 65535");
        }
    }

    private void validateUSBConfig() throws ConfigurationException {
        if (comPort == null || comPort.trim().isEmpty()) {
            throw new ConfigurationException("COM port cannot be empty for USB connection");
        }
        if (baudRate < 300 || baudRate > 115200) {
            throw new ConfigurationException("Baud rate must be between 300 and 115200");
        }
    }

    /**
     * Check if this is a dummy/simulated printer
     */
    public boolean isDummy() {
        return connectionType == EPrinterType.NONE;
    }
}
