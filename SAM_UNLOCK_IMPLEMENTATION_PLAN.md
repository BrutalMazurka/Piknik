# SAM Module Unlocking Implementation Plan - Piknik Project

**Date:** 2026-01-15
**Project:** Piknik - SAM Module Unlocking for Ingenico OPEN/1500
**Source:** EVK_Most_7 (proven working implementation)

---

## User Decision
✅ **Selected Approach:** Two-step async REST API with session management

---

## Executive Summary

Complete the SAM module unlocking implementation in Piknik by transferring and adapting the proven working code from the EVK project. The implementation uses a two-step async REST API approach to handle the asynchronous nature of card tapping while maintaining clean REST architecture.

**Critical Constraint:** SAM unlocking allows only **3 attempts** before permanent lockout. Implementation must use proven code from EVK to minimize risk.

---

## Current State Analysis

### What's Already Transferred to Piknik ✅

**SAM Unlock Package** (`pik.domain.duk.sam`):
- `SamUnlockOrder.java` - DTO containing card data and PIN
- `SamUnlockOrderProcessor.java` - Main business logic processor (63 lines)
- `SamVerifyPinOpt.java` - APDU operation for PIN verification (128 lines) - **Core implementation, proven in EVK**

**Supporting Classes:**
- `OrderProcessorBase.java` - Base class with helper methods (500 lines)
- `CardDetectedData.java` - Card information holder
- Card tapping infrastructure: `ICardTapping`, `IngenicoCardTappingController`

**REST API Infrastructure:**
- `IngenicoService.java` - Has TODO at line 381
- `IngenicoController.java` - Current endpoint `POST /api/ingenico/unlock`
- Test client: `testclient.html` - Web-based UI

### What's Missing ❌

1. **APDU Command Execution** - Line 381 TODO in `IngenicoService.java`
2. **Card Data Integration** - Current API only accepts PIN, needs card UID
3. **Session Management** - No tracking for async unlock attempts
4. **Orchestration Layer** - No coordination between card tapping and order execution
5. **Display Service Stubs** - EVK uses SWING UI, Piknik needs no-op replacements

---

## Architectural Challenge

### The Problem
- **EVK:** Synchronous SWING UI → User enters PIN → User taps card → Process unlock
- **Piknik:** Async REST API → Client sends PIN → Server must wait for card tap → Return result

### The Solution: Two-Step Async REST API

**New Endpoints:**
1. `POST /api/ingenico/unlock/start` - Accepts PIN, starts card tapping, returns session ID (202 Accepted)
2. `GET /api/ingenico/unlock/status/{sessionId}` - Returns current status (polling)

**Flow:**
```
Client                    REST API                 Orchestrator           Card Tapping         SAM/APDU
  |                          |                          |                      |                    |
  |-- POST /unlock/start --->|                          |                      |                    |
  |    { pin: "123456" }     |                          |                      |                    |
  |                          |-- startUnlock() -------->|                      |                    |
  |                          |                          |-- start tapping ---->|                    |
  |<-- 202 Accepted ---------|                          |                      |                    |
  |    { sessionId }         |                          |                      |                    |
  |                          |                          |                      |                    |
  |                          |                          |<-- card detected ----|                    |
  |                          |                          |   (callback)         |                    |
  |                          |                          |                      |                    |
  |                          |                          |-- create order ------|                    |
  |                          |                          |-- process order -----|                    |
  |                          |                          |                      |                    |
  |                          |                          |-- SamVerifyPinOpt ---|------------------->|
  |                          |                          |                      |                    |
  |                          |                          |<-- success/failure ---|<-------------------|
  |                          |                          |-- update session ----|                    |
  |                          |                          |                      |                    |
  |-- GET /unlock/status --->|                          |                      |                    |
  |    /{sessionId}          |                          |                      |                    |
  |<-- 200 OK ---------------|                          |                      |                    |
  |    { status: "COMPLETED" }                          |                      |                    |
```

---

## Implementation Plan

### Phase 1: Foundation (Create Missing Infrastructure)

**1.1 Display Service Stubs**

EVK uses `IPosDisplayService` for SWING UI. Piknik needs no-op implementations.

Create files:
- `pik/domain/pos/IPosDisplayService.java`
- `pik/domain/pos/NoOpDisplayService.java`

