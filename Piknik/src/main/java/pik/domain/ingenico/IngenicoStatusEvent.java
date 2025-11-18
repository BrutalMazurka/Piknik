package pik.domain.ingenico;

/**
 * Observable event for Ingenico reader status changes
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public class IngenicoStatusEvent {
    private final IngenicoStatus status;
    private final String source;
    private final long timestamp;

    public IngenicoStatusEvent(IngenicoStatus status, String source) {
        this.status = status;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    public IngenicoStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "IngenicoStatusEvent [status=" + status + ", source=" + source + ", timestamp=" + timestamp + "]";
    }
}
