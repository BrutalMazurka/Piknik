package pik.dal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ConfigurationLoader
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
class ConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("printer.name");
        System.clearProperty("printer.ip");
    }

    @Test
    @DisplayName("Should load configuration from properties file")
    void shouldLoadFromPropertiesFile() throws IOException {
        // Given
        Path configFile = tempDir.resolve("test.properties");
        Properties props = new Properties();
        props.setProperty("printer.name", "TestPrinter");
        props.setProperty("printer.ip", "192.168.1.100");

        try (var writer = Files.newBufferedWriter(configFile)) {
            props.store(writer, "Test config");
        }

        ConfigurationLoader loader = new ConfigurationLoader(configFile.toString());

        // When
        String printerName = loader.getString("printer.name", "default");
        String printerIp = loader.getString("printer.ip", "default");

        // Then
        assertThat(printerName).isEqualTo("TestPrinter");
        assertThat(printerIp).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("Should prioritize system properties over file")
    void shouldPrioritizeSystemProperties() {
        // Given
        System.setProperty("printer.name", "SystemPrinter");
        ConfigurationLoader loader = new ConfigurationLoader();

        // When
        String printerName = loader.getString("printer.name", "default");

        // Then
        assertThat(printerName).isEqualTo("SystemPrinter");
    }

    @Test
    @DisplayName("Should use default value when property not found")
    void shouldUseDefaultValue() {
        // Given
        ConfigurationLoader loader = new ConfigurationLoader();

        // When
        String value = loader.getString("nonexistent.property", "defaultValue");

        // Then
        assertThat(value).isEqualTo("defaultValue");
    }

    @Test
    @DisplayName("Should parse integer values correctly")
    void shouldParseIntegerValues() {
        // Given
        System.setProperty("printer.port", "9100");
        ConfigurationLoader loader = new ConfigurationLoader();

        // When
        int port = loader.getInt("printer.port", 8080);

        // Then
        assertThat(port).isEqualTo(9100);
    }

    @Test
    @DisplayName("Should return default for invalid integer")
    void shouldReturnDefaultForInvalidInteger() {
        // Given
        System.setProperty("printer.port", "invalid");
        ConfigurationLoader loader = new ConfigurationLoader();

        // When
        int port = loader.getInt("printer.port", 8080);

        // Then
        assertThat(port).isEqualTo(8080);
    }

    @Test
    @DisplayName("Should parse boolean values correctly")
    void shouldParseBooleanValues() {
        // Given
        System.setProperty("monitor.enabled", "true");
        ConfigurationLoader loader = new ConfigurationLoader();

        // When
        boolean enabled = loader.getBoolean("monitor.enabled", false);

        // Then
        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("Should throw exception for required missing property")
    void shouldThrowExceptionForRequiredMissingProperty() {
        // Given
        ConfigurationLoader loader = new ConfigurationLoader();

        // When & Then
        assertThatThrownBy(() -> loader.getRequiredString("missing.property"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Required property 'missing.property' is not configured");
    }

}
