package pik.domain.ingenico.cardread.dto;

import pik.domain.ingenico.cardread.CardReadSession;

/**
 * Response DTO for card read status polling.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadStatusResponse {
    private String sessionId;
    private String status;
    private CardContentDTO cardData;
    private String errorMessage;
    private long createdAt;
    private long updatedAt;

    public CardReadStatusResponse() {
    }

    /**
     * Create response from session
     */
    public static CardReadStatusResponse fromSession(CardReadSession session) {
        if (session == null) {
            return null;
        }

        CardReadStatusResponse response = new CardReadStatusResponse();
        response.sessionId = session.getSessionId();
        response.status = session.getStatus().name();
        response.errorMessage = session.getErrorMessage();
        response.createdAt = session.getCreatedAt();
        response.updatedAt = session.getUpdatedAt();

        if (session.getCardData() != null) {
            response.cardData = CardContentDTO.fromCardDuk(session.getCardData());
        }

        return response;
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public CardContentDTO getCardData() {
        return cardData;
    }

    public void setCardData(CardContentDTO cardData) {
        this.cardData = cardData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}