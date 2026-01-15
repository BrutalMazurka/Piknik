package pik.domain.ingenico.unlock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UnlockSession
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 15/01/2026
 */
class UnlockSessionTest {

    @Test
    @DisplayName("Should initialize session with PENDING status")
    void shouldInitializeWithPendingStatus() {
        // When
        UnlockSession session = new UnlockSession("test-session-id", "123456");

        // Then
        assertThat(session.getSessionId()).isEqualTo("test-session-id");
        assertThat(session.getPin()).isEqualTo("123456");
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.PENDING);
        assertThat(session.getErrorMessage()).isNull();
        assertThat(session.getCreatedAt()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(session.getUpdatedAt()).isEqualTo(session.getCreatedAt());
    }

    @Test
    @DisplayName("Should transition from PENDING to WAITING_FOR_CARD")
    void shouldTransitionToWaitingForCard() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        long initialUpdatedAt = session.getUpdatedAt();

        // Small delay to ensure timestamp changes
        sleep(10);

        // When
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);

        // Then
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.WAITING_FOR_CARD);
        assertThat(session.getUpdatedAt()).isGreaterThan(initialUpdatedAt);
        assertThat(session.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should transition from WAITING_FOR_CARD to PROCESSING")
    void shouldTransitionToProcessing() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
        long waitingUpdatedAt = session.getUpdatedAt();

        sleep(10);

        // When
        session.setStatus(UnlockSession.Status.PROCESSING);

        // Then
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.PROCESSING);
        assertThat(session.getUpdatedAt()).isGreaterThan(waitingUpdatedAt);
    }

    @Test
    @DisplayName("Should transition to COMPLETED on success")
    void shouldTransitionToCompleted() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        session.setStatus(UnlockSession.Status.PROCESSING);

        // When
        session.setStatus(UnlockSession.Status.COMPLETED);

        // Then
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.COMPLETED);
        assertThat(session.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should transition to FAILED and store error message")
    void shouldTransitionToFailedWithError() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        session.setStatus(UnlockSession.Status.PROCESSING);
        long processingUpdatedAt = session.getUpdatedAt();

        sleep(10);

        // When
        session.setError("SAM authentication failed");

        // Then
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.FAILED);
        assertThat(session.getErrorMessage()).isEqualTo("SAM authentication failed");
        assertThat(session.getUpdatedAt()).isGreaterThan(processingUpdatedAt);
    }

    @Test
    @DisplayName("Should update timestamp on each status change")
    void shouldUpdateTimestampOnStatusChange() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        long createdAt = session.getUpdatedAt();

        sleep(10);

        // When - First change
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
        long firstUpdate = session.getUpdatedAt();

        sleep(10);

        // When - Second change
        session.setStatus(UnlockSession.Status.PROCESSING);
        long secondUpdate = session.getUpdatedAt();

        // Then
        assertThat(firstUpdate).isGreaterThan(createdAt);
        assertThat(secondUpdate).isGreaterThan(firstUpdate);
    }

    @Test
    @DisplayName("Should detect expired session after timeout")
    void shouldDetectExpiredSession() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");

        // When - Check immediately
        boolean expiredImmediately = session.isExpired(5000);

        // When - Check with past timestamp (simulate 6 seconds passed)
        sleep(100);
        boolean expiredAfterTimeout = session.isExpired(50); // 50ms timeout

        // Then
        assertThat(expiredImmediately).isFalse();
        assertThat(expiredAfterTimeout).isTrue();
    }

    @Test
    @DisplayName("Should not detect active session as expired")
    void shouldNotDetectActiveSessionAsExpired() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        long fiveMinutes = 5 * 60 * 1000;

        // When
        boolean expired = session.isExpired(fiveMinutes);

        // Then
        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent status updates")
    void shouldBeThreadSafeForStatusUpdates() throws InterruptedException {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - Multiple threads update status concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    if (threadNum % 3 == 0) {
                        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
                    } else if (threadNum % 3 == 1) {
                        session.setStatus(UnlockSession.Status.PROCESSING);
                    } else {
                        session.setStatus(UnlockSession.Status.COMPLETED);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(5, TimeUnit.SECONDS); // Wait for completion

        // Then - No exceptions thrown, status is one of the valid states
        assertThat(session.getStatus()).isIn(
                UnlockSession.Status.WAITING_FOR_CARD,
                UnlockSession.Status.PROCESSING,
                UnlockSession.Status.COMPLETED
        );

        executor.shutdown();
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent getter calls")
    void shouldBeThreadSafeForGetters() throws InterruptedException {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - Multiple threads read concurrently while one writes
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadNum == 0) {
                        // One thread writes
                        for (int j = 0; j < 100; j++) {
                            session.setStatus(UnlockSession.Status.PROCESSING);
                        }
                    } else {
                        // Other threads read
                        for (int j = 0; j < 100; j++) {
                            String id = session.getSessionId();
                            String pin = session.getPin();
                            UnlockSession.Status status = session.getStatus();
                            if (id != null && pin != null && status != null) {
                                successCount.incrementAndGet();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        // Then - All reads succeeded without null values
        assertThat(successCount.get()).isEqualTo(19 * 100); // 19 reader threads, 100 reads each

        executor.shutdown();
    }

    @Test
    @DisplayName("Should provide meaningful toString representation")
    void shouldProvideToStringRepresentation() {
        // Given
        UnlockSession session = new UnlockSession("abc-123", "654321");
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);

        // When
        String str = session.toString();

        // Then
        assertThat(str)
                .contains("UnlockSession")
                .contains("abc-123")
                .contains("WAITING_FOR_CARD")
                .contains("created=")
                .contains("updated=");
    }

    @Test
    @DisplayName("Should allow transition to EXPIRED status")
    void shouldAllowExpiredStatus() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "123456");

        // When
        session.setStatus(UnlockSession.Status.EXPIRED);

        // Then
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.EXPIRED);
    }

    @Test
    @DisplayName("Should preserve PIN throughout lifecycle")
    void shouldPreservePinThroughoutLifecycle() {
        // Given
        UnlockSession session = new UnlockSession("test-id", "987654");

        // When - Go through various status changes
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
        session.setStatus(UnlockSession.Status.PROCESSING);
        session.setStatus(UnlockSession.Status.COMPLETED);

        // Then - PIN remains unchanged
        assertThat(session.getPin()).isEqualTo("987654");
    }

    @Test
    @DisplayName("Should preserve session ID throughout lifecycle")
    void shouldPreserveSessionIdThroughoutLifecycle() {
        // Given
        UnlockSession session = new UnlockSession("unique-session-123", "123456");

        // When - Go through various status changes
        session.setStatus(UnlockSession.Status.WAITING_FOR_CARD);
        session.setError("Some error");

        // Then - Session ID remains unchanged
        assertThat(session.getSessionId()).isEqualTo("unique-session-123");
    }

    /**
     * Helper method for sleeping with unchecked exception
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}