package pik.domain;

/**
 * Result of service initialization attempt
 * @author Martin Sustik <sustik@herman.cz>
 * @since 10/10/2025
 */
public class ServiceInitializationResult {
    private final String serviceName;
    private final boolean success;
    private final String errorMessage;
    private final Exception exception;
    private final long initializationTimeMs;

    private ServiceInitializationResult(String serviceName, boolean success,
                                        String errorMessage, Exception exception,
                                        long initializationTimeMs) {
        this.serviceName = serviceName;
        this.success = success;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.initializationTimeMs = initializationTimeMs;
    }

    /**
     * Create a successful initialization result
     */
    public static ServiceInitializationResult success(String serviceName, long initTimeMs) {
        return new ServiceInitializationResult(serviceName, true, null, null, initTimeMs);
    }

    /**
     * Create a failed initialization result
     */
    public static ServiceInitializationResult failure(String serviceName, Exception exception, long initTimeMs) {
        return new ServiceInitializationResult(
                serviceName,
                false,
                exception.getMessage(),
                exception,
                initTimeMs
        );
    }

    /**
     * Create a failed initialization result with custom message
     */
    public static ServiceInitializationResult failure(String serviceName, String errorMessage, long initTimeMs) {
        return new ServiceInitializationResult(serviceName, false, errorMessage, null, initTimeMs);
    }

    // Getters
    public String getServiceName() {
        return serviceName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Exception getException() {
        return exception;
    }

    public long getInitializationTimeMs() {
        return initializationTimeMs;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("%s: SUCCESS (initialized in %dms)",
                    serviceName, initializationTimeMs);
        } else {
            return String.format("%s: FAILED (attempted for %dms) - %s",
                    serviceName, initializationTimeMs, errorMessage);
        }
    }

}
