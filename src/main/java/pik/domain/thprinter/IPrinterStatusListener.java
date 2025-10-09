package pik.domain.thprinter;

/**
 * The observer interface
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
public interface IPrinterStatusListener {
    void onStatusChanged(PrinterStatusEvent event);
}
