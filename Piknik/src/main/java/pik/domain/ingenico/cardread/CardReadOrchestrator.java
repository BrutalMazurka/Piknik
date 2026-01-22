package pik.domain.ingenico.cardread;

import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.domain.duk.card.CardReadOrder;
import pik.domain.duk.card.CardReadOrderProcessor;
import pik.domain.ingenico.CardDetectedData;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.tap.CardTappingRequest;
import pik.domain.ingenico.tap.ICardTapping;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.*;

/**
 * Orchestrates card reading process.
 * Coordinates card tapping, order execution, and session management.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
@Singleton
public class CardReadOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(CardReadOrchestrator.class);

    private final ICardTapping cardTapping;
    private final Injector injector;
    private final CardReadSessionManager sessionManager;
    private final IngenicoReaderDevice readerDevice;
    private final ExecutorService executor;
    private final ScheduledExecutorService timeoutScheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTimeouts;

    @Inject
    public CardReadOrchestrator(ICardTapping cardTapping,
                                Injector injector,
                                CardReadSessionManager sessionManager,
                                IngenicoReaderDevice readerDevice) {
        this.cardTapping = cardTapping;
        this.injector = injector;
        this.sessionManager = sessionManager;
        this.readerDevice = readerDevice;
        this.executor = Executors.newSingleThreadExecutor();
        this.timeoutScheduler = Executors.newScheduledThreadPool(1);
        this.activeTimeouts = new ConcurrentHashMap<>();
    }

    /**
     * Start card read process
     * Returns session ID for status polling
     *
     * @param readSchema "FULL" or "BASIC" - determines which card files to read
     * @param timeoutMillis timeout for card tap in milliseconds
     * @return session ID
     * @throws IllegalStateException if reader or SAM not ready
     */
    public String startCardRead(String readSchema, int timeoutMillis) {
        logger.info("Starting card read process (schema: {}, timeout: {}ms)", readSchema, timeoutMillis);

        // Validate reader ready
        if (!readerDevice.isInitStatusDone()) {
            throw new IllegalStateException("Ingenico reader not ready");
        }

        // Check if SAM is detected and authenticated
        if (!readerDevice.getSamDuk().getSamAtr().isDukAtr()) {
            throw new IllegalStateException("SAM module not detected");
        }

        if (!readerDevice.getSamDuk().getAuth().isProcessStateFinished()) {
            throw new IllegalStateException("SAM authentication not finished (current state: " +
                    readerDevice.getSamDuk().getAuth().getProcessState() + ")");
        }

        if (!readerDevice.getSamDuk().getAuth().isAuthenticated()) {
            throw new IllegalStateException("SAM not authenticated - cannot read cards");
        }

        // Create session
        CardReadSession session = sessionManager.createSession(readSchema, timeoutMillis);

        // Start card tapping with callback
        CardTappingRequest request = new CardTappingRequest(
                CardTappingRequest.ESource.CARD_READ,
                cardData -> processCardTap(session.getSessionId(), cardData),
                () -> handleTapError(session.getSessionId())
        );

        try {
            cardTapping.start(request);
            session.setStatus(CardReadSession.Status.WAITING_FOR_CARD);
            logger.info("Card tapping started for session {}", session.getSessionId());

            // Schedule timeout to stop card tapping if no card is tapped
            scheduleTimeout(session.getSessionId(), timeoutMillis);
        } catch (Exception e) {
            logger.error("Failed to start card tapping", e);
            session.setError("Failed to start card tapping: " + e.getMessage());
            throw new RuntimeException("Failed to start card tapping", e);
        }

        return session.getSessionId();
    }

    /**
     * Schedule timeout for card reading session
     */
    private void scheduleTimeout(String sessionId, int timeoutMillis) {
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            logger.warn("Card read timeout for session {} after {}ms", sessionId, timeoutMillis);

            CardReadSession session = sessionManager.getSession(sessionId);
            if (session != null && session.getStatus() == CardReadSession.Status.WAITING_FOR_CARD) {
                try {
                    // Stop card tapping
                    cardTapping.stop(true);
                    logger.info("Card tapping stopped due to timeout for session {}", sessionId);

                    // Mark session as failed
                    sessionManager.setError(sessionId, "Timeout waiting for card tap");
                } catch (Exception e) {
                    logger.error("Error stopping card tapping on timeout for session " + sessionId, e);
                }
            }

            // Remove timeout from active timeouts
            activeTimeouts.remove(sessionId);
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        activeTimeouts.put(sessionId, timeoutTask);
        logger.debug("Scheduled timeout for session {} in {}ms", sessionId, timeoutMillis);
    }

    /**
     * Cancel timeout for card reading session
     */
    private void cancelTimeout(String sessionId) {
        ScheduledFuture<?> timeoutTask = activeTimeouts.remove(sessionId);
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
            logger.debug("Cancelled timeout for session {}", sessionId);
        }
    }

    /**
     * Called when card is detected
     * Executes card reading in background thread
     */
    private void processCardTap(String sessionId, CardDetectedData cardData) {
        // Cancel timeout since card was detected
        cancelTimeout(sessionId);

        executor.submit(() -> {
            logger.info("Processing card tap for session {}", sessionId);

            try {
                CardReadSession session = sessionManager.getSession(sessionId);
                if (session == null) {
                    logger.warn("Session {} not found", sessionId);
                    return;
                }

                session.setStatus(CardReadSession.Status.PROCESSING);
                logger.info("Card detected - UID: {}, Type: {}", cardData.getUid(), cardData.getCardType());

                // Create order and processor
                CardReadOrder order = new CardReadOrder(cardData, session.getReadSchema());
                CardReadOrderProcessor processor = new CardReadOrderProcessor(injector, order);

                // Execute processor
                logger.info("Executing CardReadOrderProcessor for session {}", sessionId);
                processor.onBeforeProcessing();
                processor.process();
                processor.onAfterProcessing();

                // Check result
                if (processor.isResultOk()) {
                    sessionManager.setCardData(sessionId, processor.getCardDuk());
                    logger.info("Card read completed successfully for session {}", sessionId);
                } else {
                    sessionManager.setError(sessionId, "Card read failed: " + processor.getResultDescription());
                    logger.error("Card read failed for session {}: {}", sessionId, processor.getResultDescription());
                }

            } catch (Exception e) {
                logger.error("Error processing card read for session " + sessionId, e);
                CardReadSession session = sessionManager.getSession(sessionId);
                if (session != null) {
                    session.setError("Card read error: " + e.getMessage());
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

        // Cancel timeout
        cancelTimeout(sessionId);

        CardReadSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setError("Card tapping error");
        }
    }

    /**
     * Get session status
     */
    public CardReadSession getSessionStatus(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    /**
     * Cancel active card reading session
     */
    public boolean cancelCardRead(String sessionId) {
        CardReadSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return false;
        }

        // Cancel timeout
        cancelTimeout(sessionId);

        try {
            cardTapping.stop(true);
            sessionManager.setError(sessionId, "Cancelled by user");
            logger.info("Card read session {} cancelled", sessionId);
            return true;
        } catch (Exception e) {
            logger.error("Error cancelling card read session " + sessionId, e);
            return false;
        }
    }
}