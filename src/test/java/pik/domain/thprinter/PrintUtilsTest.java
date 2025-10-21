package pik.domain.thprinter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PrintUtils
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class PrintUtilsTest {

    @Test
    @DisplayName("Should format price correctly")
    void shouldFormatPrice() {
        // When
        String formatted = PrintUtils.formatPrice(25.50);

        // Then
        assertThat(formatted).isEqualTo("25.50 Kč");
    }

    @Test
    @DisplayName("Should format price with two decimal places")
    void shouldFormatPriceWithTwoDecimals() {
        // When
        String formatted = PrintUtils.formatPrice(10.0);

        // Then
        assertThat(formatted).isEqualTo("10.00 Kč");
    }

    @Test
    @DisplayName("Should create separator line")
    void shouldCreateSeparatorLine() {
        // When
        String separator = PrintUtils.createSeparator(10, '-');

        // Then
        assertThat(separator).isEqualTo("----------\n");
    }

    @Test
    @DisplayName("Should format item line with correct spacing")
    void shouldFormatItemLine() {
        // When
        String line = PrintUtils.formatItemLine("Coffee", 5.50, 30);

        // Then
        assertThat(line).hasSize(30);
        assertThat(line).startsWith("Coffee");
        assertThat(line).endsWith("5.50 Kč");
        assertThat(line).contains("  "); // Should have spacing
    }

    @Test
    @DisplayName("Should handle long item names")
    void shouldHandleLongItemNames() {
        // When
        String line = PrintUtils.formatItemLine("Very Long Coffee Name", 5.50, 30);

        // Then
        assertThat(line).hasSize(30);
    }
}
