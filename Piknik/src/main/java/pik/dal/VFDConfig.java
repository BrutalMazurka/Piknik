package pik.dal;

import pik.common.EDisplayType;
import pik.common.EVFDConnectionType;

/**
 * Type-safe configuration for VFD display
 * Supports USB/Serial (VCP) connection and Dummy mode for testing
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public record VFDConfig(
        EDisplayType displayType,
        String portName,
        int baudRate,
        EVFDConnectionType connectionType) {

    /**
     * Factory method: Create USB/Serial VFD configuration
     */
    public static VFDConfig usb(EDisplayType displayType, String portName, int baudRate) {
        return new VFDConfig(displayType, portName, baudRate, EVFDConnectionType.USB);
    }

    /**
     * Factory method: Create dummy VFD configuration (testing mode)
     */
    public static VFDConfig dummy() {
        return new VFDConfig(EDisplayType.NONE, null, 0, EVFDConnectionType.NONE);
    }

    @Override
    public String toString() {
        return switch (connectionType) {
            case USB -> String.format("VFDConfiguration{type=USB, displayType=%s, port='%s', baud=%d}",
                    displayType, portName, baudRate);
            case NONE -> "VFDConfiguration{type=NONE (Dummy)}";
        };
    }

    /**
     * Validate configuration based on connection type
     */
    public void validate() throws ConfigurationException {
        if (connectionType == null) {
            throw new ConfigurationException("VFD connection type cannot be null");
        }

        switch (connectionType) {
            case USB -> validateUSBConfig();
            case NONE -> { /* No validation needed for dummy mode */ }
        }
    }

    private void validateUSBConfig() throws ConfigurationException {
        if (displayType == null) {
            throw new ConfigurationException("VFD display type cannot be null");
        }
        if (portName == null || portName.trim().isEmpty()) {
            throw new ConfigurationException("VFD port name cannot be empty for USB connection");
        }
        if (baudRate < 300 || baudRate > 115200) {
            throw new ConfigurationException("VFD baud rate must be between 300 and 115200");
        }
    }

    /**
     * Check if this is a dummy/simulated VFD
     */
    public boolean isDummy() {
        return connectionType == EVFDConnectionType.NONE;
    }
}
