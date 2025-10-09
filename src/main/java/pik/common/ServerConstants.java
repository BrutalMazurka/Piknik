package pik.common;

/**
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
}
