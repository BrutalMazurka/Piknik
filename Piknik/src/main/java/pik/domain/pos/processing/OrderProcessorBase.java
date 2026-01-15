package pik.domain.pos.processing;

import com.google.inject.Injector;
import epis5.pos.processing.opt.OptBase;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import pik.common.ELogger;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.tap.ICardTapping;
import pik.domain.pos.IPosDisplayService;

/**
 * Simplified OrderProcessorBase for Piknik (REST API).
 * Contains only the methods needed for SAM unlocking.
 * Full EVK version backed up as OrderProcessorBase.java.evk-backup
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
public abstract class OrderProcessorBase {
    protected final ILogger logger;
    protected final Injector injector;
    protected final IPosDisplayService displayService;
    protected final IngenicoReaderDevice ingenicoReader;
    protected final ICardTapping cardTapping;

    private String resultDescription;
    private boolean resultOk = false;

    public OrderProcessorBase(Injector injector) {
        this.injector = injector;
        this.logger = LoggerFactory.get(ELogger.POS_ORDER_PROCESSING);

        // Get required services from injector
        this.displayService = injector.getInstance(IPosDisplayService.class);
        this.ingenicoReader = injector.getInstance(IngenicoReaderDevice.class);
        this.cardTapping = injector.getInstance(ICardTapping.class);
    }

    /**
     * Main processing method - must be implemented by subclasses
     */
    public abstract void process();

    /**
     * Called before processing
     */
    public void onBeforeProcessing() {
        resultOk = false;
        resultDescription = null;
    }

    /**
     * Called after processing
     */
    public void onAfterProcessing() {
        // Override if needed
    }

    /**
     * Stop card tapping
     */
    protected void stopCardTapping(boolean setLedDiodesOff) {
        try {
            cardTapping.stop(setLedDiodesOff);
        } catch (Exception e) {
            logger.warn("Error stopping card tapping", e);
        }
    }

    /**
     * Verify Ingenico reader is ready
     */
    protected boolean verifyIngenicoReaderReady(boolean showError) {
        if (!ingenicoReader.isInitStatusDone()) {
            if (showError) {
                showErrorMessageAndFinish("Ingenico reader not initialized");
            }
            return false;
        }

        if (!ingenicoReader.getTransitApp().isConnectedAndAppAlive()) {
            if (showError) {
                showErrorMessageAndFinish("Transit app not connected");
            }
            return false;
        }

        return true;
    }

    /**
     * Get instance from injector
     */
    protected <T> T getInstance(Class<T> type) {
        return injector.getInstance(type);
    }

    /**
     * Execute operation
     */
    protected boolean executeOpt(OptBase opt) {
        try {
            opt.execute();

            if (opt.isResultOk()) {
                return true;
            } else {
                setResultError(opt.getResultDescription());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error executing operation", e);
            setResultError("Operation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Show error message and finish
     */
    protected void showErrorMessageAndFinish(String message) {
        logger.error(message);
        displayService.showErrorMessage(message);
        setResultError(message);
    }

    /**
     * Set result as OK
     */
    protected void setResultOk() {
        this.resultOk = true;
        this.resultDescription = "OK";
    }

    /**
     * Set result as error
     */
    protected void setResultError(String description) {
        this.resultOk = false;
        this.resultDescription = description;
    }

    /**
     * Check if result description is "not applicable"
     */
    protected boolean isResultDescriptionNotApplicable() {
        return resultDescription != null && resultDescription.equals("N/A");
    }

    /**
     * Get result status
     */
    public boolean isResultOk() {
        return resultOk;
    }

    /**
     * Get result description
     */
    public String getResultDescription() {
        return resultDescription;
    }
}