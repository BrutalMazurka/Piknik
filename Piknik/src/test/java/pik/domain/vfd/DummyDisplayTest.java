package pik.domain.vfd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for DummyDisplay
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class DummyDisplayTest {

    private DummyDisplay display;

    @BeforeEach
    void setUp() {
        display = new DummyDisplay();
    }

    @Test
    @DisplayName("Should always connect successfully")
    void shouldAlwaysConnectSuccessfully() {
        // When
        boolean connected = display.connect("DUMMY", 9600);

        // Then
        assertThat(connected).isTrue();
        assertThat(display.isConnected()).isTrue();
    }

    @Test
    @DisplayName("Should be identified as dummy")
    void shouldBeIdentifiedAsDummy() {
        // Then
        assertThat(display.isDummy()).isTrue();
    }

    @Test
    @DisplayName("Should handle all operations without errors")
    void shouldHandleAllOperationsWithoutErrors() {
        // Given
        display.connect("DUMMY", 9600);

        // When & Then
        assertThatCode(() -> {
            display.displayText("Test");
            display.clearDisplay();
            display.homeCursor();
            display.setCursorPosition(0, 0);
            display.setBrightness(50);
            display.showCursor(true);
            display.sendCustomCommand("TEST");
            display.runDemo();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should disconnect successfully")
    void shouldDisconnectSuccessfully() {
        // Given
        display.connect("DUMMY", 9600);

        // When
        display.disconnect();

        // Then
        assertThat(display.isConnected()).isFalse();
    }
}