```java
// IPosDisplayService.java
package pik.domain.pos;

public interface IPosDisplayService {
    void showCardProcessing(Object msg);
    void showResult(Object display);
    void showDefault();
    void showErrorMessage(String message);
}

// NoOpDisplayService.java
package pik.domain.pos;

import javax.inject.Singleton;

@Singleton
public class NoOpDisplayService implements IPosDisplayService {
    // All methods empty - Piknik is REST API only
    @Override public void showCardProcessing(Object msg) {}
    @Override public void showResult(Object display) {}
    @Override public void showDefault() {}
    @Override public void showErrorMessage(String message) {}
}
```

**1.2 Support Classes**

Create files:
- `pik/domain/pos/processing/CardProcessingDialogMsg.java`
- `pik/domain/pos/processing/EvkResultDisplay.java`

```java
// CardProcessingDialogMsg.java
package pik.domain.pos.processing;

public class CardProcessingDialogMsg {
    private final String message;

    private CardProcessingDialogMsg(String message) {
        this.message = message;
    }

    public static CardProcessingDialogMsg newDuk(String msg) {
        return new CardProcessingDialogMsg(msg);
    }

    public String getMessage() {
        return message;
    }
}

// EvkResultDisplay.java
package pik.domain.pos.processing;

public class EvkResultDisplay {
    private final String message;

    private EvkResultDisplay(String message) {
        this.message = message;
    }

    public static EvkResultDisplay newGeneral(String msg) {
        return new EvkResultDisplay(msg);
    }

    public String getMessage() {
        return message;
    }
}
```

**1.3 Register in Guice**

Modify `pik/GuiceModule.java`:
```java
// In configure() method
bind(IPosDisplayService.class).to(NoOpDisplayService.class).in(Singleton.class);
```

### Phase 2: Session Management

**2.1 Create UnlockSession**

Create file: `pik/domain/ingenico/unlock/UnlockSession.java`

```java
package pik.domain.ingenico.unlock;

public class UnlockSession {
    public enum Status {
        PENDING,           // Session created
        WAITING_FOR_CARD,  // Card tapping started
        PROCESSING,        // Card detected, executing unlock
        COMPLETED,         // Successfully unlocked
        FAILED,            // Failed (error or timeout)
        EXPIRED            // Session expired
    }

    private final String sessionId;
    private final String pin;
    private Status status;
    private String errorMessage;
    private final long createdAt;
    private long updatedAt;

    public UnlockSession(String sessionId, String pin) {
        this.sessionId = sessionId;
        this.pin = pin;
        this.status = Status.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
    }

    public synchronized void setStatus(Status status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void setError(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - createdAt > timeoutMillis;
    }

    // Getters (all synchronized)
    public synchronized String getSessionId() { return sessionId; }
    public synchronized String getPin() { return pin; }
    public synchronized Status getStatus() { return status; }
    public synchronized String getErrorMessage() { return errorMessage; }
    public synchronized long getCreatedAt() { return createdAt; }
    public synchronized long getUpdatedAt() { return updatedAt; }
}
```

**2.2 Create UnlockSessionManager**

Create file: `pik/domain/ingenico/unlock/UnlockSessionManager.java`

```java
package pik.domain.ingenico.unlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UnlockSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(UnlockSessionManager.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, UnlockSession> sessions = new ConcurrentHashMap<>();

    public UnlockSession createSession(String pin) {
        String sessionId = UUID.randomUUID().toString();
        UnlockSession session = new UnlockSession(sessionId, pin);
        sessions.put(sessionId, session);
        logger.info("Created unlock session: {}", sessionId);
        return session;
    }

    public UnlockSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void updateStatus(String sessionId, UnlockSession.Status status) {
        UnlockSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            logger.info("Session {} status updated to {}", sessionId, status);
        }
    }

    public void setError(String sessionId, String errorMessage) {
        UnlockSession session = sessions.get(sessionId);
        if (session != null) {
            session.setError(errorMessage);
            logger.error("Session {} failed: {}", sessionId, errorMessage);
        }
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        logger.info("Removed unlock session: {}", sessionId);
    }

    /**
     * Remove expired sessions (older than 5 minutes)
     * Should be called periodically
     */
    public void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, UnlockSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(SESSION_TIMEOUT_MILLIS)) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired unlock sessions", removed);
        }
    }
}
```

### Phase 3: Orchestration

**3.1 Create SamUnlockOrchestrator**

Create file: `pik/domain/ingenico/unlock/SamUnlockOrchestrator.java`

```java
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
            throw new IllegalStateException("SAM authentication not finished");
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
```

