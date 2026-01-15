package pik.domain.ingenico.unlock;

/**
 * SAM unlock session tracking state.
 * Thread-safe session state for async SAM unlock process.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
public class UnlockSession {
    /**
     * Session status
     */
    public enum Status {
        PENDING,           // Session created
        WAITING_FOR_CARD,  // Card tapping started
        PROCESSING,        // Card detected, executing unlock
        COMPLETED,         // Successfully unlocked
        FAILED,            // Failed (error or timeout)
        EXPIRED            // Session expired
    }

    private final String sessionId;
    private final String pin;
    private Status status;
    private String errorMessage;
    private final long createdAt;
    private long updatedAt;

    public UnlockSession(String sessionId, String pin) {
        this.sessionId = sessionId;
        this.pin = pin;
        this.status = Status.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
    }

    public synchronized void setStatus(Status status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void setError(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - createdAt > timeoutMillis;
    }

    // Getters (all synchronized for thread-safety)
    public synchronized String getSessionId() {
        return sessionId;
    }

    public synchronized String getPin() {
        return pin;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized String getErrorMessage() {
        return errorMessage;
    }

    public synchronized long getCreatedAt() {
        return createdAt;
    }

    public synchronized long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("UnlockSession{id=%s, status=%s, created=%d, updated=%d}",
                sessionId, status, createdAt, updatedAt);
    }
}