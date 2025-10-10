package pik.domain.thprinter;

/**
 * Printer state machine states
 * @author Martin Sustik <sustik@herman.cz>
 * @since 10/10/2025
 */
public enum PrinterState {
    UNINITIALIZED,      // Initial state
    OPENING,            // Opening device
    OPENED,             // Device opened
    CLAIMING,           // Claiming device
    CLAIMED,            // Device claimed
    ENABLING,           // Enabling device
    ENABLED,            // Device enabled
    CONFIGURING,        // Configuring printer settings
    READY,              // Ready for operations
    ERROR               // Error state
}