**3.2 Register in Guice**

Modify `pik/GuiceModule.java`:
```java
// In configure() method
bind(UnlockSessionManager.class).in(Singleton.class);
bind(SamUnlockOrchestrator.class).in(Singleton.class);
```

### Phase 4: Update REST API

**4.1 Modify IngenicoController**

Update `pik/domain/ingenico/IngenicoController.java`:

```java
// Add field
private final SamUnlockOrchestrator unlockOrchestrator;

// Update constructor
public IngenicoController(IngenicoService ingenicoService,
                          IntegratedController integratedController,
                          SamUnlockOrchestrator unlockOrchestrator) {
    this.ingenicoService = ingenicoService;
    this.integratedController = integratedController;
    this.unlockOrchestrator = unlockOrchestrator;
    this.gson = new Gson();
}

// Update registerRoutes()
public void registerRoutes() {
    path("/api/ingenico", () -> {
        get("/status", this::getStatus);
        get("/health", this::healthCheck);
        get("/info", this::getInfo);
        get("/config", this::getConfig);
        post("/test", this::testReader);
        get("/diagnostics", this::getDiagnostics);

        // New async unlock endpoints
        path("/unlock", () -> {
            post("/start", this::startUnlock);
            get("/status/:sessionId", this::getUnlockStatus);
        });
    });

    path("/api/ingenico/events", () -> {
        sse("/status", this::sseStatusUpdates);
    });
}

// Add new endpoint methods
/**
 * Start SAM unlock process
 * POST /api/ingenico/unlock/start
 * Body: { "pin": "123456" }
 * Returns: { "success": true, "message": "...", "data": { "sessionId": "..." } }
 */
private void startUnlock(Context ctx) {
    try {
        UnlockRequest request = ctx.bodyAsClass(UnlockRequest.class);

        if (request == null || request.pin() == null) {
            ctx.status(400).json(ApiResponse.error("PIN is required"));
            return;
        }

        // Start unlock process
        String sessionId = unlockOrchestrator.startUnlock(request.pin());

        // Return 202 Accepted with session ID
        UnlockStartResponse response = new UnlockStartResponse(sessionId);
        ctx.status(202).json(ApiResponse.success(
            "Unlock started - please tap card",
            response
        ));

        logger.info("SAM unlock started with session ID: {}", sessionId);

    } catch (IllegalArgumentException e) {
        logger.warn("Invalid unlock request: {}", e.getMessage());
        ctx.status(400).json(ApiResponse.error(e.getMessage()));
    } catch (IllegalStateException e) {
        logger.warn("Invalid state for unlock: {}", e.getMessage());
        ctx.status(409).json(ApiResponse.error(e.getMessage()));
    } catch (Exception e) {
        logger.error("Error starting SAM unlock", e);
        ctx.status(500).json(ApiResponse.error("Failed to start unlock: " + e.getMessage()));
    }
}

/**
 * Get unlock session status
 * GET /api/ingenico/unlock/status/{sessionId}
 * Returns: { "success": true, "data": { "sessionId": "...", "status": "...", ... } }
 */
private void getUnlockStatus(Context ctx) {
    try {
        String sessionId = ctx.pathParam("sessionId");
        UnlockSession session = unlockOrchestrator.getSessionStatus(sessionId);

        if (session == null) {
            ctx.status(404).json(ApiResponse.error("Session not found"));
            return;
        }

        // Convert to DTO
        UnlockStatusResponse response = new UnlockStatusResponse(
            session.getSessionId(),
            session.getStatus().name(),
            session.getErrorMessage(),
            session.getUpdatedAt()
        );

        ctx.json(ApiResponse.success(response));

    } catch (Exception e) {
        logger.error("Error getting unlock status", e);
        ctx.status(500).json(ApiResponse.error("Failed to get status: " + e.getMessage()));
    }
}

// Add DTOs (at end of class)
/**
 * Unlock start response DTO
 */
public record UnlockStartResponse(String sessionId) {}

/**
 * Unlock status response DTO
 */
public record UnlockStatusResponse(
    String sessionId,
    String status,
    String errorMessage,
    long updatedAt
) {}
```

**4.2 Update IngenicoService (Optional)**

Modify `pik/domain/ingenico/IngenicoService.java` line 381:

```java
@Override
public boolean unlockSAM(String pin) {
    logger.warn("DEPRECATED: unlockSAM() is deprecated. Use SamUnlockOrchestrator instead.");
    throw new UnsupportedOperationException(
        "This method is deprecated. Use POST /api/ingenico/unlock/start instead."
    );
}
```

