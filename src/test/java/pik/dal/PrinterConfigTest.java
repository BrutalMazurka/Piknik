package pik.dal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pik.common.EPrinterType;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PrinterConfig with connection type support
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
class PrinterConfigTest {

    @Test
    @DisplayName("Should validate USB printer configuration successfully")
    void shouldValidateUSBConfigSuccessfully() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 9600, 10000);

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.USB);
        assertThat(config.comPort()).isEqualTo("COM14");
        assertThat(config.baudRate()).isEqualTo(9600);
    }

    @Test
    @DisplayName("Should validate network printer configuration successfully")
    void shouldValidateNetworkConfigSuccessfully() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 10000);

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NETWORK);
        assertThat(config.ipAddress()).isEqualTo("10.0.0.150");
        assertThat(config.networkPort()).isEqualTo(9100);
    }

    @Test
    @DisplayName("Should validate dummy printer configuration successfully")
    void shouldValidateDummyConfigSuccessfully() {
        // Given
        PrinterConfig config = new PrinterConfig(
                "TM-T20III",
                null,
                0,
                10000,
                null,
                0,
                EPrinterType.NONE
        );

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.isDummy()).isTrue();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NONE);
    }

    @Test
    @DisplayName("Should reject empty printer name")
    void shouldRejectEmptyName() {
        // Given
        PrinterConfig config = new PrinterConfig("", "COM14", 9600, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer name cannot be empty");
    }

    @Test
    @DisplayName("Should reject invalid network port")
    void shouldRejectInvalidNetworkPort() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 0, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Network port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should reject empty COM port for USB connection")
    void shouldRejectEmptyComPort() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "", 9600, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("COM port cannot be empty");
    }

    @Test
    @DisplayName("Should reject invalid baud rate")
    void shouldRejectInvalidBaudRate() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 200, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Baud rate must be between 300 and 115200");
    }

    @Test
    @DisplayName("Should reject invalid timeout")
    void shouldRejectInvalidTimeout() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 9600, 500);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Connection timeout must be at least 1000ms");
    }

    @Test
    @DisplayName("Should generate correct USB connection string")
    void shouldGenerateUSBConnectionString() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 9600, 10000);

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Serial;PORT=COM14;BAUD=9600;TIMEOUT=10000");
    }

    @Test
    @DisplayName("Should generate correct network connection string")
    void shouldGenerateNetworkConnectionString() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Ethernet;IP=10.0.0.150;PORT=9100;TIMEOUT=10000");
    }

    @Test
    @DisplayName("Should generate correct dummy connection string")
    void shouldGenerateDummyConnectionString() {
        // Given
        PrinterConfig config = new PrinterConfig(
                "TM-T20III",
                null,
                0,
                10000,
                null,
                0,
                EPrinterType.NONE
        );

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Dummy");
    }

    @Test
    @DisplayName("Should generate correct logical name")
    void shouldGenerateLogicalName() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 9600, 10000);

        // When
        String logicalName = config.getLogicalName();

        // Then
        assertThat(logicalName).isEqualTo("TM_T20III");
    }

    @Test
    @DisplayName("Should correctly identify dummy mode")
    void shouldIdentifyDummyMode() {
        // Given
        PrinterConfig dummyConfig = new PrinterConfig(
                "TM-T20III",
                null,
                0,
                10000,
                null,
                0,
                EPrinterType.NONE
        );
        PrinterConfig usbConfig = new PrinterConfig("TM-T20III", "COM14", 9600, 10000);

        // When & Then
        assertThat(dummyConfig.isDummy()).isTrue();
        assertThat(usbConfig.isDummy()).isFalse();
    }

    @Test
    @DisplayName("Should display correct toString for USB connection")
    void shouldDisplayCorrectToStringForUSB() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "COM14", 9600, 10000);

        // When
        String toString = config.toString();

        // Then
        assertThat(toString).contains("type=USB");
        assertThat(toString).contains("comPort='COM14'");
        assertThat(toString).contains("baud=9600");
    }

    @Test
    @DisplayName("Should display correct toString for network connection")
    void shouldDisplayCorrectToStringForNetwork() {
        // Given
        PrinterConfig config = new PrinterConfig("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String toString = config.toString();

        // Then
        assertThat(toString).contains("type=NETWORK");
        assertThat(toString).contains("ip='10.0.0.150'");
        assertThat(toString).contains("port=9100");
    }
}
