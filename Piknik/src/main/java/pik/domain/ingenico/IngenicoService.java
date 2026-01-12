package pik.domain.ingenico;

import epis5.duk.bck.core.sam.SamDuk;
import epis5.duk.bck.core.sam.SamType;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.IngenicoConfig;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.ingenico.transit.IngenicoTransitApp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for Ingenico Card Reader operations
 * Wraps IngenicoReaderDevice and provides status monitoring
 *
 * Thread-safe: Uses AtomicReference for status updates and CopyOnWriteArrayList for listeners
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public class IngenicoService implements IIngenicoService {
    private static final Logger logger = LoggerFactory.getLogger(IngenicoService.class);

    private final IngenicoConfig config;
    private final IngenicoReaderDevice readerDevice;
    private final List<IIngenicoStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final CompositeDisposable subscriptions = new CompositeDisposable();

    // Thread-safe atomic reference to immutable status
    private final AtomicReference<IngenicoStatus> currentStatus = new AtomicReference<>();
    private volatile boolean initialized = false;

    public IngenicoService(IngenicoConfig config, IngenicoReaderDevice readerDevice) {
        this.config = config;
        this.readerDevice = readerDevice;

        // Initialize with default status
        IngenicoStatus.Builder builder = IngenicoStatus.builder();
        if (config.isDummy()) {
            builder.dummyMode(true);
            logger.info("Ingenico service configured for DUMMY mode");
        }
        this.currentStatus.set(builder.build());
    }

    @Override
    public void initialize() {
        logger.info("Initializing Ingenico reader service with config: {}", config);

        if (config.isDummy()) {
            initializeDummyMode();
            return;
        }

        // NETWORK mode - initialize with real hardware
        logger.info("Initializing Ingenico reader in NETWORK mode (reader IP: {})", config.readerIpAddress());

        try {
            // Subscribe to device events for status updates
            subscribeToDeviceEvents();

            // Initial status update
            updateStatusFromDevice();

            initialized = true;
            logger.info("Ingenico reader service initialized successfully in NETWORK mode");

        } catch (Exception e) {
            // Log the actual error - don't silently fall back to dummy mode
            logger.error("CRITICAL: Ingenico service initialization failed in NETWORK mode", e);
            logger.error("  Reader IP: {}", config.readerIpAddress());
            logger.error("  IFSF Port: {}", config.ifsfTcpServerPort());
            logger.error("  Transit Port: {}", config.transitTcpServerPort());
            logger.error("  Change 'ingenico.connection.type=NONE' in application.properties to use dummy mode");

            // Re-throw the exception instead of silently falling back
            throw new RuntimeException("Failed to initialize Ingenico reader in NETWORK mode. " +
                    "Either fix the connection or set ingenico.connection.type=NONE for dummy mode.", e);
        }
    }

    /**
     * Initialize in dummy mode
     */
    private void initializeDummyMode() {
        IngenicoStatus dummyStatus = IngenicoStatus.builder()
                .dummyMode(true)
                .initialized(true)
                .initState(EReaderInitState.DONE)
                .ifsfConnected(false)
                .ifsfAppAlive(false)
                .transitConnected(false)
                .transitAppAlive(false)
                .error(false)
                .errorMessage("Running in dummy mode - no physical reader")
                .lastUpdate(System.currentTimeMillis())
                .build();

        currentStatus.set(dummyStatus);
        initialized = true;
        notifyStatusChanged("dummy_init");
        logger.info("Ingenico service running in DUMMY mode");
    }

    /**
     * Subscribe to device events for automatic status updates
     * IMPORTANT: Store disposables to prevent memory leak
     */
    private void subscribeToDeviceEvents() {
        // Subscribe to init state changes
        subscriptions.add(readerDevice.getInitStateChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("init_state_changed");
        }));

        // Subscribe to IFSF app events
        IngenicoIfsfApp ifsfApp = readerDevice.getIfsfApp();
        subscriptions.add(ifsfApp.getTcpConnectionChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_connection_changed");
        }));
        subscriptions.add(ifsfApp.getAppAliveChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_app_alive_changed");
        }));
        subscriptions.add(ifsfApp.getTerminalIdChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_terminal_id_changed");
        }));

        // Subscribe to Transit app events
        IngenicoTransitApp transitApp = readerDevice.getTransitApp();
        subscriptions.add(transitApp.getTcpConnectionChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_connection_changed");
        }));
        subscriptions.add(transitApp.getAppAliveChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_app_alive_changed");
        }));
        subscriptions.add(transitApp.getTerminalStatusChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_status_changed");
        }));

        logger.debug("Subscribed to Ingenico device events");
    }

    /**
     * Update current status by reading from device
     * Thread-safe: Creates new immutable status and updates atomically
     */
    private void updateStatusFromDevice() {
        IngenicoIfsfApp ifsfApp = readerDevice.getIfsfApp();
        IngenicoTransitApp transitApp = readerDevice.getTransitApp();
        SamDuk samDuk = readerDevice.getSamDuk();

        // Check if SAM is detected
        boolean samDetected = samDuk.getSamAtr().isDukAtr();

        // Build new immutable status
        IngenicoStatus.Builder builder = IngenicoStatus.builder()
                // Initialization state
                .initState(readerDevice.getInitStatus())
                .initialized(readerDevice.isInitStatusDone())
                // IFSF status
                .ifsfConnected(ifsfApp.isConnected())
                .ifsfAppAlive(ifsfApp.isAppAlive())
                .terminalId(ifsfApp.getTerminalID())
                // Transit status
                .transitConnected(transitApp.isConnected())
                .transitAppAlive(transitApp.isAppAlive())
                .transitTerminalStatusCode(transitApp.getTerminalStatusCode())
                .transitTerminalStatus(transitApp.getTerminalStatus().toString())
                // SAM status
                .samDukDetected(samDetected)
                .samDukStatus(samDetected ? samDuk.getAuth().getProcessState().toString() : null)
                .samNumber(samDetected ? samDuk.getAuditSamNumber() : null)
                .samType(samDetected ? formatSamType(samDuk, readerDevice) : null)
                .networkId(samDetected ? SamDuk.NETWORK_ID : null)  // The required/validated network ID
                .samAtr(samDetected ? samDuk.getAuditATR() : null)
                .slotIndex(samDetected ? samDuk.getSlotIndex() : null)
                .slotStatus(samDetected ? samDuk.getAuditSlotStatus() : null)
                .unlockStatus(samDetected ? samDuk.getAuditUnlockStatus() : null)
                .lastUpdate(System.currentTimeMillis())
                .dummyMode(false);

        IngenicoStatus newStatus = builder.build();

        // Calculate error state
        boolean hasError = !newStatus.isOperational() && newStatus.initialized();
        if (hasError) {
            newStatus = builder
                    .error(true)
                    .errorMessage(buildErrorMessage(newStatus))
                    .build();
        }

        // Atomic update
        currentStatus.set(newStatus);
    }

    /**
     * Build error message based on status
     */
    private String buildErrorMessage(IngenicoStatus status) {
        StringBuilder sb = new StringBuilder();

        if (!status.ifsfConnected()) {
            sb.append("IFSF not connected; ");
        } else if (!status.ifsfAppAlive()) {
            sb.append("IFSF app not alive; ");
        }

        if (!status.transitConnected()) {
            sb.append("Transit not connected; ");
        } else if (!status.transitAppAlive()) {
            sb.append("Transit app not alive; ");
        }

        if (!status.samDukDetected()) {
            sb.append("SAM DUK not detected; ");
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Format SAM type for display showing both expected and found types
     */
    private String formatSamType(SamDuk samDuk, IngenicoReaderDevice readerDevice) {
        String expectedType = samDuk.getSamType() != null ? samDuk.getSamType().toString() : "UNKNOWN";
        SamType found = readerDevice.getFoundSamType();
        String foundType = found != null ? found.toString() : "-";

        logger.debug("formatSamType: expected={}, found={} ({})", expectedType, foundType, found);

        return String.format("expected - %s, found - %s", expectedType, foundType);
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isReady() {
        IngenicoStatus status = currentStatus.get();
        return initialized && (status.dummyMode() || status.isOperational());
    }

    @Override
    public boolean isDummyMode() {
        return currentStatus.get().dummyMode();
    }

    @Override
    public IngenicoStatus getStatus() {
        // No need for defensive copy - status is immutable
        return currentStatus.get();
    }

    @Override
    public String getReaderInfo() {
        IngenicoStatus status = currentStatus.get();

        if (status.dummyMode()) {
            return "Ingenico Card Reader (DUMMY MODE)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Ingenico Card Reader\n");
        sb.append("Init State: ").append(status.initState().getDescription()).append("\n");
        sb.append("IFSF: ").append(status.ifsfConnected() ? "Connected" : "Disconnected");
        sb.append(" (").append(status.ifsfAppAlive() ? "Alive" : "Not alive").append(")\n");
        sb.append("Transit: ").append(status.transitConnected() ? "Connected" : "Disconnected");
        sb.append(" (").append(status.transitAppAlive() ? "Alive" : "Not alive").append(")\n");

        if (status.terminalId() != null && !status.terminalId().isEmpty()) {
            sb.append("Terminal ID: ").append(status.terminalId()).append("\n");
        }

        sb.append("SAM DUK: ").append(status.samDukDetected() ? "Detected" : "Not detected");
        if (status.samDukDetected()) {
            sb.append(" (").append(status.samDukStatus()).append(")");
        }

        return sb.toString();
    }

    @Override
    public void addStatusListener(IIngenicoStatusListener listener) {
        if (listener != null && !statusListeners.contains(listener)) {
            statusListeners.add(listener);
            logger.debug("Added Ingenico status listener: {}", listener.getClass().getSimpleName());
        }
    }

    @Override
    public void removeStatusListener(IIngenicoStatusListener listener) {
        statusListeners.remove(listener);
        logger.debug("Removed Ingenico status listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Notify all status listeners of a status change
     * Thread-safe: Status is immutable and listener list is thread-safe
     */
    private void notifyStatusChanged(String source) {
        if (statusListeners.isEmpty()) {
            return;
        }

        // Get current immutable status - no need for copy
        IngenicoStatus status = currentStatus.get();
        IngenicoStatusEvent event = new IngenicoStatusEvent(status, source);

        for (IIngenicoStatusListener listener : statusListeners) {
            try {
                listener.onStatusChanged(event);
            } catch (Exception e) {
                logger.error("Error notifying Ingenico listener {}: {}",
                    listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        logger.info("Closing Ingenico service");

        // CRITICAL: Dispose all RxJava subscriptions to prevent memory leak
        subscriptions.dispose();

        statusListeners.clear();
        initialized = false;

        logger.info("Ingenico service closed (subscriptions disposed)");
    }
}
