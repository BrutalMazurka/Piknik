package pik.dal;

import pik.common.EDisplayType;

/**
 * Type-safe configuration for VFD display
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public record VFDConfig(EDisplayType displayType, String portName, int baudRate) {

    @Override
    public String toString() {
        return String.format("VFDConfiguration{type=%s, port='%s', baud=%d}",
                displayType, portName, baudRate);
    }

    /**
     * Validate configuration
     */
    public void validate() throws ConfigurationException {
        if (displayType == null) {
            throw new ConfigurationException("VFD display type cannot be null");
        }
        if (portName == null || portName.trim().isEmpty()) {
            throw new ConfigurationException("VFD port name cannot be empty");
        }
        if (baudRate < 300 || baudRate > 115200) {
            throw new ConfigurationException("VFD baud rate must be between 300 and 115200");
        }
    }
}
