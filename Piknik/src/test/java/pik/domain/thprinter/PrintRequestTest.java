package pik.domain.thprinter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PrintRequest validation
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class PrintRequestTest {

    @Test
    @DisplayName("Should create print request with default values")
    void shouldCreateWithDefaults() {
        // Given & When
        PrintRequest request = new PrintRequest();

        // Then
        assertThat(request.isCutPaper()).isTrue();
        assertThat(request.getCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should set and get text correctly")
    void shouldSetAndGetText() {
        // Given
        PrintRequest request = new PrintRequest();
        String text = "Test receipt";

        // When
        request.setText(text);

        // Then
        assertThat(request.getText()).isEqualTo(text);
    }

    @Test
    @DisplayName("Should handle multiple copies")
    void shouldHandleMultipleCopies() {
        // Given
        PrintRequest request = new PrintRequest();

        // When
        request.setCopies(3);

        // Then
        assertThat(request.getCopies()).isEqualTo(3);
    }
}
