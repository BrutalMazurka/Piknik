package pik.domain.thprinter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ApiResponse
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class ApiResponseTest {

    @Test
    @DisplayName("Should create success response")
    void shouldCreateSuccessResponse() {
        // When
        ApiResponse<String> response = ApiResponse.success("data");

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("data");
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getTimestamp()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should create success response with custom message")
    void shouldCreateSuccessResponseWithCustomMessage() {
        // When
        ApiResponse<String> response = ApiResponse.success("Custom message", "data");

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Custom message");
        assertThat(response.getData()).isEqualTo("data");
    }

    @Test
    @DisplayName("Should create error response")
    void shouldCreateErrorResponse() {
        // When
        ApiResponse<String> response = ApiResponse.error("Error occurred");

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Error occurred");
        assertThat(response.getData()).isNull();
    }
}