### Phase 5: Update Test Client

**5.1 Modify testclient.html**

Update the JavaScript unlock function:

```javascript
async function unlockSAM() {
    const pinInput = document.getElementById('pin');
    const pin = pinInput.value;
    const unlockBtn = document.getElementById('unlockBtn');

    // Validate PIN format
    if (!/^[0-9]{6}$/.test(pin)) {
        logMessage('Invalid PIN: must be exactly 6 digits (0-9)', 'error');
        return;
    }

    unlockBtn.disabled = true;

    try {
        // Step 1: Start unlock
        logMessage(`Starting SAM unlock with PIN: ${pin}`, 'info');

        const startResponse = await fetch(`${API_BASE}/ingenico/unlock/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ pin: pin })
        });

        const startResult = await startResponse.json();

        if (!startResponse.ok) {
            logMessage(`Failed to start unlock: ${startResult.message}`, 'error');
            unlockBtn.disabled = false;
            return;
        }

        const sessionId = startResult.data.sessionId;
        logMessage(`✓ Unlock session started: ${sessionId}`, 'success');
        logMessage('⏳ Waiting for card tap...', 'info');

        // Step 2: Poll for status
        let pollCount = 0;
        const maxPolls = 60; // 30 seconds (60 * 500ms)

        const pollInterval = setInterval(async () => {
            try {
                pollCount++;

                const statusResponse = await fetch(`${API_BASE}/ingenico/unlock/status/${sessionId}`);
                const statusResult = await statusResponse.json();

                if (!statusResponse.ok) {
                    clearInterval(pollInterval);
                    logMessage(`Error checking status: ${statusResult.message}`, 'error');
                    unlockBtn.disabled = false;
                    return;
                }

                const session = statusResult.data;

                if (session.status === 'COMPLETED') {
                    clearInterval(pollInterval);
                    logMessage('✓✓✓ SAM UNLOCKED SUCCESSFULLY! ✓✓✓', 'success');
                    pinInput.value = '';
                    unlockBtn.disabled = false;

                } else if (session.status === 'FAILED') {
                    clearInterval(pollInterval);
                    const errorMsg = session.errorMessage || 'Unknown error';
                    logMessage(`✗ Unlock failed: ${errorMsg}`, 'error');
                    unlockBtn.disabled = false;

                } else if (session.status === 'PROCESSING') {
                    logMessage('⚙️ Processing unlock...', 'info');

                } else if (pollCount >= maxPolls) {
                    clearInterval(pollInterval);
                    logMessage('✗ Unlock timeout (30 seconds) - please try again', 'error');
                    unlockBtn.disabled = false;
                }
                // Continue polling for WAITING_FOR_CARD, PENDING

            } catch (error) {
                clearInterval(pollInterval);
                logMessage(`Status check error: ${error.message}`, 'error');
                unlockBtn.disabled = false;
            }
        }, 500); // Poll every 500ms

    } catch (error) {
        logMessage(`SAM unlock error: ${error.message}`, 'error');
        unlockBtn.disabled = false;
    }
}
```

**5.2 Update HTML UI (Optional)**

Add better visual feedback:

```html
<!-- Add status indicator -->
<div id="unlockStatus" style="margin-top: 10px; display: none;">
    <div class="status-indicator">
        <span id="statusIcon">⏳</span>
        <span id="statusText">Waiting for card...</span>
    </div>
