package pik.dal;

/**
 * Exception thrown when configuration is invalid or missing
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class ConfigurationException extends Exception {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
