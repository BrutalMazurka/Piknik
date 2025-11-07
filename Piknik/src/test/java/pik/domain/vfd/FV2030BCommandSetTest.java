package pik.domain.vfd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FV2030BCommandSet with 1-based indexing
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class FV2030BCommandSetTest {

    private FV2030BCommandSet commandSet;

    @BeforeEach
    void setUp() {
        commandSet = new FV2030BCommandSet();
    }

    @Test
    @DisplayName("Should provide correct display dimensions")
    void shouldProvideCorrectDimensions() {
        // Then
        assertThat(commandSet.getMaxRows()).isEqualTo(2);
        assertThat(commandSet.getMaxColumns()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should generate clear command")
    void shouldGenerateClearCommand() {
        // When
        byte[] command = commandSet.getClearCommand();

        // Then
        assertThat(command).isNotNull();
        assertThat(command).hasSize(1);
        assertThat(command[0]).isEqualTo((byte) 0x0C);
    }

    @Test
    @DisplayName("Should generate home cursor command")
    void shouldGenerateHomeCursorCommand() {
        // When
        byte[] command = commandSet.getHomeCursorCommand();

        // Then
        assertThat(command).isNotNull();
        assertThat(command).hasSize(3);
        assertThat(command[0]).isEqualTo((byte) 0x1B);
        assertThat(command[1]).isEqualTo((byte) '[');
        assertThat(command[2]).isEqualTo((byte) 'H');
    }

    @Test
    @DisplayName("Should clamp brightness to valid range")
    void shouldClampBrightnessToValidRange() {
        // When
        byte[] tooLow = commandSet.getBrightnessCommand(-10);
        byte[] tooHigh = commandSet.getBrightnessCommand(150);

        // Then
        assertThat(tooLow[2]).isEqualTo((byte) 1);  // Minimum brightness
        assertThat(tooHigh[2]).isEqualTo((byte) 4); // Maximum brightness
    }

    @Test
    @DisplayName("Should convert text to ASCII bytes")
    void shouldConvertTextToASCIIBytes() {
        // When
        byte[] textBytes = commandSet.getTextCommand("Hello");

        // Then
        assertThat(textBytes).isNotNull();
        assertThat(new String(textBytes)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Should generate cursor position command with 1-based indexing")
    void shouldGenerateCursorPositionCommand() {
        // When - Row 1, Column 1 (upper left corner)
        byte[] command = commandSet.getCursorPositionCommand(1, 1);

        // Then
        assertThat(command).isNotNull();
        assertThat(command).hasSize(4);
        assertThat(command[0]).isEqualTo((byte) 0x1B); // ESC
        assertThat(command[1]).isEqualTo((byte) 0x6c); // 'l' command
        assertThat(command[2]).isEqualTo((byte) 1);    // Column 1
        assertThat(command[3]).isEqualTo((byte) 1);    // Row 1
    }

    @Test
    @DisplayName("Should generate cursor position command for row 2")
    void shouldGenerateCursorPositionForRow2() {
        // When - Row 2, Column 10
        byte[] command = commandSet.getCursorPositionCommand(2, 10);

        // Then
        assertThat(command).isNotNull();
        assertThat(command[2]).isEqualTo((byte) 10); // Column 10
        assertThat(command[3]).isEqualTo((byte) 2);  // Row 2
    }

    @Test
    @DisplayName("Should generate cursor position command for last position")
    void shouldGenerateCursorPositionForLastPosition() {
        // When - Row 2, Column 20 (lower right corner)
        byte[] command = commandSet.getCursorPositionCommand(2, 20);

        // Then
        assertThat(command).isNotNull();
        assertThat(command[2]).isEqualTo((byte) 20); // Column 20
        assertThat(command[3]).isEqualTo((byte) 2);  // Row 2
    }

    @Test
    @DisplayName("Should include 1-based indexing in command description")
    void shouldDescribe1BasedIndexing() {
        // When
        String description = commandSet.getCommandDescription();

        // Then
        assertThat(description).contains("1-based");
        assertThat(description).contains("rows 1-2");
        assertThat(description).contains("columns 1-20");
    }
}
