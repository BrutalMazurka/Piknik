package pik.domain.ingenico.cardread.dto;

/**
 * Response DTO for starting card read operation.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadStartResponse {
    private String sessionId;

    public CardReadStartResponse() {
    }

    public CardReadStartResponse(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}