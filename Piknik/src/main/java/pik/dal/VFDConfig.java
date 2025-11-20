package pik.dal;

import pik.common.EDisplayType;

/**
 * Type-safe configuration for VFD display
 * Supports USB/Serial (VCP) connection and Dummy mode for testing
 * Display type NONE indicates dummy mode
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public record VFDConfig(EDisplayType displayType, String portName, int baudRate) {

    /**
     * Factory method: Create USB/Serial VFD configuration
     */
    public static VFDConfig usb(EDisplayType displayType, String portName, int baudRate) {
        return new VFDConfig(displayType, portName, baudRate);
    }

    /**
     * Factory method: Create dummy VFD configuration (testing mode)
     */
    public static VFDConfig dummy() {
        return new VFDConfig(EDisplayType.NONE, null, 0);
    }

    @Override
    public String toString() {
        if (displayType == EDisplayType.NONE) {
            return "VFDConfiguration{type=NONE (Dummy)}";
        }
        return String.format("VFDConfiguration{displayType=%s, port='%s', baud=%d}",
                displayType, portName, baudRate);
    }

    /**
     * Validate configuration based on display type
     */
    public void validate() throws ConfigurationException {
        if (displayType == null) {
            throw new ConfigurationException("VFD display type cannot be null");
        }

        // No validation needed for dummy mode
        if (displayType == EDisplayType.NONE) {
            return;
        }

        // Validate USB/Serial configuration
        if (portName == null || portName.trim().isEmpty()) {
            throw new ConfigurationException("VFD port name cannot be empty");
        }
        if (baudRate < 300 || baudRate > 115200) {
            throw new ConfigurationException("VFD baud rate must be between 300 and 115200");
        }
    }

    /**
     * Check if this is a dummy/simulated VFD
     */
    public boolean isDummy() {
        return displayType == EDisplayType.NONE;
    }
}
