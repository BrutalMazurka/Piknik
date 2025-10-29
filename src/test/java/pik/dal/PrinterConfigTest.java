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
    @DisplayName("Should create and validate USB printer configuration successfully")
    void shouldValidateUSBConfigSuccessfully() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.USB);
        assertThat(config.name()).isEqualTo("TM-T20III");
        assertThat(config.comPort()).isEqualTo("COM14");
        assertThat(config.baudRate()).isEqualTo(9600);
        assertThat(config.connectionTimeout()).isEqualTo(10000);
        assertThat(config.ipAddress()).isNull();
        assertThat(config.networkPort()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create and validate network printer configuration successfully")
    void shouldValidateNetworkConfigSuccessfully() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 10000);

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NETWORK);
        assertThat(config.name()).isEqualTo("TM-T20III");
        assertThat(config.ipAddress()).isEqualTo("10.0.0.150");
        assertThat(config.networkPort()).isEqualTo(9100);
        assertThat(config.connectionTimeout()).isEqualTo(10000);
        assertThat(config.comPort()).isNull();
        assertThat(config.baudRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create and validate dummy printer configuration successfully")
    void shouldValidateDummyConfigSuccessfully() {
        // Given
        PrinterConfig config = PrinterConfig.dummy("TM-T20III");

        // When & Then
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
        assertThat(config.isDummy()).isTrue();
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NONE);
        assertThat(config.name()).isEqualTo("TM-T20III");
        assertThat(config.connectionTimeout()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Should reject empty printer name for USB")
    void shouldRejectEmptyNameForUSB() {
        // Given
        PrinterConfig config = PrinterConfig.usb("", "COM14", 9600, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer name cannot be empty");
    }

    @Test
    @DisplayName("Should reject empty printer name for Network")
    void shouldRejectEmptyNameForNetwork() {
        // Given
        PrinterConfig config = PrinterConfig.network("", "10.0.0.150", 9100, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer name cannot be empty");
    }

    @Test
    @DisplayName("Should reject invalid network port - too low")
    void shouldRejectInvalidNetworkPortTooLow() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 0, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Network port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should reject invalid network port - too high")
    void shouldRejectInvalidNetworkPortTooHigh() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 99999, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Network port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should reject empty IP address for network connection")
    void shouldRejectEmptyIPAddress() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "", 9100, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer IP address cannot be empty");
    }

    @Test
    @DisplayName("Should reject null IP address for network connection")
    void shouldRejectNullIPAddress() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", null, 9100, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Printer IP address cannot be empty");
    }

    @Test
    @DisplayName("Should reject empty COM port for USB connection")
    void shouldRejectEmptyComPort() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "", 9600, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("COM port cannot be empty");
    }

    @Test
    @DisplayName("Should reject null COM port for USB connection")
    void shouldRejectNullComPort() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", null, 9600, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("COM port cannot be empty");
    }

    @Test
    @DisplayName("Should reject invalid baud rate - too low")
    void shouldRejectInvalidBaudRateTooLow() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 200, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Baud rate must be between 300 and 115200");
    }

    @Test
    @DisplayName("Should reject invalid baud rate - too high")
    void shouldRejectInvalidBaudRateTooHigh() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 200000, 10000);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Baud rate must be between 300 and 115200");
    }

    @Test
    @DisplayName("Should accept valid baud rates")
    void shouldAcceptValidBaudRates() {
        // Given - common baud rates
        int[] validBaudRates = {300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200};

        for (int baudRate : validBaudRates) {
            PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", baudRate, 10000);

            // When & Then
            assertThatCode(() -> config.validate())
                    .as("Baud rate %d should be valid", baudRate)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should reject invalid timeout for USB")
    void shouldRejectInvalidTimeoutForUSB() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 500);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Connection timeout must be at least 1000ms");
    }

    @Test
    @DisplayName("Should reject invalid timeout for Network")
    void shouldRejectInvalidTimeoutForNetwork() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 999);

        // When & Then
        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Connection timeout must be at least 1000ms");
    }

    @Test
    @DisplayName("Dummy mode should not validate timeout")
    void dummyModeShouldNotValidateTimeout() {
        // Given
        PrinterConfig config = PrinterConfig.dummy("TM-T20III");

        // When & Then - should not throw even though timeout might be less than required
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should generate correct USB connection string")
    void shouldGenerateUSBConnectionString() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Serial;PORT=COM14;BAUD=9600;TIMEOUT=10000");
    }

    @Test
    @DisplayName("Should generate correct network connection string")
    void shouldGenerateNetworkConnectionString() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Ethernet;IP=10.0.0.150;PORT=9100;TIMEOUT=10000");
    }

    @Test
    @DisplayName("Should generate correct dummy connection string")
    void shouldGenerateDummyConnectionString() {
        // Given
        PrinterConfig config = PrinterConfig.dummy("TM-T20III");

        // When
        String connectionString = config.getConnectionString();

        // Then
        assertThat(connectionString).isEqualTo("TYPE=Dummy");
    }

    @Test
    @DisplayName("Should generate correct logical name")
    void shouldGenerateLogicalName() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);

        // When
        String logicalName = config.getLogicalName();

        // Then
        assertThat(logicalName).isEqualTo("TM_T20III");
    }

    @Test
    @DisplayName("Should generate logical name with special characters replaced")
    void shouldGenerateLogicalNameWithSpecialCharactersReplaced() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III (Main)", "COM14", 9600, 10000);

        // When
        String logicalName = config.getLogicalName();

        // Then
        assertThat(logicalName).isEqualTo("TM_T20III__Main_");
        assertThat(logicalName).doesNotContain("-", "(", ")", " ");
    }

    @Test
    @DisplayName("Should correctly identify dummy mode")
    void shouldIdentifyDummyMode() {
        // Given
        PrinterConfig dummyConfig = PrinterConfig.dummy("TM-T20III");
        PrinterConfig usbConfig = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);
        PrinterConfig networkConfig = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 10000);

        // When & Then
        assertThat(dummyConfig.isDummy()).isTrue();
        assertThat(usbConfig.isDummy()).isFalse();
        assertThat(networkConfig.isDummy()).isFalse();
    }

    @Test
    @DisplayName("Should display correct toString for USB connection")
    void shouldDisplayCorrectToStringForUSB() {
        // Given
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);

        // When
        String toString = config.toString();

        // Then
        assertThat(toString).contains("type=USB");
        assertThat(toString).contains("name='TM-T20III'");
        assertThat(toString).contains("comPort='COM14'");
        assertThat(toString).contains("baud=9600");
        assertThat(toString).contains("timeout=10000");
    }

    @Test
    @DisplayName("Should display correct toString for network connection")
    void shouldDisplayCorrectToStringForNetwork() {
        // Given
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 10000);

        // When
        String toString = config.toString();

        // Then
        assertThat(toString).contains("type=NETWORK");
        assertThat(toString).contains("name='TM-T20III'");
        assertThat(toString).contains("ip='10.0.0.150'");
        assertThat(toString).contains("port=9100");
        assertThat(toString).contains("timeout=10000");
    }

    @Test
    @DisplayName("Should display correct toString for dummy connection")
    void shouldDisplayCorrectToStringForDummy() {
        // Given
        PrinterConfig config = PrinterConfig.dummy("TM-T20III");

        // When
        String toString = config.toString();

        // Then
        assertThat(toString).contains("type=NONE");
        assertThat(toString).contains("Dummy");
        assertThat(toString).contains("name='TM-T20III'");
    }

    @Test
    @DisplayName("Should accept valid network ports at boundaries")
    void shouldAcceptValidNetworkPortsAtBoundaries() {
        // Given - boundary values
        PrinterConfig minPort = PrinterConfig.network("TM-T20III", "10.0.0.150", 1, 10000);
        PrinterConfig maxPort = PrinterConfig.network("TM-T20III", "10.0.0.150", 65535, 10000);

        // When & Then
        assertThatCode(() -> minPort.validate()).doesNotThrowAnyException();
        assertThatCode(() -> maxPort.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should use USB type for factory method")
    void shouldUseUSBTypeForFactoryMethod() {
        // When
        PrinterConfig config = PrinterConfig.usb("TM-T20III", "COM14", 9600, 10000);

        // Then
        assertThat(config.connectionType()).isEqualTo(EPrinterType.USB);
    }

    @Test
    @DisplayName("Should use NETWORK type for factory method")
    void shouldUseNetworkTypeForFactoryMethod() {
        // When
        PrinterConfig config = PrinterConfig.network("TM-T20III", "10.0.0.150", 9100, 10000);

        // Then
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NETWORK);
    }

    @Test
    @DisplayName("Should use NONE type for dummy factory method")
    void shouldUseNoneTypeForDummyFactoryMethod() {
        // When
        PrinterConfig config = PrinterConfig.dummy("TM-T20III");

        // Then
        assertThat(config.connectionType()).isEqualTo(EPrinterType.NONE);
    }
}
