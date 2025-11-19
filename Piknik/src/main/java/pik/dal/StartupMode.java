package pik.dal;

/**
 * Defines how strictly the application should enforce service initialization
 * @author Martin Sustik <sustik@herman.cz>
 * @since 10/10/2025
 */
public enum StartupMode {
    /**
     * STRICT mode - All services must initialize successfully
     * Application will not start if any service fails
     * Use for: Production environments where all hardware is expected
     */
    STRICT("All services must initialize"),

    /**
     * LENIENT mode - At least one service must initialize successfully
     * Application will start if printer OR VFD initializes
     * Use for: Development or environments where some hardware may be unavailable
     */
    LENIENT("At least one service must initialize"),

    /**
     * PERMISSIVE mode - Application always starts regardless of service status
     * Services can be offline, web server will still start
     * Use for: Testing, debugging, or demonstration mode
     */
    PERMISSIVE("Application starts regardless of service status");

    private final String description;

    StartupMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + ": " + description;
    }
}
