package pik.domain.ingenico;

/**
 * Service interface for Ingenico Card Reader operations
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public interface IIngenicoService {
    /**
     * Initialize the Ingenico reader service
     */
    void initialize();

    /**
     * Check if service is initialized
     */
    boolean isInitialized();

    /**
     * Check if reader is ready for operations
     * (fully initialized and operational)
     */
    boolean isReady();

    /**
     * Check if running in dummy mode
     */
    boolean isDummyMode();

    /**
     * Get current reader status
     */
    IngenicoStatus getStatus();

    /**
     * Get reader information (version, capabilities, etc.)
     */
    String getReaderInfo();

    /**
     * Add status change listener
     */
    void addStatusListener(IIngenicoStatusListener listener);

    /**
     * Remove status change listener
     */
    void removeStatusListener(IIngenicoStatusListener listener);

    /**
     * Close and cleanup resources
     */
    void close();
}
