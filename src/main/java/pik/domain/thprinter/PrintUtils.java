package pik.domain.thprinter;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 28/08/2025
 */
public final class PrintUtils {
    private PrintUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * ESC/POS formatting commands
     */
    public static final String ESC = "\u001b";
    public static final String GS = "\u001D";
    public static final String PRINTER_INIT = ESC + "@";
    public static final String BOLD_ON = ESC + "E" + "\u0001";
    public static final String BOLD_OFF = ESC + "E" + "\0";
    public static final String DOUBLE_ON = GS + "!" + "\u0011";  // 2x sized text (double-high + double-wide)
    public static final String DOUBLE_OFF = GS + "!" + "\0";

    /**
     * Format price for printing
     */
    public static String formatPrice(double price) {
        return String.format("%.2f Kč", price);
    }

    /**
     * Create separator line
     */
    public static String createSeparator(int length, char character) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(character);
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Format item line with price alignment
     */
    public static String formatItemLine(String item, double price, int lineWidth) {
        String priceStr = formatPrice(price);
        int spaces = lineWidth - item.length() - priceStr.length();
        StringBuilder sb = new StringBuilder();
        sb.append(item);
        for (int i = 0; i < spaces; i++) {
            sb.append(" ");
        }
        sb.append(priceStr);
        return sb.toString();
    }
}
