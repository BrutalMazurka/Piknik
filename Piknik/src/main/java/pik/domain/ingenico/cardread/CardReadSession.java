package pik.domain.ingenico.cardread;

import epis5.duk.bck.core.card.CardDuk;

/**
 * Card reading session tracking state.
 * Thread-safe session state for async card read process.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadSession {
    /**
     * Session status
     */
    public enum Status {
        PENDING,           // Session created
        WAITING_FOR_CARD,  // Card tapping started
        PROCESSING,        // Card detected, reading data
        COMPLETED,         // Successfully read card
        FAILED,            // Failed (error or timeout)
        EXPIRED            // Session expired
    }

    private final String sessionId;
    private final String readSchema;
    private final int timeoutMillis;
    private Status status;
    private CardDuk cardData;
    private String errorMessage;
    private final long createdAt;
    private long updatedAt;

    public CardReadSession(String sessionId, String readSchema, int timeoutMillis) {
        this.sessionId = sessionId;
        this.readSchema = readSchema;
        this.timeoutMillis = timeoutMillis;
        this.status = Status.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
    }

    public synchronized void setStatus(Status status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void setCardData(CardDuk cardData) {
        this.cardData = cardData;
        this.status = Status.COMPLETED;
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

    public synchronized String getReadSchema() {
        return readSchema;
    }

    public synchronized int getTimeoutMillis() {
        return timeoutMillis;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized CardDuk getCardData() {
        return cardData;
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
        return String.format("CardReadSession{id=%s, status=%s, schema=%s, created=%d, updated=%d}",
                sessionId, status, readSchema, createdAt, updatedAt);
    }
}