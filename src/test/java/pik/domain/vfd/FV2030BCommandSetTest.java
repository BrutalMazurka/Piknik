package pik.domain.vfd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
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
        assertThat(commandSet.getMaxColumns()).isEqualTo(30);
    }

    @Test
    @DisplayName("Should generate clear command")
    void shouldGenerateClearCommand() {
        // When
        byte[] command = commandSet.getClearCommand();

        // Then
        assertThat(command).isNotNull();
        assertThat(command).hasSize(2);
        assertThat(command[0]).isEqualTo((byte) 0x1B);
        assertThat(command[1]).isEqualTo((byte) 'C');
    }

    @Test
    @DisplayName("Should generate home cursor command")
    void shouldGenerateHomeCursorCommand() {
        // When
        byte[] command = commandSet.getHomeCursorCommand();

        // Then
        assertThat(command).isNotNull();
        assertThat(command).hasSize(2);
        assertThat(command[0]).isEqualTo((byte) 0x1B);
        assertThat(command[1]).isEqualTo((byte) 'H');
    }

    @Test
    @DisplayName("Should clamp brightness to valid range")
    void shouldClampBrightnessToValidRange() {
        // When
        byte[] tooLow = commandSet.getBrightnessCommand(-10);
        byte[] tooHigh = commandSet.getBrightnessCommand(150);

        // Then
        assertThat(tooLow[2]).isEqualTo((byte) 0);
        assertThat(tooHigh[2]).isEqualTo((byte) 100);
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
}
