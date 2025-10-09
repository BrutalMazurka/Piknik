package pik.dal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PrinterConfig
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class PrinterConfigTest {

    @Test
    @DisplayName("Should validate printer configuration successfully")
    void shouldValidateSuccessfully() {
        // Given
        PrinterConfig config = new PrinterConfig(
                "TM-T20III",
                "10.0.0.150",
                9100,
                10000
        );

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject empty printer name")
    void shouldRejectEmptyName() {
        // Given
        PrinterConfig config = new PrinterConfig("", "10.0.0.150", 9100, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer name cannot be empty");
    }

    @Test
    @DisplayName("Should reject invalid port")
    void shouldRejectInvalidPort() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 0, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should reject invalid timeout")
    void shouldRejectInvalidTimeout() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 500);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Connection timeout must be at least 1000ms");
    }

    @Test
    @DisplayName("Should generate correct connection string")
    void shouldGenerateConnectionString() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Ethernet;IP=10.0.0.150;PORT=9100;TIMEOUT=10000");
    }

    @Test
    @DisplayName("Should generate correct logical name")
    void shouldGenerateLogicalName() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String logicalName = config.getLogicalName();

        // Then
        assertThat(logicalName).isEqualTo("EpsonPrinter_TM_T20III");
    }
}
