package pik.domain;

import pik.dal.StartupMode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when application fails to start according to StartupMode requirements
 * @author Martin Sustik <sustik@herman.cz>
 * @since 10/10/2025
 */
public class StartupException extends RuntimeException {
    private final StartupMode mode;
    private final List<ServiceInitializationResult> results;

    public StartupException(String message, StartupMode mode, List<ServiceInitializationResult> results) {
        super(message + "\n" + formatResults(results));
        this.mode = mode;
        this.results = results;
    }

    public StartupMode getMode() {
        return mode;
    }

    public List<ServiceInitializationResult> getResults() {
        return results;
    }

    public List<ServiceInitializationResult> getFailedServices() {
        return results.stream()
                .filter(r -> !r.isSuccess())
                .collect(Collectors.toList());
    }

    public List<ServiceInitializationResult> getSuccessfulServices() {
        return results.stream()
                .filter(ServiceInitializationResult::isSuccess)
                .collect(Collectors.toList());
    }

    private static String formatResults(List<ServiceInitializationResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nService Initialization Results:");
        for (ServiceInitializationResult result : results) {
            sb.append("\n  - ").append(result);
        }
        return sb.toString();
    }

}
