package pik.domain.ingenico;

import epis5.duk.bck.core.sam.SamDuk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.dal.IngenicoConfig;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.ingenico.transit.IngenicoTransitApp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for Ingenico Card Reader operations
 * Wraps IngenicoReaderDevice and provides status monitoring
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public class IngenicoService implements IIngenicoService {
    private static final Logger logger = LoggerFactory.getLogger(IngenicoService.class);

    private final IngenicoConfig config;
    private final IngenicoReaderDevice readerDevice;
    private final ReentrantLock serviceLock = new ReentrantLock();
    private final List<IIngenicoStatusListener> statusListeners = new CopyOnWriteArrayList<>();

    private IngenicoStatus currentStatus;
    private volatile boolean initialized = false;

    public IngenicoService(IngenicoConfig config, IngenicoReaderDevice readerDevice) {
        this.config = config;
        this.readerDevice = readerDevice;
        this.currentStatus = new IngenicoStatus();

        // Set dummy mode from config
        if (config.isDummy()) {
            this.currentStatus.setDummyMode(true);
            logger.info("Ingenico service configured for DUMMY mode");
        }
    }

    @Override
    public void initialize() {
        serviceLock.lock();
        try {
            logger.info("Initializing Ingenico reader service with config: {}", config);

            if (config.isDummy()) {
                initializeDummyMode();
                return;
            }

            // NETWORK mode - initialize with real hardware
            logger.info("Initializing Ingenico reader in NETWORK mode (reader IP: {})", config.readerIpAddress());

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
            logger.error("  Change 'ingenico.connection.type=NONE' in app.properties to use dummy mode");

            // Re-throw the exception instead of silently falling back
            throw new RuntimeException("Failed to initialize Ingenico reader in NETWORK mode. " +
                    "Either fix the connection or set ingenico.connection.type=NONE for dummy mode.", e);
        } finally {
            serviceLock.unlock();
        }
    }

    /**
     * Initialize in dummy mode
     */
    private void initializeDummyMode() {
        currentStatus.setDummyMode(true);
        currentStatus.setInitialized(true);
        currentStatus.setInitState(EReaderInitState.DONE);
        currentStatus.setIfsfConnected(false);
        currentStatus.setIfsfAppAlive(false);
        currentStatus.setTransitConnected(false);
        currentStatus.setTransitAppAlive(false);
        currentStatus.setError(false);
        currentStatus.setErrorMessage("Running in dummy mode - no physical reader");
        currentStatus.setLastUpdate(System.currentTimeMillis());

        initialized = true;
        notifyStatusChanged("dummy_init");
        logger.info("Ingenico service running in DUMMY mode");
    }

    /**
     * Subscribe to device events for automatic status updates
     */
    private void subscribeToDeviceEvents() {
        // Subscribe to init state changes
        readerDevice.getInitStateChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("init_state_changed");
        });

        // Subscribe to IFSF app events
        IngenicoIfsfApp ifsfApp = readerDevice.getIfsfApp();
        ifsfApp.getTcpConnectionChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_connection_changed");
        });
        ifsfApp.getAppAliveChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_app_alive_changed");
        });
        ifsfApp.getTerminalIdChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("ifsf_terminal_id_changed");
        });

        // Subscribe to Transit app events
        IngenicoTransitApp transitApp = readerDevice.getTransitApp();
        transitApp.getTcpConnectionChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_connection_changed");
        });
        transitApp.getAppAliveChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_app_alive_changed");
        });
        transitApp.getTerminalStatusChanges().subscribe(ea -> {
            updateStatusFromDevice();
            notifyStatusChanged("transit_status_changed");
        });

        logger.debug("Subscribed to Ingenico device events");
    }

    /**
     * Update current status by reading from device
     */
    private void updateStatusFromDevice() {
        serviceLock.lock();
        try {
            IngenicoIfsfApp ifsfApp = readerDevice.getIfsfApp();
            IngenicoTransitApp transitApp = readerDevice.getTransitApp();
            SamDuk samDuk = readerDevice.getSamDuk();

            // Initialization state
            currentStatus.setInitState(readerDevice.getInitStatus());
            currentStatus.setInitialized(readerDevice.isInitStatusDone());

            // IFSF status
            currentStatus.setIfsfConnected(ifsfApp.isConnected());
            currentStatus.setIfsfAppAlive(ifsfApp.isAppAlive());
            currentStatus.setTerminalId(ifsfApp.getTerminalID());

            // Transit status
            currentStatus.setTransitConnected(transitApp.isConnected());
            currentStatus.setTransitAppAlive(transitApp.isAppAlive());
            currentStatus.setTransitTerminalStatusCode(transitApp.getTerminalStatusCode());
            currentStatus.setTransitTerminalStatus(transitApp.getTerminalStatus().toString());

            // SAM status
            currentStatus.setSamDukDetected(samDuk.getSamAtr().isDukAtr());
            currentStatus.setSamDukStatus(samDuk.getAuth().getProcessState().toString());

            // Error state
            boolean hasError = !currentStatus.isOperational() && currentStatus.isInitialized();
            currentStatus.setError(hasError);
            if (hasError) {
                currentStatus.setErrorMessage(buildErrorMessage());
            } else {
                currentStatus.setErrorMessage(null);
            }

            currentStatus.setLastUpdate(System.currentTimeMillis());

        } finally {
            serviceLock.unlock();
        }
    }

    /**
     * Build error message based on current state
     */
    private String buildErrorMessage() {
        StringBuilder sb = new StringBuilder();

        if (!currentStatus.isIfsfConnected()) {
            sb.append("IFSF not connected; ");
        } else if (!currentStatus.isIfsfAppAlive()) {
            sb.append("IFSF app not alive; ");
        }

        if (!currentStatus.isTransitConnected()) {
            sb.append("Transit not connected; ");
        } else if (!currentStatus.isTransitAppAlive()) {
            sb.append("Transit app not alive; ");
        }

        if (!currentStatus.isSamDukDetected()) {
            sb.append("SAM DUK not detected; ");
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isReady() {
        return initialized && (currentStatus.isDummyMode() || currentStatus.isOperational());
    }

    @Override
    public boolean isDummyMode() {
        return currentStatus.isDummyMode();
    }

    @Override
    public IngenicoStatus getStatus() {
        // Return a copy to prevent external modification
        return new IngenicoStatus(currentStatus);
    }

    @Override
    public String getReaderInfo() {
        if (currentStatus.isDummyMode()) {
            return "Ingenico Card Reader (DUMMY MODE)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Ingenico Card Reader\n");
        sb.append("Init State: ").append(currentStatus.getInitState().getDescription()).append("\n");
        sb.append("IFSF: ").append(currentStatus.isIfsfConnected() ? "Connected" : "Disconnected");
        sb.append(" (").append(currentStatus.isIfsfAppAlive() ? "Alive" : "Not alive").append(")\n");
        sb.append("Transit: ").append(currentStatus.isTransitConnected() ? "Connected" : "Disconnected");
        sb.append(" (").append(currentStatus.isTransitAppAlive() ? "Alive" : "Not alive").append(")\n");

        if (currentStatus.getTerminalId() != null && !currentStatus.getTerminalId().isEmpty()) {
            sb.append("Terminal ID: ").append(currentStatus.getTerminalId()).append("\n");
        }

        sb.append("SAM DUK: ").append(currentStatus.isSamDukDetected() ? "Detected" : "Not detected");
        if (currentStatus.isSamDukDetected()) {
            sb.append(" (").append(currentStatus.getSamDukStatus()).append(")");
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
     */
    private void notifyStatusChanged(String source) {
        if (statusListeners.isEmpty()) {
            return;
        }

        IngenicoStatusEvent event = new IngenicoStatusEvent(new IngenicoStatus(currentStatus), source);

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
        serviceLock.lock();
        try {
            logger.info("Closing Ingenico service");
            statusListeners.clear();
            initialized = false;
        } finally {
            serviceLock.unlock();
        }
    }
}
