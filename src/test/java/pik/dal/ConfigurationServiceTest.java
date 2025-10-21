package pik.dal;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pik.common.EDisplayType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ConfigurationService
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
class ConfigurationServiceTest {

    @Test
    @DisplayName("Should load valid printer configuration")
    void shouldLoadValidPrinterConfiguration() throws ConfigurationException {
        // Given
        System.setProperty("printer.name", "TM-T20III");
        System.setProperty("printer.ip", "10.0.0.150");
        System.setProperty("printer.port", "9100");
        System.setProperty("printer.connection.timeout", "10000");

        // When
        ConfigurationService service = new ConfigurationService();
        PrinterConfig config = service.getPrinterConfiguration();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.name()).isEqualTo("TM-T20III");
        assertThat(config.ipAddress()).isEqualTo("10.0.0.150");
        assertThat(config.port()).isEqualTo(9100);
        assertThat(config.connectionTimeout()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Should throw exception for invalid printer port")
    void shouldThrowExceptionForInvalidPrinterPort() {
        // Given
        System.setProperty("printer.name", "TM-T20III");
        System.setProperty("printer.ip", "10.0.0.150");
        System.setProperty("printer.port", "99999"); // Invalid port
        System.setProperty("printer.connection.timeout", "10000");

        // When & Then
        assertThatThrownBy(() -> new ConfigurationService())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should load valid VFD configuration")
    void shouldLoadValidVFDConfiguration() throws ConfigurationException {
        // Given
        System.setProperty("vfd.type", "FV_2030B");
        System.setProperty("vfd.port", "COM3");
        System.setProperty("vfd.baud", "9600");

        // When
        ConfigurationService service = new ConfigurationService();
        VFDConfig config = service.getVFDConfiguration();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.displayType()).isEqualTo(EDisplayType.FV_2030B);
        assertThat(config.portName()).isEqualTo("COM3");
        assertThat(config.baudRate()).isEqualTo(9600);
    }

    @Test
    @DisplayName("Should load valid server configuration")
    void shouldLoadValidServerConfiguration() throws ConfigurationException {
        // Given
        System.setProperty("server.port", "8080");
        System.setProperty("server.host", "0.0.0.0");
        System.setProperty("monitor.status.interval", "5000");
        System.setProperty("monitor.enabled", "true");
        System.setProperty("server.thread.pool", "3");

        // When
        ConfigurationService service = new ConfigurationService();
        ServerConfig config = service.getServerConfiguration();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.port()).isEqualTo(8080);
        assertThat(config.host()).isEqualTo("0.0.0.0");
        assertThat(config.statusCheckInterval()).isEqualTo(5000);
        assertThat(config.monitoringEnabled()).isTrue();
        assertThat(config.threadPoolSize()).isEqualTo(3);
    }

    @AfterEach
    void cleanup() {
        System.clearProperty("printer.name");
        System.clearProperty("printer.ip");
        System.clearProperty("printer.port");
        System.clearProperty("printer.connection.timeout");
        System.clearProperty("vfd.type");
        System.clearProperty("vfd.port");
        System.clearProperty("vfd.baud");
        System.clearProperty("server.port");
        System.clearProperty("server.host");
        System.clearProperty("monitor.status.interval");
        System.clearProperty("monitor.enabled");
        System.clearProperty("server.thread.pool");
    }
}
