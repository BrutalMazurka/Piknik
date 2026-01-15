package pik.domain.ingenico.unlock;

import com.google.inject.Injector;
import epis5.duk.bck.core.sam.SamPin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.duk.sam.SamUnlockOrder;
import pik.domain.duk.sam.SamUnlockOrderProcessor;
import pik.domain.ingenico.CardDetectedData;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.tap.CardTappingRequest;
import pik.domain.ingenico.tap.ICardTapping;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates SAM unlock process.
 * Coordinates card tapping, order execution, and session management.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
@Singleton
public class SamUnlockOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(SamUnlockOrchestrator.class);

    private final ICardTapping cardTapping;
    private final Injector injector;
    private final UnlockSessionManager sessionManager;
    private final IngenicoReaderDevice readerDevice;
    private final ExecutorService executor;

    @Inject
    public SamUnlockOrchestrator(ICardTapping cardTapping,
                                  Injector injector,
                                  UnlockSessionManager sessionManager,
                                  IngenicoReaderDevice readerDevice) {
        this.cardTapping = cardTapping;
        this.injector = injector;
        this.sessionManager = sessionManager;
        this.readerDevice = readerDevice;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Start SAM unlock process
     * Returns session ID for status polling
     *
     * @param pin 6-digit PIN for SAM unlock
     * @return session ID
     * @throws IllegalArgumentException if PIN format invalid
     * @throws IllegalStateException if SAM not ready for unlock
     */
    public String startUnlock(String pin) {
        logger.info("Starting SAM unlock process");

        // Validate PIN format
        if (!SamPin.isValidFormat(pin)) {
            throw new IllegalArgumentException("Invalid PIN format: must be exactly 6 digits (0-9)");
        }

        // Check if SAM is detected and authenticated
        if (!readerDevice.getSamDuk().getSamAtr().isDukAtr()) {
            throw new IllegalStateException("SAM module not detected");
        }

        if (!readerDevice.getSamDuk().getAuth().isProcessStateFinished()) {
            throw new IllegalStateException("SAM authentication not finished (current state: " +
                    readerDevice.getSamDuk().getAuth().getProcessState() + ")");
        }

        if (readerDevice.getSamDuk().isUnlockStatusCompleted()) {
            throw new IllegalStateException("SAM already unlocked");
        }

        // Create session
        UnlockSession session = sessionManager.createSession(pin);

        // Start card tapping with callback
        CardTappingRequest request = new CardTappingRequest(
            CardTappingRequest.ESource.SAM_UNLOCK,
            cardData -> processCardTap(session.getSessionId(), cardData),
            () -> handleTapError(session.getSessionId())
        );

        try {
            cardTapping.start(request);
            session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
            logger.info("Card tapping started for session {}", session.getSessionId());
        } catch (Exception e) {
            logger.error("Failed to start card tapping", e);
            session.setError("Failed to start card tapping: " + e.getMessage());
            throw new RuntimeException("Failed to start card tapping", e);
        }

        return session.getSessionId();
    }

    /**
     * Called when card is detected
     * Executes SAM unlock in background thread
     */
    private void processCardTap(String sessionId, CardDetectedData cardData) {
        executor.submit(() -> {
            logger.info("Processing card tap for session {}", sessionId);

            try {
                UnlockSession session = sessionManager.getSession(sessionId);
                if (session == null) {
                    logger.warn("Session {} not found", sessionId);
                    return;
                }

                session.setStatus(UnlockSession.Status.PROCESSING);
                logger.info("Card detected - UID: {}, Type: {}", cardData.getUid(), cardData.getCardType());

                // Create order and processor (EVK pattern)
                SamUnlockOrder order = new SamUnlockOrder(cardData, session.getPin());
                SamUnlockOrderProcessor processor = new SamUnlockOrderProcessor(injector, order);

                // Execute processor
                logger.info("Executing SamUnlockOrderProcessor for session {}", sessionId);
                processor.onBeforeProcessing();
                processor.process();
                processor.onAfterProcessing();

                // Check result
                if (processor.isResultOk()) {
                    session.setStatus(UnlockSession.Status.COMPLETED);
                    logger.info("SAM unlock completed successfully for session {}", sessionId);
                } else {
                    session.setError("Unlock failed: " + processor.getResultDescription());
                    logger.error("SAM unlock failed for session {}: {}", sessionId, processor.getResultDescription());
                }

            } catch (Exception e) {
                logger.error("Error processing SAM unlock for session " + sessionId, e);
                UnlockSession session = sessionManager.getSession(sessionId);
                if (session != null) {
                    session.setError("Unlock error: " + e.getMessage());
                }
            } finally {
                // Stop card tapping
                try {
                    cardTapping.stop(true);
                    logger.info("Card tapping stopped for session {}", sessionId);
                } catch (Exception e) {
                    logger.warn("Error stopping card tapping", e);
                }
            }
        });
    }

    /**
     * Called if card tapping fails
     */
    private void handleTapError(String sessionId) {
        logger.error("Card tapping error for session {}", sessionId);
        UnlockSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setError("Card tapping error");
        }
    }

    /**
     * Get session status
     */
    public UnlockSession getSessionStatus(String sessionId) {
        return sessionManager.getSession(sessionId);
    }
}