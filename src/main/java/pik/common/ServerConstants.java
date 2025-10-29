package pik.common;

/**
 * Web Server Parameters
 * @author Martin Sustik <sustik@herman.cz>
 * @since 07/10/2025
 */
public final class ServerConstants {
    private ServerConstants() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public static final int SERVER_PORT = 8080;
    public static final String SERVER_IP = "0.0.0.0";
    public static final int THREAD_POOL_SIZE = 3;
    public static final int THREAD_POOL_KEEP_ALIVE_TIME = 1;

    // SSE Configuration
    public static final long SSE_CLIENT_TIMEOUT_MS = 300_000;        // 5 minutes
    public static final long SSE_HEARTBEAT_INTERVAL_MS = 30_000;     // 30 seconds
    public static final long SSE_CLEANUP_INTERVAL_MS = 60_000;       // 1 minute
    public static final int SSE_MAX_CLIENTS = 10;                    // Maximum concurrent SSE clients
}
