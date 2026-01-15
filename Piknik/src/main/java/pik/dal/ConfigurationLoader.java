package pik.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads configuration with priority:
 * 1. System Properties
 * 2. Environment Variables
 * 3. External config/application.properties (next to JAR or via -Dconfig.dir system property)
 * 4. Classpath config/application.properties
 * 5. Classpath application.properties (embedded in JAR)
 *
 * <p>For IDE debugging, specify the external config directory using:</p>
 * <pre>-Dconfig.dir=/path/to/config</pre>
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class ConfigurationLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
    private static final String DEFAULT_CONFIG_FILE = "config/application.properties";

    // External config locations (relative to JAR)
    private static final String[] EXTERNAL_CONFIG_PATHS = {
            "config/application.properties",
            "config/application.properties",
            "../config/application.properties"
    };

    private final Properties properties;

    public ConfigurationLoader() {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigurationLoader(String configFile) {
        this.properties = loadProperties(configFile);
    }

    /**
     * Load properties with the following priority:
     * 1. Absolute file path (if provided)
     * 2. External file (next to JAR or via -Dconfig.dir)
     * 3. Classpath resource (in JAR)
     */
    private Properties loadProperties(String configFile) {
        Properties props = new Properties();

        // Check if configFile is an absolute path to an existing file
        Path configPath = Paths.get(configFile);
        if (configPath.isAbsolute() && Files.exists(configPath) && Files.isRegularFile(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                props.load(input);
                logger.info("Loaded configuration from absolute path: {}", configPath);
                return props;
            } catch (IOException e) {
                logger.warn("Failed to load from absolute path '{}': {}", configPath, e.getMessage());
            }
        }

        // Try to load from external locations first
        Properties externalProps = loadFromExternalLocations();
        if (!externalProps.isEmpty()) {
            props.putAll(externalProps);
            logger.info("Loaded configuration from external file");
        }

        // Try to load from classpath (fallback/defaults)
        Properties classpathProps = loadFromClasspath(configFile);
        if (!classpathProps.isEmpty()) {
            // Add only properties not already loaded from external config
            for (String key : classpathProps.stringPropertyNames()) {
                if (!props.containsKey(key)) {
                    props.put(key, classpathProps.get(key));
                }
            }
            if (externalProps.isEmpty()) {
                logger.info("Loaded configuration from classpath '{}'", configFile);
            } else {
                logger.debug("Loaded default values from classpath '{}'", configFile);
            }
        }

        if (props.isEmpty()) {
            logger.warn("No configuration file found, using built-in defaults only");
        }

        return props;
    }

    /**
     * Try to load properties from external file locations
     */
    private Properties loadFromExternalLocations() {
        Properties props = new Properties();

        // Check if config directory is explicitly specified via system property or environment variable
        String configDir = System.getProperty("config.dir");
        if (configDir == null) {
            configDir = System.getenv("CONFIG_DIR");
        }
        if (configDir != null) {
            Path configPath = Paths.get(configDir, "application.properties").normalize();
            logger.debug("Checking explicit config directory: {}", configPath);

            if (Files.exists(configPath) && Files.isRegularFile(configPath)) {
                try (InputStream input = Files.newInputStream(configPath)) {
                    props.load(input);
                    logger.info("Loaded external configuration from explicit config directory: {}", configPath.toAbsolutePath());
                    return props;
                } catch (IOException e) {
                    logger.warn("Failed to load external config from '{}': {}", configPath, e.getMessage());
                }
            } else {
                logger.warn("Config directory specified but file not found: {}", configPath.toAbsolutePath());
            }
        }

        // Get the directory where the JAR is running from
        String jarDir = getJarDirectory();
        logger.debug("Application running from directory: {}", jarDir);

        // Skip external config loading if running from IDE (target/classes or build/classes)
        // to avoid loading the bundled config as "external"
        if (jarDir.contains("target" + System.getProperty("file.separator") + "classes") ||
            jarDir.contains("build" + System.getProperty("file.separator") + "classes")) {
            logger.debug("Running from IDE build directory, skipping auto-detection. " +
                    "Use -Dconfig.dir=<path> to specify external config location.");
            return props;
        }

        for (String relativePath : EXTERNAL_CONFIG_PATHS) {
            Path configPath = Paths.get(jarDir, relativePath).normalize();

            if (Files.exists(configPath) && Files.isRegularFile(configPath)) {
                try (InputStream input = Files.newInputStream(configPath)) {
                    props.load(input);
                    logger.info("Loaded external configuration from: {}", configPath.toAbsolutePath());
                    return props; // Return first found
                } catch (IOException e) {
                    logger.warn("Failed to load external config from '{}': {}", configPath, e.getMessage());
                }
            } else {
                logger.trace("External config not found at: {}", configPath.toAbsolutePath());
            }
        }

        return props;
    }

    /**
     * Load properties from classpath (embedded in JAR)
     */
    private Properties loadFromClasspath(String configFile) {
        Properties props = new Properties();

        // Try multiple classpath locations
        String[] classpathLocations = {
                configFile,                    // Root of classpath
                "config/" + configFile,        // In config directory
                "/" + configFile              // Absolute from root
        };

        for (String location : classpathLocations) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(location)) {
                if (input != null) {
                    props.load(input);
                    logger.debug("Loaded classpath configuration from '{}'", location);
                    return props;
                }
            } catch (IOException e) {
                logger.debug("Error loading configuration from classpath '{}': {}", location, e.getMessage());
            }
        }

        return props;
    }

    /**
     * Get the directory where the JAR is running from
     */
    private String getJarDirectory() {
        try {
            String jarPath = getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            Path path = Paths.get(jarPath);

            // If it's a JAR file, get its parent directory
            if (jarPath.endsWith(".jar")) {
                return path.getParent().toString();
            }

            // If running from IDE (classes directory), use project root
            return path.toString();
        } catch (Exception e) {
            logger.warn("Could not determine JAR directory: {}", e.getMessage());
            return System.getProperty("user.dir");  // Fallback to working directory
        }
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
            logger.warn("Invalid integer value for property '{}': '{}', using default: {}", key, value, defaultValue);
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

    /**
     * Reload properties from external file
     */
    public void reload() {
        Properties newProps = loadProperties(DEFAULT_CONFIG_FILE);
        properties.clear();
        properties.putAll(newProps);
        logger.info("Configuration reloaded");
    }
}
