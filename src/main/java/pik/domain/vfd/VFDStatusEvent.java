package pik.domain.vfd;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
public class VFDStatusEvent {
    private final VFDStatus status;
    private final String    source;
    private final long      timestamp;

    public VFDStatusEvent(VFDStatus status, String source) {
        this.status = status;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    public VFDStatus getStatus() {
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
        return "VFDStatusEvent [status=" + status + ", source=" + source + ", timestamp=" + timestamp + "]";
    }

}
