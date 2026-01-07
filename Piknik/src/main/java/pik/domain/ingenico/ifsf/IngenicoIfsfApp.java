package pik.domain.ingenico.ifsf;

import com.google.common.eventbus.Subscribe;
import epis5.ingenicoifsf.EmvOfflineBufferState;
import epis5.ingenicoifsf.IIngenicoIEmvTerminal;
import epis5.ingenicoifsf.prot.xml.IfsfPrivateData;
import epis5.ingenicoifsf.prot.xml.service.DiagnosisResponseDto;
import pik.domain.ingenico.IngenicoReaderDevice;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import jCommons.comm.io.access.ITcpServerState;
import jCommons.comm.io.tcp.TcpServerClientConnectedEvent;
import jCommons.comm.io.tcp.TcpServerClientDisconnectedEvent;
import jCommons.timer.TickCounter;
import jCommons.utils.StringUtils;

import java.net.InetSocketAddress;
import java.util.Set;

public class IngenicoIfsfApp implements IIngenicoIEmvTerminal {
    public static final int CMD_DEFAULT_TIMEOUT_MS = 5_000;
    public static final int EMV_PAY_TIMEOUT_MS = 70_000;
    public static final int EMV_SEND_OFFLINE_TRANS_MS = 90_000;
    public static final int EMV_CLOSURE_TIMEOUT_MS = 90_000;
    public static final int EMV_TMS_TIMEOUT_MS = 20 * 60_000;
    public static final int EMV_DENY_LIST_TIMEOUT_MS = 12 * 60_000;

    public static final int DELAY_BETWEEN_CARD_OPER_MILLS = 200;
    public static final int DELAY_BETWEEN_SERVICE_OPER_MILLS = 2000;

    private final PublishSubject<IngenicoIfsfEventArgs> eventBus;

    private boolean connected;
    private InetSocketAddress socketAddress;
    private final TickCounter lastRxTC;

    private boolean appAlive;
    private final TickCounter lastAppAliveTC;
    private String terminalID;
    private String privateDataStr;
    private IfsfPrivateData privateData;
    private String lastStartStr;
    private EmvOfflineBufferState offlineBufferState;
    //private boolean backgroundMaintenance;

    public IngenicoIfsfApp(PublishSubject<IngenicoIfsfEventArgs> eventBus, String readerIpAddress) {
        this.eventBus = eventBus;

        connected = false;
        socketAddress = new InetSocketAddress(readerIpAddress, 2913);
        lastRxTC = TickCounter.instanceFromNow();

        appAlive = false;
        lastAppAliveTC = TickCounter.instanceFromNow();

        terminalID = "";
        privateDataStr = "";
        lastStartStr = "";
        privateData = IfsfPrivateData.NULL_INSTANCE;
        offlineBufferState = EmvOfflineBufferState.NULL_INSTANCE;

        //backgroundMaintenance = false;
    }

    public Observable<IngenicoIfsfEventArgs> getTcpConnectionChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoIfsfEventArgs.EType.TCP_CONNECTION);
    }

    public Observable<IngenicoIfsfEventArgs> getAppAliveChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoIfsfEventArgs.EType.APP_ALIVE);
    }

    public Observable<IngenicoIfsfEventArgs> getTerminalIdChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoIfsfEventArgs.EType.TERMINAL_ID);
    }

    public Observable<IngenicoIfsfEventArgs> getRestartEvents() {
        return eventBus.filter(ea -> ea.getType() == IngenicoIfsfEventArgs.EType.RESTART);
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
                setTerminalID("");
            }
        }

        postTcpConnectionEvent();
    }

    private void postTcpConnectionEvent() {
        eventBus.onNext(newEventArgs(IngenicoIfsfEventArgs.EType.TCP_CONNECTION));
    }

    @Override
    public void recordActivity() {
        lastRxTC.recordNow();
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
            eventBus.onNext(newEventArgs(IngenicoIfsfEventArgs.EType.APP_ALIVE));
        }
    }

    public boolean isElapsedFromLastAppAliveSec(int sec) {
        return lastAppAliveTC.isElapsedSec(sec);
    }

    public boolean isAnyTerminalID() {
        return !StringUtils.isNullOrEmpty(terminalID);
    }

    public String getTerminalID() {
        return terminalID;
    }

    public void setTerminalID(String termID) {
        if (!this.terminalID.equals(termID)) {
            this.terminalID = termID;
            eventBus.onNext(newEventArgs(IngenicoIfsfEventArgs.EType.TERMINAL_ID));
        }
    }

    public void updatePrivateData(DiagnosisResponseDto dto) {
        if (privateDataStr.equals(dto.getPrivateDataStr())) {
            return;
        }

        IfsfPrivateData newPrivateData = dto.asPrivateData();
        Set<String> diffKeys = privateData.getDiffKeys(newPrivateData);

        privateDataStr = dto.getPrivateDataStr();
        privateData = newPrivateData;

        //eventBus.post(new EmvTerminalPrivateDataEvent(this, diffKeys, privateData));

        if (!lastStartStr.equals(privateData.get(IfsfPrivateData.KEY_LASTSTART))) {
            boolean post = !StringUtils.isNullOrBlank(lastStartStr);
            lastStartStr = privateData.get(IfsfPrivateData.KEY_LASTSTART);
            if (post) {
                eventBus.onNext(newEventArgs(IngenicoIfsfEventArgs.EType.RESTART));
            }
        }
    }

    public IfsfPrivateData getPrivateData() {
        return privateData;
    }

    public EmvOfflineBufferState getOfflineBufferState() {
        return offlineBufferState;
    }

    public void setOfflineBufferState(EmvOfflineBufferState offlineBufferState) {
        this.offlineBufferState = offlineBufferState;
    }

    /*public boolean isBackgroundMaintenance() {
        return backgroundMaintenance;
    }

    public void setBackgroundMaintenance(boolean progress) {
        if (this.backgroundMaintenance != progress) {
            this.backgroundMaintenance = progress;
            //TODO
            //eventBus.post(new EmvTerminalBgMaintenanceEvent(this));
        }
    }*/

    private IngenicoIfsfEventArgs newEventArgs(IngenicoIfsfEventArgs.EType type) {
        return new IngenicoIfsfEventArgs(type, this);
    }
}
