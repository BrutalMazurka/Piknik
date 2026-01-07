package pik.domain.ingenico.transit;

import com.google.common.eventbus.Subscribe;
import epis5.ingenico.transit.IIngenicoTransitApp;
import epis5.ingenico.transit.TerminalStatus;
import epis5.ingenico.transit.sam.SamSlotEventArgs;
import epis5.ingenico.transit.sam.SamSlots;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import jCommons.comm.io.access.ITcpServerState;
import jCommons.comm.io.tcp.TcpServerClientConnectedEvent;
import jCommons.comm.io.tcp.TcpServerClientDisconnectedEvent;
import jCommons.timer.TickCounter;

import java.net.InetSocketAddress;

public class IngenicoTransitApp implements IIngenicoTransitApp {
    private static final int APP_DISCONNECTED_TIMEOUT_MILLS = 15_000;

    private final PublishSubject<IngenicoTransitEventArgs> eventBus;

    private boolean connected;
    private InetSocketAddress socketAddress;
    private final TickCounter lastRxTC;

    private boolean appAlive;
    private final TickCounter lastAppAliveTC;
    private int terminalStatusCode;
    private final SamSlots samSlots;

    public IngenicoTransitApp(PublishSubject<IngenicoTransitEventArgs> eventBus, String readerIpAddress) {
        this.eventBus = eventBus;

        connected = false;
        socketAddress = new InetSocketAddress(readerIpAddress, 2914);
        lastRxTC = TickCounter.instanceFromNow();

        appAlive = false;
        lastAppAliveTC = TickCounter.instanceFromNow();
        this.terminalStatusCode = TerminalStatus.NULL.getCode();
        this.samSlots = new SamSlots();
    }

    public Observable<IngenicoTransitEventArgs> getTcpConnectionChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoTransitEventArgs.EType.TCP_CONNECTION);
    }

    public Observable<IngenicoTransitEventArgs> getAppAliveChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoTransitEventArgs.EType.APP_ALIVE);
    }

    public Observable<IngenicoTransitEventArgs> getTerminalStatusChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoTransitEventArgs.EType.TERMINAL_STATUS);
    }

    public Observable<IngenicoTransitEventArgs> getMsgIdSyncedAfterErrorEvents() {
        return eventBus.filter(ea -> ea.getType() == IngenicoTransitEventArgs.EType.MESSAGE_ID_SYNCED_AFTER_ERROR);
    }

    public Observable<SamSlotEventArgs> getSamSlotStatusChanges() {
        return samSlots.getSlotStatusChanges();
    }

    public Observable<SamSlotEventArgs> getSamAtrChanges() {
        return samSlots.getSamAtrChanges();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public boolean isAppAlive() {
        return appAlive;
    }

    public boolean isConnectedAndAppAlive() {
        return connected && appAlive;
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }

    public void registerToTcpServer(ITcpServerState tcpServerState) {
        tcpServerState.getEventBus().register(this);
    }

    public void unregisterFromTcpServer(ITcpServerState tcpServerState) {
        tcpServerState.getEventBus().unregister(this);
    }

    @Subscribe
    public void onTcpServerClientConnectedEvent(TcpServerClientConnectedEvent ev) {
        synchronized (this) {
            socketAddress = ev.getSocketAddress();
            connected = true;
        }

        postTcpConnectionEvent();
    }

    @Subscribe
    public void onTcpServerClientDisconnectedEvent(TcpServerClientDisconnectedEvent ev) {
        synchronized (this) {
            if (connected && socketAddress.equals(ev.getSocketAddress())) {
                connected = false;
                // Reset cached state when disconnected
                setAppNotAlive();
                // Set terminalStatusCode directly to avoid side effects
                this.terminalStatusCode = TerminalStatus.NULL.getCode();
            }
        }

        postTcpConnectionEvent();
    }

    private void postTcpConnectionEvent() {
        eventBus.onNext(newEventArgs(IngenicoTransitEventArgs.EType.TCP_CONNECTION));
    }

    @Override
    public void recordActivity() {
        lastRxTC.recordNow();
    }

    @Override
    public void onMessageIdSyncedAfterError() {
        eventBus.onNext(newEventArgs(IngenicoTransitEventArgs.EType.MESSAGE_ID_SYNCED_AFTER_ERROR));
    }

    public void setAppAlive() {
        lastAppAliveTC.recordNow();

        setAppAlive(true);
    }

    public void setAppNotAlive() {
        setAppAlive(false);
    }

    private void setAppAlive(boolean alive) {
        if (this.appAlive != alive) {
            this.appAlive = alive;
            eventBus.onNext(newEventArgs(IngenicoTransitEventArgs.EType.APP_ALIVE));
        }
    }

    public boolean isConditionForAppNotAlive() {
        return lastAppAliveTC.isElapsedMills(APP_DISCONNECTED_TIMEOUT_MILLS) && lastRxTC.isElapsedMills(APP_DISCONNECTED_TIMEOUT_MILLS);
    }

    public boolean isElapsedFromLastRxMills(int mills) {
        return lastRxTC.isElapsedMills(mills);
    }

    public int getTerminalStatusCode() {
        return terminalStatusCode;
    }

    public TerminalStatus getTerminalStatus() {
        return TerminalStatus.fromCode(terminalStatusCode);
    }

    public String getTerminalStatusAsAuditLog() {
        return String.format("kod=%d, status=%s", terminalStatusCode, getTerminalStatus().toString().toLowerCase());
    }

    public void setTerminalStatusCode(int terminalStatusCode) {
        if (this.terminalStatusCode != terminalStatusCode) {
            this.terminalStatusCode = terminalStatusCode;
            eventBus.onNext(newEventArgs(IngenicoTransitEventArgs.EType.TERMINAL_STATUS));
        }

        setAppAlive();
    }

    public SamSlots getSamSlots() {
        return samSlots;
    }

    public boolean isConditionForDisconnectedTerminal(int evkAppUptimeSeconds) {
        return !appAlive && evkAppUptimeSeconds >= 90 && lastRxTC.isElapsedSec(30);
    }

    private IngenicoTransitEventArgs newEventArgs(IngenicoTransitEventArgs.EType type) {
        return new IngenicoTransitEventArgs(type, this);
    }
}
