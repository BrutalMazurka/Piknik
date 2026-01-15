package pik.domain.ingenico.unlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for SAM unlock sessions.
 * Thread-safe session lifecycle management with automatic cleanup.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 15/01/2026
 */
@Singleton
public class UnlockSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(UnlockSessionManager.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, UnlockSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create new unlock session
     */
    public UnlockSession createSession(String pin) {
        String sessionId = UUID.randomUUID().toString();
        UnlockSession session = new UnlockSession(sessionId, pin);
        sessions.put(sessionId, session);
        logger.info("Created unlock session: {}", sessionId);
        return session;
    }

    /**
     * Get session by ID
     */
    public UnlockSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Update session status
     */
    public void updateStatus(String sessionId, UnlockSession.Status status) {
        UnlockSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            logger.info("Session {} status updated to {}", sessionId, status);
        }
    }

    /**
     * Set session error
     */
    public void setError(String sessionId, String errorMessage) {
        UnlockSession session = sessions.get(sessionId);
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
        logger.info("Removed unlock session: {}", sessionId);
    }

    /**
     * Remove expired sessions (older than 5 minutes)
     * Should be called periodically
     */
    public void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, UnlockSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(SESSION_TIMEOUT_MILLIS)) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired unlock sessions", removed);
        }
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}