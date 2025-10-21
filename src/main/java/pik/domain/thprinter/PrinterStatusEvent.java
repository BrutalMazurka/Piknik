package pik.domain.thprinter;

/**
 * Observed event/subject for the printer
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
public class PrinterStatusEvent {
    private final PrinterStatus status;
    private final String        source;
    private final long          timestamp;

    public PrinterStatusEvent(PrinterStatus status, String source) {
        this.status = status;
        this.timestamp = System.currentTimeMillis();
        this.source = source;
    }

    public PrinterStatus getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "PrinterStatusEvent [status=" + status + ", source=" + source + ", timestamp=" + timestamp + "]";
    }

}
