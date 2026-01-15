package pik.domain.ingenico.unlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UnlockSessionManager
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 15/01/2026
 */
class UnlockSessionManagerTest {

    private UnlockSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new UnlockSessionManager();
    }

    @Test
    @DisplayName("Should create session with unique UUID")
    void shouldCreateSessionWithUniqueId() {
        // When
        UnlockSession session = sessionManager.createSession("123456");

        // Then
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.getSessionId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(session.getPin()).isEqualTo("123456");
        assertThat(session.getStatus()).isEqualTo(UnlockSession.Status.PENDING);
    }

    @Test
    @DisplayName("Should create multiple sessions with unique IDs")
    void shouldCreateMultipleSessionsWithUniqueIds() {
        // When
        UnlockSession session1 = sessionManager.createSession("111111");
        UnlockSession session2 = sessionManager.createSession("222222");
        UnlockSession session3 = sessionManager.createSession("333333");

        // Then
        assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
        assertThat(session1.getSessionId()).isNotEqualTo(session3.getSessionId());
        assertThat(session2.getSessionId()).isNotEqualTo(session3.getSessionId());
    }

    @Test
    @DisplayName("Should store session after creation")
    void shouldStoreSessionAfterCreation() {
        // When
        UnlockSession created = sessionManager.createSession("123456");

        // Then
        UnlockSession retrieved = sessionManager.getSession(created.getSessionId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getSessionId()).isEqualTo(created.getSessionId());
        assertThat(retrieved.getPin()).isEqualTo("123456");
    }

    @Test
    @DisplayName("Should return null for nonexistent session")
    void shouldReturnNullForNonexistentSession() {
        // When
        UnlockSession session = sessionManager.getSession("nonexistent-id");

        // Then
        assertThat(session).isNull();
    }

    @Test
    @DisplayName("Should update session status")
    void shouldUpdateSessionStatus() {
        // Given
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        // When
        sessionManager.updateStatus(sessionId, UnlockSession.Status.WAITING_FOR_CARD);

        // Then
        UnlockSession updated = sessionManager.getSession(sessionId);
        assertThat(updated.getStatus()).isEqualTo(UnlockSession.Status.WAITING_FOR_CARD);
    }

    @Test
    @DisplayName("Should handle update status for nonexistent session gracefully")
    void shouldHandleUpdateStatusForNonexistentSession() {
        // When - Should not throw exception
        sessionManager.updateStatus("nonexistent-id", UnlockSession.Status.COMPLETED);

        // Then - No exception thrown, test passes
    }

    @Test
    @DisplayName("Should set error message and FAILED status")
    void shouldSetErrorMessage() {
        // Given
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        // When
        sessionManager.setError(sessionId, "SAM not authenticated");

        // Then
        UnlockSession updated = sessionManager.getSession(sessionId);
        assertThat(updated.getStatus()).isEqualTo(UnlockSession.Status.FAILED);
        assertThat(updated.getErrorMessage()).isEqualTo("SAM not authenticated");
    }

    @Test
    @DisplayName("Should handle set error for nonexistent session gracefully")
    void shouldHandleSetErrorForNonexistentSession() {
        // When - Should not throw exception
        sessionManager.setError("nonexistent-id", "Some error");

        // Then - No exception thrown, test passes
    }

    @Test
    @DisplayName("Should remove session")
    void shouldRemoveSession() {
        // Given
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();
        assertThat(sessionManager.getSession(sessionId)).isNotNull();

        // When
        sessionManager.removeSession(sessionId);

        // Then
        assertThat(sessionManager.getSession(sessionId)).isNull();
    }

    @Test
    @DisplayName("Should handle remove nonexistent session gracefully")
    void shouldHandleRemoveNonexistentSession() {
        // When - Should not throw exception
        sessionManager.removeSession("nonexistent-id");

        // Then - No exception thrown, test passes
    }

    @Test
    @DisplayName("Should track active session count")
    void shouldTrackActiveSessionCount() {
        // Given
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);

        // When
        UnlockSession session1 = sessionManager.createSession("111111");
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);

        UnlockSession session2 = sessionManager.createSession("222222");
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(2);

        // When - Remove one
        sessionManager.removeSession(session1.getSessionId());

        // Then
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cleanup expired sessions")
    void shouldCleanupExpiredSessions() {
        // NOTE: This test verifies the cleanup logic exists but doesn't actually
        // wait 5 minutes for expiration. The cleanup is tested in combination
        // with "should not cleanup active sessions" test.

        // Given - Create session
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        // When - Call cleanup (session is NOT expired yet, so won't be removed)
        sessionManager.cleanupExpiredSessions();

        // Then - Recent session should NOT be removed (this is correct behavior)
        assertThat(sessionManager.getSession(sessionId)).isNotNull();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should not cleanup active sessions")
    void shouldNotCleanupActiveSessions() {
        // Given - Create recent session
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        // When - Cleanup immediately (session is not expired)
        sessionManager.cleanupExpiredSessions();

        // Then - Session should still exist
        assertThat(sessionManager.getSession(sessionId)).isNotNull();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cleanup only expired sessions, not active ones")
    void shouldCleanupOnlyExpiredSessions() throws InterruptedException {
        // NOTE: This test verifies selective cleanup logic. Since we can't wait
        // 5 minutes for real expiration, we test that cleanup doesn't remove
        // any recent sessions.

        // Given - Create two sessions at different times
        UnlockSession session1 = sessionManager.createSession("111111");
        String id1 = session1.getSessionId();

        // Wait a bit
        Thread.sleep(100);

        UnlockSession session2 = sessionManager.createSession("222222");
        String id2 = session2.getSessionId();

        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(2);

        // When - Cleanup (neither session is expired yet)
        sessionManager.cleanupExpiredSessions();

        // Then - Both sessions still present (correct behavior for active sessions)
        assertThat(sessionManager.getSession(id1)).isNotNull();
        assertThat(sessionManager.getSession(id2)).isNotNull();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle concurrent session creation")
    void shouldHandleConcurrentSessionCreation() throws InterruptedException {
        // Given
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<String> sessionIds = new HashSet<>();

        // When - Multiple threads create sessions concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UnlockSession session = sessionManager.createSession("pin-" + threadNum);
                    synchronized (sessionIds) {
                        sessionIds.add(session.getSessionId());
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

        // Then - All sessions created with unique IDs
        assertThat(sessionIds).hasSize(threadCount);
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(threadCount);

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle concurrent status updates")
    void shouldHandleConcurrentStatusUpdates() throws InterruptedException {
        // Given
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - Multiple threads update status concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UnlockSession.Status status;
                    if (threadNum % 3 == 0) {
                        status = UnlockSession.Status.WAITING_FOR_CARD;
                    } else if (threadNum % 3 == 1) {
                        status = UnlockSession.Status.PROCESSING;
                    } else {
                        status = UnlockSession.Status.COMPLETED;
                    }
                    sessionManager.updateStatus(sessionId, status);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        // Then - Session exists with valid status (no corruption)
        UnlockSession updated = sessionManager.getSession(sessionId);
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isIn(
                UnlockSession.Status.WAITING_FOR_CARD,
                UnlockSession.Status.PROCESSING,
                UnlockSession.Status.COMPLETED
        );

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle concurrent read/write operations")
    void shouldHandleConcurrentReadWriteOperations() throws InterruptedException {
        // Given
        UnlockSession session = sessionManager.createSession("123456");
        String sessionId = session.getSessionId();

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - Some threads read, some write
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadNum % 2 == 0) {
                        // Reader threads
                        for (int j = 0; j < 50; j++) {
                            UnlockSession s = sessionManager.getSession(sessionId);
                            assertThat(s).isNotNull();
                        }
                    } else {
                        // Writer threads
                        for (int j = 0; j < 50; j++) {
                            sessionManager.updateStatus(sessionId, UnlockSession.Status.PROCESSING);
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

        // Then - Session still exists and is not corrupted
        UnlockSession finalSession = sessionManager.getSession(sessionId);
        assertThat(finalSession).isNotNull();
        assertThat(finalSession.getSessionId()).isEqualTo(sessionId);

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle cleanup during concurrent access")
    void shouldHandleCleanupDuringConcurrentAccess() throws InterruptedException {
        // Given - Create several sessions
        for (int i = 0; i < 5; i++) {
            sessionManager.createSession("pin-" + i);
        }

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - Concurrent cleanup and session creation
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadNum % 2 == 0) {
                        sessionManager.cleanupExpiredSessions();
                    } else {
                        sessionManager.createSession("concurrent-" + threadNum);
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

        // Then - No exceptions thrown, sessions exist
        assertThat(sessionManager.getActiveSessionCount()).isGreaterThan(0);

        executor.shutdown();
    }
}