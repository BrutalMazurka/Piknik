package pik.domain;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an SSE (Server-Sent Events) client connection with metadata
 * @author Martin Sustik <sustik@herman.cz>
 * @since 10/10/2025
 */
public class SSEClient {
    private static final Logger logger = LoggerFactory.getLogger(SSEClient.class);

    private final String clientId;
    private final Context context;
    private final long connectedAt;
    private volatile long lastMessageAt;
    private volatile long lastHeartbeatAt;
    private final AtomicLong messagesSent;
    private final String remoteAddress;
    private final String userAgent;
    private volatile boolean active;

    public SSEClient(String clientId, Context context) {
        this.clientId = clientId;
        this.context = context;
        this.connectedAt = System.currentTimeMillis();
        this.lastMessageAt = connectedAt;
        this.lastHeartbeatAt = connectedAt;
        this.messagesSent = new AtomicLong(0);
        this.remoteAddress = context.ip();
        this.userAgent = context.userAgent();
        this.active = true;
    }

    /**
     * Send a message to this client
     * @param message The message to send
     * @return true if message was sent successfully, false if client is disconnected
     */
    public boolean sendMessage(String message) {
        if (!active) {
            return false;
        }

        try {
            context.result(message);
            lastMessageAt = System.currentTimeMillis();
            messagesSent.incrementAndGet();
            return true;
        } catch (Exception e) {
            logger.debug("Failed to send message to client {}: {}", clientId, e.getMessage());
            active = false;
            return false;
        }
    }

    /**
     * Send a heartbeat/keepalive message
     * @return true if heartbeat was sent successfully
     */
    public boolean sendHeartbeat() {
        if (!active) {
            return false;
        }

        try {
            // SSE comment format - ignored by clients but keeps connection alive
            context.result(": heartbeat\n\n");
            lastHeartbeatAt = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            logger.debug("Failed to send heartbeat to client {}: {}", clientId, e.getMessage());
            active = false;
            return false;
        }
    }

    /**
     * Check if this client should be considered stale
     * @param timeoutMs Timeout in milliseconds
     * @return true if client hasn't received messages within timeout
     */
    public boolean isStale(long timeoutMs) {
        long now = System.currentTimeMillis();
        long timeSinceLastMessage = now - lastMessageAt;
        return timeSinceLastMessage > timeoutMs;
    }

    /**
     * Check if client needs a heartbeat
     * @param heartbeatIntervalMs Heartbeat interval in milliseconds
     * @return true if client needs a heartbeat
     */
    public boolean needsHeartbeat(long heartbeatIntervalMs) {
        long now = System.currentTimeMillis();
        long timeSinceLastHeartbeat = now - lastHeartbeatAt;
        return timeSinceLastHeartbeat > heartbeatIntervalMs;
    }

    /**
     * Close the client connection
     */
    public void close() {
        active = false;
        try {
            context.req().getAsyncContext().complete();
            logger.debug("Closed SSE client {}", clientId);
        } catch (Exception e) {
            logger.debug("Error closing SSE client {}: {}", clientId, e.getMessage());
        }
    }

    // Getters
    public String getClientId() {
        return clientId;
    }

    public Context getContext() {
        return context;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public long getLastMessageAt() {
        return lastMessageAt;
    }

    public long getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isActive() {
        return active;
    }

    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedAt;
    }

    @Override
    public String toString() {
        return String.format("SSEClient{id='%s', ip='%s', connected=%dms, messages=%d, active=%s}",
                clientId, remoteAddress, getConnectionDuration(), messagesSent.get(), active);
    }
}
