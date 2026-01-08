package pik.domain;

import io.javalin.http.sse.SseClient;
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
    private final SseClient sseClient;
    private final long connectedAt;
    private volatile long lastMessageAt;
    private volatile long lastHeartbeatAt;
    private final AtomicLong messagesSent;
    private volatile boolean active;

    public SSEClient(String clientId, SseClient sseClient) {
        this.clientId = clientId;
        this.sseClient = sseClient;
        this.connectedAt = System.currentTimeMillis();
        this.lastMessageAt = connectedAt;
        this.lastHeartbeatAt = connectedAt;
        this.messagesSent = new AtomicLong(0);
        this.active = true;
    }

    /**
     * Send a message to this client
     * @param message The message to send (format: "event: type\ndata: content\n\n")
     * @return true if message was sent successfully, false if client is disconnected
     */
    public boolean sendMessage(String message) {
        if (!active || sseClient == null) {
            return false;
        }

        try {
            // Check if message is already formatted as SSE (contains "event:" or "data:")
            if (message.contains("event:") || message.contains("data:")) {
                // Message is pre-formatted SSE, parse and send properly
                String eventType = "message";
                StringBuilder dataBuilder = new StringBuilder();

                String[] lines = message.split("\n");
                logger.trace("Client {}: Parsing {} lines", clientId, lines.length);
                for (String line : lines) {
                    logger.trace("Client {}: Line=[{}]", clientId, line);
                    if (line.startsWith("event: ")) {
                        eventType = line.substring(7).trim();
                    } else if (line.startsWith("data: ")) {
                        // Accumulate all data lines
                        if (dataBuilder.length() > 0) {
                            dataBuilder.append("\n");
                        }
                        dataBuilder.append(line.substring(6).trim());
                    }
                }

                String data = dataBuilder.toString();
                if (data.isEmpty()) {
                    logger.warn("Empty data in SSE message for client {}", clientId);
                    return false;
                }

                logger.trace("Sending SSE event '{}' to client {}: {}", eventType, clientId, data.length() > 100 ? data.substring(0, 100) + "..." : data);

                sseClient.sendEvent(eventType, data);
            } else {
                // Plain message, send as-is with default event type
                logger.trace("Sending plain message to client {}: {}", clientId, message.length() > 100 ? message.substring(0, 100) + "..." : message);
                sseClient.sendEvent("message", message);
            }

            lastMessageAt = System.currentTimeMillis();
            messagesSent.incrementAndGet();
            return true;
        } catch (Exception e) {
            logger.error("Failed to send message to client {}: {}", clientId, e.getMessage(), e);
            active = false;
            return false;
        }
    }

    /**
     * Send a heartbeat/keepalive message
     * @return true if heartbeat was sent successfully
     */
    public boolean sendHeartbeat() {
        if (!active || sseClient == null) {
            return false;
        }

        try {
            sseClient.sendComment("heartbeat");
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
     * @return true if client hasn't received messages OR heartbeats within timeout
     */
    public boolean isStale(long timeoutMs) {
        long now = System.currentTimeMillis();
        long timeSinceLastMessage = now - lastMessageAt;
        long timeSinceLastHeartbeat = now - lastHeartbeatAt;
        // Client is alive if EITHER messages or heartbeats are being sent
        // Only stale if BOTH haven't been updated within timeout
        long timeSinceLastActivity = Math.min(timeSinceLastMessage, timeSinceLastHeartbeat);
        boolean stale = timeSinceLastActivity > timeoutMs;

        if (stale) {
            logger.warn("Client {} is STALE: timeSinceMsg={}ms, timeSinceHB={}ms, timeout={}ms",
                clientId, timeSinceLastMessage, timeSinceLastHeartbeat, timeoutMs);
        }

        return stale;
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
            if (sseClient != null) {
                sseClient.close();
            }
            logger.debug("Closed SSE client {}", clientId);
        } catch (Exception e) {
            logger.debug("Error closing SSE client {}: {}", clientId, e.getMessage());
        }
    }

    // Getters
    public String getClientId() {
        return clientId;
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
        return "N/A";  // Javalin's SseClient doesn't expose remote address
    }

    public String getUserAgent() {
        return "N/A";  // Not available through SseClient
    }

    public boolean isActive() {
        return active;
    }

    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedAt;
    }

    @Override
    public String toString() {
        return String.format("SSEClient{id='%s', connected=%dms, messages=%d, active=%s}",
                clientId, getConnectionDuration(), messagesSent.get(), active);
    }
}
