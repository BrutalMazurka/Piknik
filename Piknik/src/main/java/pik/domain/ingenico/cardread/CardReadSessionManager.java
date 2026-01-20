package pik.domain.ingenico.cardread;

import epis5.duk.bck.core.card.CardDuk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for card reading sessions.
 * Thread-safe session lifecycle management with automatic cleanup.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
@Singleton
public class CardReadSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(CardReadSessionManager.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, CardReadSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create new card read session
     */
    public CardReadSession createSession(String readSchema, int timeoutMillis) {
        String sessionId = UUID.randomUUID().toString();
        CardReadSession session = new CardReadSession(sessionId, readSchema, timeoutMillis);
        sessions.put(sessionId, session);
        logger.info("Created card read session: {}", sessionId);
        return session;
    }

    /**
     * Get session by ID
     */
    public CardReadSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Update session status
     */
    public void updateStatus(String sessionId, CardReadSession.Status status) {
        CardReadSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            logger.info("Session {} status updated to {}", sessionId, status);
        }
    }

    /**
     * Set session card data (marks as COMPLETED)
     */
    public void setCardData(String sessionId, CardDuk cardData) {
        CardReadSession session = sessions.get(sessionId);
        if (session != null) {
            session.setCardData(cardData);
            logger.info("Session {} completed with card data", sessionId);
        }
    }

    /**
     * Set session error
     */
    public void setError(String sessionId, String errorMessage) {
        CardReadSession session = sessions.get(sessionId);
        if (session != null) {
            session.setError(errorMessage);
            logger.error("Session {} failed: {}", sessionId, errorMessage);
        }
    }

    /**
     * Remove session
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        logger.info("Removed card read session: {}", sessionId);
    }

    /**
     * Remove expired sessions (older than 5 minutes)
     * Should be called periodically
     */
    public void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, CardReadSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(SESSION_TIMEOUT_MILLIS)) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired card read sessions", removed);
        }
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
