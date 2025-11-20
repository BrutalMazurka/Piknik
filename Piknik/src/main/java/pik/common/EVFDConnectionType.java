package pik.common;

/**
 * VFD (Visual Feedback Display) connection type enumeration
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/11/2025
 */
public enum EVFDConnectionType {
    USB,      // USB/Serial (VCP) connection
    NONE      // Dummy mode (no physical display)
}
