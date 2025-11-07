package pik.domain.thprinter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PrinterStatus
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class PrinterStatusTest {

    @Test
    @DisplayName("Should correctly identify errors")
    void shouldIdentifyErrors() {
        // Given
        PrinterStatus status = new PrinterStatus();
        status.setError(true);

        // When
        boolean hasErrors = status.hasErrors();

        // Then
        assertThat(hasErrors).isTrue();
    }

    @Test
    @DisplayName("Should identify paper empty as error")
    void shouldIdentifyPaperEmptyAsError() {
        // Given
        PrinterStatus status = new PrinterStatus();
        status.setPaperEmpty(true);

        // When
        boolean hasErrors = status.hasErrors();

        // Then
        assertThat(hasErrors).isTrue();
    }

    @Test
    @DisplayName("Should identify offline as error")
    void shouldIdentifyOfflineAsError() {
        // Given
        PrinterStatus status = new PrinterStatus();
        status.setOnline(false);

        // When
        boolean hasErrors = status.hasErrors();

        // Then
        assertThat(hasErrors).isTrue();
    }

    @Test
    @DisplayName("Should correctly identify warnings")
    void shouldIdentifyWarnings() {
        // Given
        PrinterStatus status = new PrinterStatus();
        status.setPaperNearEnd(true);

        // When
        boolean hasWarnings = status.hasWarnings();

        // Then
        assertThat(hasWarnings).isTrue();
    }

    @Test
    @DisplayName("Should identify cover open as warning")
    void shouldIdentifyCoverOpenAsWarning() {
        // Given
        PrinterStatus status = new PrinterStatus();
        status.setCoverOpen(true);

        // When
        boolean hasWarnings = status.hasWarnings();

        // Then
        assertThat(hasWarnings).isTrue();
    }

    @Test
    @DisplayName("Copy constructor should create independent copy")
    void copyConstructorShouldCreateIndependentCopy() {
        // Given
        PrinterStatus original = new PrinterStatus();
        original.setOnline(true);
        original.setError(false);
        original.setPaperEmpty(false);

        // When
        PrinterStatus copy = new PrinterStatus(original);
        copy.setOnline(false); // Modify copy

        // Then
        assertThat(original.isOnline()).isTrue();
        assertThat(copy.isOnline()).isFalse();
    }
}