</div>
```

### Phase 6: Clean Up Transferred Code

**6.1 Update SamUnlockOrderProcessor**

Modify `pik/domain/duk/sam/SamUnlockOrderProcessor.java`:

```java
// Remove or comment out display calls
@Override
public void process() {
    try {
        stopCardTapping(true);

        if (!verifyIngenicoReaderReady(true)) {
            return;
        }

        if (!verifySamDukReadyForUnlock()) {
            return;
        }

        if (!SamPin.isValidFormat(order.getPin())) {
            showErrorMessageAndFinish("Neplatný formát zadaného PINu!");
            return;
        }

        // REMOVED: displayService.showCardProcessing(CardProcessingDialogMsg.newDuk(...))
        // Piknik is REST API - no display needed
        logger.info("Processing SAM unlock with card UID: {}", order.getCardDetectedData().getUid());

        SamVerifyPinOpt verifyPinOpt = getInstance(SamVerifyPinOpt.class);
        verifyPinOpt.setOrder(order);
        if (!executeOpt(verifyPinOpt)) {
            return;
        }

        // REMOVED: showResult(EvkResultDisplay.newGeneral("SAM ODEMČEN"), 1_800);
        // Result is tracked via session status in orchestrator
        logger.info("SAM unlock completed successfully");

    } catch (Exception e) {
        logger.fatal("Processing SamUnlockOrderProcessor", e);
        showErrorMessageAndFinish(e.getMessage());
    }
}
```

**6.2 No Changes Needed**

Keep these files as-is:
- ✅ `SamVerifyPinOpt.java` - Core APDU implementation (proven working)
- ✅ `SamUnlockOrder.java` - Simple DTO
- ✅ `OrderProcessorBase.java` - Validation helpers are useful

---

## Files Summary

### Files to Create (7 new files)

1. `pik/domain/pos/IPosDisplayService.java` - Display service interface
2. `pik/domain/pos/NoOpDisplayService.java` - No-op implementation
3. `pik/domain/pos/processing/CardProcessingDialogMsg.java` - Support class
4. `pik/domain/pos/processing/EvkResultDisplay.java` - Support class
5. `pik/domain/ingenico/unlock/UnlockSession.java` - Session state
6. `pik/domain/ingenico/unlock/UnlockSessionManager.java` - Session management
7. `pik/domain/ingenico/unlock/SamUnlockOrchestrator.java` - Main orchestrator

### Files to Modify (4 files)

1. `pik/GuiceModule.java` - Add dependency injection bindings
2. `pik/domain/ingenico/IngenicoController.java` - Add new REST endpoints
3. `pik/domain/duk/sam/SamUnlockOrderProcessor.java` - Remove display calls
4. `resources/html/testclient.html` - Update JavaScript client

### Files Unchanged (Proven EVK Code)

1. ✅ `pik/domain/duk/sam/SamVerifyPinOpt.java` - Core APDU implementation
2. ✅ `pik/domain/duk/sam/SamUnlockOrder.java` - DTO
3. ✅ `pik/domain/pos/processing/OrderProcessorBase.java` - Base class
4. ✅ Card tapping infrastructure

---

## Testing & Validation

### Unit Testing (No Hardware Required)

1. **Session Management**
   - Create session → Verify ID generated
   - Update status → Verify state changes
   - Session timeout → Verify cleanup

2. **PIN Validation**
   - Valid PIN: "123456" → Pass
   - Invalid: "12345" → Fail (too short)
   - Invalid: "ABCDEF" → Fail (not digits)
   - Invalid: null → Fail

3. **REST API**
   - POST /unlock/start → Returns 202 + sessionId
   - GET /unlock/status/{id} → Returns session data
   - Invalid session ID → Returns 404

### Integration Testing (With Test Card)

1. **Card Tapping Flow**
   - Start unlock → Card tapping starts
   - Tap any DUK card → Card UID captured
   - Verify logs show correct UID

2. **Session State Transitions**
   - PENDING → WAITING_FOR_CARD → PROCESSING → COMPLETED
   - Verify each transition logged

3. **Error Handling**
   - No card tapped (30s timeout) → FAILED
   - Card tapping error → FAILED
   - Invalid PIN → 400 Bad Request

### Real SAM Unlock Testing (CRITICAL)

⚠️ **Only 3 attempts before permanent SAM lockout!**

**Pre-flight Checklist:**
- [ ] Test card tapping works (UID captured in logs)
- [ ] Session management works (create → complete)
- [ ] PIN validation works (6 digits, 0-9 only)
- [ ] EVK still works (reference check)
- [ ] Correct PIN verified from documentation
- [ ] Correct unlock card identified
- [ ] Logs show detailed APDU communication

**Safety Protocol:**

1. **Verify EVK** - Confirm EVK SAM unlock still works as reference
2. **Triple-check PIN** - Get correct PIN from documentation
3. **Verify card** - Ensure correct unlock card (not test card)
4. **Monitor logs** - Set log level to DEBUG, watch for:
   - Card UID detection
   - Session cipher encryption
   - APDU request/response
   - Status word validation
5. **First attempt:**
   - Use HTML test client
   - Enter PIN carefully
   - Tap card when prompted
   - Watch logs in real-time
   - If ANY errors appear → STOP immediately
6. **Success criteria:**
   - Logs show "SAM ODEMČEN" or "COMPLETED"
   - Unlock status changes to COMPLETED
   - No errors in APDU response

**If unlock fails:**
- Review logs for exact error
- Compare with EVK logs (if available)
- Check session cipher initialization
- Verify PIN format in APDU
- Do NOT retry without understanding the failure

---

## Risk Mitigation

### Primary Risk: SAM Permanent Lockout

**Likelihood:** Low (if using proven EVK code)
**Impact:** CRITICAL (permanent SAM lockout, hardware replacement needed)

**Mitigations:**
1. ✅ Use `SamVerifyPinOpt` from EVK unchanged (proven working)
2. ✅ Validate PIN at 3 layers (client JS, REST API, processor)
3. ✅ Test card tapping independently before unlock
4. ✅ Extensive logging before real attempt
5. ✅ Verify EVK still works as reference
6. ✅ Document exact PIN and card requirements

### Secondary Risks

**Risk: Card data not captured**
- Test card tapping with test card first
- Add DEBUG logging for card detection
- Verify `CardDetectedData.getUidBytes()` non-empty

**Risk: Session state race conditions**
- Use `ConcurrentHashMap` for thread-safe storage
- Synchronize status updates in `UnlockSession`
- Single-threaded executor for order processing

**Risk: Memory leaks from abandoned sessions**
- Session timeout: 5 minutes
- Periodic cleanup task
- Limit max concurrent sessions

---

## Success Criteria

### Implementation Complete When:

1. ✅ All 7 new files created and compile
2. ✅ Guice dependency injection configured
3. ✅ REST API endpoints functional
4. ✅ Test client updated with polling
5. ✅ Unit tests pass
6. ✅ Integration tests pass with test card
7. ✅ Code cleanup complete (display calls removed)

### SAM Unlock Functional When:

1. ✅ Card tapping detects card and captures UID
2. ✅ Session state transitions correctly
3. ✅ `SamVerifyPinOpt` executes without errors
4. ✅ APDU response status word is success (0x9000)
5. ✅ SAM unlock status changes to `COMPLETED`
6. ✅ No SAM lockout (all 3 attempts preserved)

---

## Rollback Plan

If implementation fails or SAM lockout risk too high:

1. **Revert to EVK**
   - Use EVK project for SAM unlock
   - Keep Piknik for other features
   - Document limitation

2. **Simplify approach**
   - Use direct APDU in `IngenicoService` (blocking)
   - Skip session management
   - Accept timeout limitations

3. **External tool**
   - Create standalone CLI tool using EVK code
   - Call from Piknik via process execution
   - Isolate risk from main application

---

## Implementation Time Estimate

- **Phase 1: Foundation** - 1-2 hours
- **Phase 2: Session Management** - 1 hour
- **Phase 3: Orchestration** - 2-3 hours
- **Phase 4: REST API** - 1 hour
- **Phase 5: Test Client** - 1 hour
- **Phase 6: Testing** - 2-3 hours

**Total: 8-12 hours**

---

## References

### EVK Project Locations
- SAM unlock implementation: `~/WorkCode/DP_Most/EVK/EVK_Most_7/Evk.Domain/src/evk/domain/duk/sam/`
- SWING UI (reference only): `~/WorkCode/DP_Most/EVK/EVK_Most_7/Evk/src/evk/ui/duk/sam/`

### Piknik Project Locations
- Domain logic: `~/WorkCode/DP_Most/PIK_GitHub/Piknik/Piknik/src/main/java/pik/domain/`
- Resources: `~/WorkCode/DP_Most/PIK_GitHub/Piknik/Piknik/src/main/resources/`

### Key Classes from EVK (Proven Working)
- `SamVerifyPinOpt.java` - APDU unlock operation (128 lines)
- `SamUnlockOrderProcessor.java` - Business logic (63 lines)
- `SamDukAuthStateMachine.java` - Authentication state machine

---

## Notes

- This plan implements SAM unlocking using the **two-step async REST API** approach as selected by the user
- The implementation reuses proven EVK code (`SamVerifyPinOpt`) to minimize risk of SAM lockout
- Display service stubs replace SWING UI from EVK (Piknik is headless REST API)
- Session management enables async card tapping in REST architecture
- All validation from EVK is preserved (PIN format, SAM state, authentication)
- Testing protocol ensures safety before attempting real SAM unlock

**Critical Reminder:** SAM unlocking allows only **3 attempts** before permanent lockout. Test thoroughly with mock data and test cards before using real SAM unlock card.

---

**End of Implementation Plan**
