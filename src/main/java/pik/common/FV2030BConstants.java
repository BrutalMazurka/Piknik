package pik.common;

/**
 * VIRTUOS FV-2030B VFD Display Constants
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/10/2025
 */
public final class FV2030BConstants {
    private FV2030BConstants() {
        throw new AssertionError("Utility class cannot be instantiated");
    }
    public static final int MAX_ROWS = 2;
    public static final int MAX_COLUMNS = 20;
    public static final String VFD_ENCODING = "CP852";     // Welcome to the days of MS-DOS!
}
