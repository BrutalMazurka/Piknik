package pik.common;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 07/10/2025
 */
public final class PrinterConstants {
    private PrinterConstants() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
    public static final int DEFAULT_PORT = 9100;
    public static final int STATUS_CHECK_INTERVAL = 5000;
    public static final int BRIGHTNESS_MIN = 1;
    public static final int BRIGHTNESS_MAX = 4;
    public static final int GRAYSCALE_THRESHOLD = 128;
    public static final int FULL_CUT_PERCENTAGE = 100;
    public static final int CONNECTION_STABILIZATION_DELAY_MS = 500;
    public static final int DEMO_STEP_DELAY_MS = 500;
    public static final int MIN_FONT_SIZE = 1;
    public static final int MAX_FONT_SIZE = 8;
}
