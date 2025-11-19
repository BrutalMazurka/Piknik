package pik.common;

/**
 * Ingenico Card Reader connection type enumeration
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public enum EReaderType {
    NETWORK,  // Network/TCP connection to physical reader
    NONE      // Dummy mode (no physical reader)
}
