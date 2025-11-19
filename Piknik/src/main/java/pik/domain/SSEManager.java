package pik.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages SSE (Server-Sent Events) clients for all services
 * @author Martin Sustik <sustik@herman.cz>
 * @since 19/11/2025
 */
public class SSEManager {
    private static final Logger logger = LoggerFactory.getLogger(SSEManager.class);

    private final ConcurrentHashMap<String, SSEClient> printerSSEClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SSEClient> vfdSSEClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SSEClient> ingenicoSSEClients = new ConcurrentHashMap<>();

    private ScheduledFuture<?> sseCleanupTask;
    private ScheduledFuture<?> sseHeartbeatTask;

    /**
     * Get printer SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getPrinterSSEClients() {
        return printerSSEClients;
    }

    /**
     * Get VFD SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getVFDSSEClients() {
        return vfdSSEClients;
    }

    /**
     * Get Ingenico SSE clients map
     */
    public ConcurrentHashMap<String, SSEClient> getIngenicoSSEClients() {
        return ingenicoSSEClients;
    }

    /**
     * Broadcast message to printer SSE clients only
     */
    public void broadcastToPrinterSSE(String message) {
        int clientCount = printerSSEClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} printer SSE clients", clientCount);
        logger.debug("Message to broadcast: [{}]", message);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : printerSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to printer client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("Printer broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Broadcast message to VFD SSE clients only
     */
    public void broadcastToVFDSSE(String message) {
        int clientCount = vfdSSEClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} VFD SSE clients", clientCount);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : vfdSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to VFD client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("VFD broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Broadcast message to Ingenico SSE clients only
     */
    public void broadcastToIngenicoSSE(String message) {
        int clientCount = ingenicoSSEClients.size();
        if (clientCount == 0) {
            return;
        }

        logger.debug("Broadcasting to {} Ingenico SSE clients", clientCount);

        int successCount = 0;
        int failureCount = 0;

        for (var entry : ingenicoSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (client.sendMessage(message)) {
                successCount++;
            } else {
                failureCount++;
                logger.debug("Failed to send to Ingenico client: {}", client);
            }
        }

        if (failureCount > 0) {
            logger.debug("Ingenico broadcast complete: {} successful, {} failed", successCount, failureCount);
        }
    }

    /**
     * Send heartbeat to all SSE clients to keep connections alive
     */
    private void sendHeartbeatToAllClients() {
        // Printer clients
        if (!printerSSEClients.isEmpty()) {
            logger.trace("Sending heartbeat to {} printer SSE clients", printerSSEClients.size());
            for (SSEClient client : printerSSEClients.values()) {
                if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                    if (!client.sendHeartbeat()) {
                        logger.debug("Heartbeat failed for printer client: {}", client);
                    }
                }
            }
        }

        // VFD clients
        if (!vfdSSEClients.isEmpty()) {
            logger.trace("Sending heartbeat to {} VFD SSE clients", vfdSSEClients.size());
            for (SSEClient client : vfdSSEClients.values()) {
                if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                    if (!client.sendHeartbeat()) {
                        logger.debug("Heartbeat failed for VFD client: {}", client);
                    }
                }
            }
        }

        // Ingenico clients
        if (!ingenicoSSEClients.isEmpty()) {
            logger.trace("Sending heartbeat to {} Ingenico SSE clients", ingenicoSSEClients.size());
            for (SSEClient client : ingenicoSSEClients.values()) {
                if (client.needsHeartbeat(pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS)) {
                    if (!client.sendHeartbeat()) {
                        logger.debug("Heartbeat failed for Ingenico client: {}", client);
                    }
                }
            }
        }
    }

    /**
     * Clean up stale SSE clients
     */
    private void cleanupStaleSSEClients() {
        int totalClients = printerSSEClients.size() + vfdSSEClients.size() + ingenicoSSEClients.size();
        if (totalClients == 0) {
            return;
        }

        logger.debug("Running SSE client cleanup check ({} printer, {} VFD, {} Ingenico clients)",
                printerSSEClients.size(), vfdSSEClients.size(), ingenicoSSEClients.size());

        int removedCount = 0;

        // Clean printer clients
        for (var entry : printerSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} printer SSE client: {}", reason, client.getClientId());
                client.close();
                printerSSEClients.remove(entry.getKey());
                removedCount++;
            }
        }

        // Clean VFD clients
        for (var entry : vfdSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} VFD SSE client: {}", reason, client.getClientId());
                client.close();
                vfdSSEClients.remove(entry.getKey());
                removedCount++;
            }
        }

        // Clean Ingenico clients
        for (var entry : ingenicoSSEClients.entrySet()) {
            SSEClient client = entry.getValue();
            if (!client.isActive() || client.isStale(pik.common.ServerConstants.SSE_CLIENT_TIMEOUT_MS)) {
                String reason = !client.isActive() ? "inactive" : "stale";
                logger.info("Removing {} Ingenico SSE client: {}", reason, client.getClientId());
                client.close();
                ingenicoSSEClients.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} stale SSE clients", removedCount);
        }
    }

    /**
     * Unregister a printer SSE client
     */
    public void unregisterPrinterSSEClient(String clientId) {
        SSEClient client = printerSSEClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("Printer SSE client unregistered: {} (total printer clients: {})", clientId, printerSSEClients.size());
        }
    }

    /**
     * Unregister a VFD SSE client
     */
    public void unregisterVFDSSEClient(String clientId) {
        SSEClient client = vfdSSEClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("VFD SSE client unregistered: {} (total VFD clients: {})", clientId, vfdSSEClients.size());
        }
    }

    /**
     * Unregister an Ingenico SSE client
     */
    public void unregisterIngenicoSSEClient(String clientId) {
        SSEClient client = ingenicoSSEClients.remove(clientId);
        if (client != null) {
            client.close();
            logger.info("Ingenico SSE client unregistered: {} (total Ingenico clients: {})", clientId, ingenicoSSEClients.size());
        }
    }

    /**
     * Start SSE management background tasks
     */
    public void startSSEManagementTasks(ScheduledExecutorService executorService) {
        sseCleanupTask = executorService.scheduleWithFixedDelay(
                this::cleanupStaleSSEClients,
                pik.common.ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                pik.common.ServerConstants.SSE_CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE cleanup task started");

        sseHeartbeatTask = executorService.scheduleWithFixedDelay(
                this::sendHeartbeatToAllClients,
                pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                pik.common.ServerConstants.SSE_HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.info("SSE heartbeat task started");
    }

    /**
     * Stop SSE management tasks
     */
    public void stopSSEManagementTasks() {
        if (sseCleanupTask != null) {
            sseCleanupTask.cancel(false);
        }
        if (sseHeartbeatTask != null) {
            sseHeartbeatTask.cancel(false);
        }
    }

    /**
     * Close all SSE clients
     */
    public void closeAllClients() {
        logger.info("Closing {} printer SSE clients, {} VFD SSE clients, {} Ingenico SSE clients",
                printerSSEClients.size(), vfdSSEClients.size(), ingenicoSSEClients.size());

        for (SSEClient client : printerSSEClients.values()) {
            client.close();
        }
        printerSSEClients.clear();

        for (SSEClient client : vfdSSEClients.values()) {
            client.close();
        }
        vfdSSEClients.clear();

        for (SSEClient client : ingenicoSSEClients.values()) {
            client.close();
        }
        ingenicoSSEClients.clear();
    }
}
