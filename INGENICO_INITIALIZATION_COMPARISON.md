# Ingenico Reader Initialization: EVK vs Piknik Comparison

## Overview
Comparison between the **working EVK implementation** and the **non-working Piknik implementation** of Ingenico reader initialization.

---

## Critical Differences Found

### 1. Protocol Proxy Initialization

#### ✅ EVK (Working) - CtrlGeneral.java:247-248, 258
```java
ifsfProtProxy.open();
ifsfDevProxyProtProxy.open();
// ...
transitProtProxy.open();
```

#### ❌ Piknik (Missing)
The proxies are created but **NEVER opened**. The `open()` method likely initializes internal state and prepares the proxy for message handling.

---

### 2. MasterLoop Registration (CRITICAL!)

#### ✅ EVK (Working) - CtrlGeneral.java:252-253, 262-263
```java
masterLoop.registerRunnable(ifsfProtProxy);
masterLoop.registerRunnable(ifsfProtCtrl);
// ...
masterLoop.registerRunnable(transitProtProxy);
masterLoop.registerRunnable(transitProtCtrl);

// Later: masterLoop.start(); (line 341)
```

#### ❌ Piknik (Missing)
Piknik **does not have a MasterLoop** running the protocol controllers!

**What MasterLoop does:**
- Runs a continuous loop calling `run()` on all registered runnables
- The protocol controllers implement `Runnable`
- The `run()` method calls `periodicalCheck()` on all registered services
- Without this, the periodic services (TransitServiceGetState, IfsfServiceDiagnosis, IngenicoReaderInitStateMachine) are **NEVER executed**

---

### 3. Initialization Sequence

#### ✅ EVK (Working) - Complete Sequence
```java
// 1. Get the reader device
IngenicoReaderDevice ingenicoReaderDevice = injector.getInstance(IngenicoReaderDevice.class);

// 2. Register apps with TCP servers (for connection events)
ingenicoReaderDevice.getIfsfApp().registerToTcpServer(iOGeneral.getIfsfTcpServerAccess());

// 3. Open the proxies
ifsfProtProxy.open();
ifsfDevProxyProtProxy.open();

// 4. Initialize protocol controller with services
ifsfProtCtrl.init(new IfsfProtCtrlRegistrationBuilder(injector).build());

// 5. Register with outputter registry
ProtocolMsgOutputters.INSTANCE.registerIngenicoIfsf(ifsfProtCtrl);

// 6. Register with MasterLoop for periodic execution
masterLoop.registerRunnable(ifsfProtProxy);
masterLoop.registerRunnable(ifsfProtCtrl);

// Repeat for Transit...

// 7. Start MasterLoop
masterLoop.start();
```

#### ❌ Piknik (Incomplete) - IntegratedController.java
```java
// 1. ✅ Get reader device
IngenicoReaderDevice readerDevice = injector.getInstance(IngenicoReaderDevice.class);

// 2. ✅ Register apps with TCP servers
readerDevice.getIfsfApp().registerToTcpServer(ioGeneral.getIfsfTcpServerAccess());
readerDevice.getTransitApp().registerToTcpServer(ioGeneral.getTransitTcpServerAccess());

// 3. ❌ MISSING: Open proxies
// ifsfProtProxy.open();
// transitProtProxy.open();

// 4. ✅ Initialize controllers (but they won't run!)
ifsfProtCtrl.init(ifsfRegBuilder.build());
transitProtCtrl.init(transitRegBuilder.build());

// 5. ❌ MISSING: Register with MasterLoop
// masterLoop.registerRunnable(ifsfProtProxy);
// masterLoop.registerRunnable(ifsfProtCtrl);
// masterLoop.registerRunnable(transitProtProxy);
// masterLoop.registerRunnable(transitProtCtrl);

// 6. ❌ MISSING: Start MasterLoop
// masterLoop.start();
```

---

## Root Cause

The protocol controllers are **initialized but never executed**.

### Why Services Never Run:
1. Services like `TransitServiceGetState` and `IfsfServiceDiagnosis` implement `IPeriodicalChecker`
2. They are registered with the protocol controllers via the registration builders
3. The protocol controllers implement `Runnable` and call `periodicalCheck()` on registered services in their `run()` method
4. **EVK**: MasterLoop continuously calls `run()` on the controllers → Services execute → Messages sent to reader
5. **Piknik**: No MasterLoop → `run()` never called → Services never execute → No messages sent to reader

### Result:
- Reader connects to Piknik's TCP servers ✅
- Piknik NEVER sends protocol messages ❌
- Connection times out after 18 seconds due to inactivity ❌
- Initialization state machine stuck at STARTING ❌

---

## Solution for Piknik

### Option 1: Add MasterLoop (Preferred - matches EVK architecture)
```java
// In IntegratedController constructor:
private final MasterLoop masterLoop;

this.masterLoop = new MasterLoop(loggerFactory.get(ELogger.APP), 2, "MasterLoop_prot");

// After initializing protocol controllers:
ifsfProtProxy.open();
ifsfDevProxyProtProxy.open();
masterLoop.registerRunnable(ifsfProtProxy);
masterLoop.registerRunnable(ifsfProtCtrl);

transitProtProxy.open();
masterLoop.registerRunnable(transitProtProxy);
masterLoop.registerRunnable(transitProtCtrl);

// In start() method, before or after ioGeneral.init():
masterLoop.start();

// In shutdown():
masterLoop.stop();
```

### Option 2: Use Existing ExecutorService
Run the protocol controllers in the existing scheduled executor service:
```java
executorService.scheduleAtFixedRate(
    () -> {
        ifsfProtProxy.run();
        ifsfProtCtrl.run();
        transitProtProxy.run();
        transitProtCtrl.run();
    },
    0, 15, TimeUnit.MILLISECONDS
);
```

---

## Files Compared

### EVK (Working):
- `/tmp/EVK/Evk.Domain/src/evk/domain/CtrlGeneral.java` - Main controller with MasterLoop
- `/tmp/EVK/Evk.Domain/src/evk/domain/IOGeneral.java` - IO initialization

### Piknik (Not Working):
- `/home/user/Piknik/Piknik/src/main/java/pik/domain/IntegratedController.java` - Main controller
- `/home/user/Piknik/Piknik/src/main/java/pik/domain/io/IOGeneral.java` - IO initialization

---

## Conclusion

The Piknik implementation is **99% correct** but missing the **critical execution loop** that actually runs the protocol controllers. Adding a MasterLoop or using the existing ExecutorService to periodically call `run()` on the controllers will fix the initialization issue.
