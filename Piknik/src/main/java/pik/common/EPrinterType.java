package pik.common;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 29/10/2025
 */
public enum EPrinterType {
    USB,      // COM/Serial port connection
    NETWORK,  // Ethernet/TCP connection
    NONE      // Dummy mode (no physical printer)
}
