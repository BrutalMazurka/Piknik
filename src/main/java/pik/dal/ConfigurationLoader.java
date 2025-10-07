package pik.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads configuration from application.properties with support for overrides
 * Priority: System Properties > Environment Variables > application.properties
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class ConfigurationLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
    private static final String DEFAULT_CONFIG_FILE = "application.properties";

    private final Properties properties;

    public ConfigurationLoader() {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigurationLoader(String configFile) {
        this.properties = loadProperties(configFile);
    }

    /**
     * Load properties file from classpath
     */
    private Properties loadProperties(String configFile) {
        Properties props = new Properties();

        // Try to load from classpath
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                logger.warn("Configuration file '{}' not found on classpath, using defaults", configFile);
                return props;
            }

            props.load(input);
            logger.info("Loaded configuration from '{}'", configFile);

        } catch (IOException e) {
            logger.error("Error loading configuration file '{}'", configFile, e);
        }

        return props;
    }

    /**
     * Get string property with priority: System Property > Env Var > Properties File > Default
     */
    public String getString(String key, String defaultValue) {
        // 1. Check system properties
        String value = System.getProperty(key);
        if (value != null) {
            logger.debug("Property '{}' from System Properties: {}", key, value);
            return value;
        }

        // 2. Check environment variables (convert dots to underscores, uppercase)
        String envKey = key.replace('.', '_').toUpperCase();
        value = System.getenv(envKey);
        if (value != null) {
            logger.debug("Property '{}' from Environment Variable '{}': {}", key, envKey, value);
            return value;
        }

        // 3. Check properties file
        value = properties.getProperty(key);
        if (value != null) {
            logger.debug("Property '{}' from configuration file: {}", key, value);
            return value;
        }

        // 4. Return default
        logger.debug("Property '{}' not found, using default: {}", key, defaultValue);
        return defaultValue;
    }

    /**
     * Get integer property
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property '{}': '{}', using default: {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get boolean property
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Get required string property (throws exception if missing)
     */
    public String getRequiredString(String key) throws ConfigurationException {
        String value = getString(key, null);
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException("Required property '" + key + "' is not configured");
        }
        return value;
    }

    /**
     * Get all properties
     */
    public Properties getAllProperties() {
        return new Properties(properties);
    }
}
