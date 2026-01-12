package pik.domain.ingenico;

import epis5.duk.bck.core.sam.SamDuk;
import epis5.duk.bck.core.sam.SamType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import jCommons.timer.TickCounter;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.ingenico.ifsf.IngenicoIfsfEventArgs;
import pik.domain.ingenico.transit.IngenicoTransitApp;
import pik.domain.ingenico.transit.IngenicoTransitEventArgs;

public class IngenicoReaderDevice {
    private final PublishSubject<IngenicoIfsfEventArgs> ifsfEventBus;
    private final PublishSubject<IngenicoTransitEventArgs> transitEventBus;
    private final PublishSubject<IngenicoEventArgs> eventBus;
    private final IngenicoIfsfApp ifsfApp;
    private final IngenicoTransitApp transitApp;
    private final SamDuk samDuk;

    private volatile EReaderInitState initState;
    private final TickCounter lastInitStateChangedTc;

    // Store the actual SAM type found in the module during authentication
    private volatile SamType foundSamType;

    //Pokud se ctecka restartne (napr. TMS) a aplikace EVK bezi - zacne ctecka odpovidat na zakladni prikazy (GET_INFO),
    // ale na stav SAM modulu vraci error
    // Aplikace EVK musi dat ctece nejaky cas aby si inicializovala SAM rozhranni
    private volatile boolean extraDelayOnInit;

    public IngenicoReaderDevice(SamDuk samDuk, String readerIpAddress) {
        this.samDuk = samDuk;
        this.ifsfEventBus = PublishSubject.create();
        this.transitEventBus = PublishSubject.create();
        this.eventBus = PublishSubject.create();

        this.initState = EReaderInitState.STARTING;
        this.extraDelayOnInit = false;
        this.foundSamType = null;
        this.lastInitStateChangedTc = TickCounter.instanceFromNow();

        this.ifsfApp = new IngenicoIfsfApp(this.ifsfEventBus, readerIpAddress);
        this.transitApp = new IngenicoTransitApp(this.transitEventBus, readerIpAddress);
    }

    public Observable<IngenicoEventArgs> getInitStateChanges() {
        return eventBus.filter(ea -> ea.getType() == IngenicoEventArgs.EType.INIT_STATE);
    }

    public IngenicoIfsfApp getIfsfApp() {
        return ifsfApp;
    }

    public IngenicoTransitApp getTransitApp() {
        return transitApp;
    }

    public EReaderInitState getInitStatus() {
        return initState;
    }

    public boolean isInitStatusDone() {
        return initState == EReaderInitState.DONE;
    }

    public void setExtraDelayOnInit(boolean extraDelayOnInit) {
        this.extraDelayOnInit = extraDelayOnInit;
    }

    public void setInitState(EReaderInitState initState) {
        if (this.initState != initState) {
            lastInitStateChangedTc.recordNow();
            this.initState = initState;

            if (this.initState == EReaderInitState.STARTING) {
                samDuk.getAuth().restart();
            }

            eventBus.onNext(new IngenicoEventArgs(IngenicoEventArgs.EType.INIT_STATE, this));
        }
    }

    public boolean isExtraDelayElapsed() {
        if (!extraDelayOnInit) {
            return true;
        }
        return isElapsedFromLastInitStateChangedMills(5_000);
    }

    private boolean isElapsedFromLastInitStateChangedMills(int mills) {
        return lastInitStateChangedTc.isElapsedMills(mills);
    }

    public SamDuk getSamDuk() {
        return samDuk;
    }

    public SamType getFoundSamType() {
        return foundSamType;
    }

    public void setFoundSamType(SamType foundSamType) {
        this.foundSamType = foundSamType;
    }
}
