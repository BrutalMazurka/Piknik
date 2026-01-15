package pik;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import epis5.duk.bck.core.sam.SamDuk;
import epis5.duk.bck.core.sam.SamType;
import epis5.ingenico.transit.IIngenicoTransitApp;
import epis5.ingenico.transit.IngenicoTransitModuleConfig;
import epis5.ingenicoifsf.IIngenicoIEmvTerminal;
import epis5.ingenicoifsf.IngenicoIfsfModuleConfig;
import epis5.ingenicoifsf.proxy.IfsfDeviceOutputRegister;
import jCommons.logging.ILoggerFactory;
import jCommons.utils.ByteUtils;
import pik.common.ELogger;
import pik.dal.IngenicoConfig;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.ingenico.tap.ICardTapping;
import pik.domain.ingenico.tap.IngenicoCardTappingState;
import pik.domain.ingenico.transit.IngenicoTransitApp;
import pik.domain.ingenico.unlock.SamUnlockOrchestrator;
import pik.domain.ingenico.unlock.UnlockSessionManager;
import pik.domain.io.IOGeneral;
import pik.domain.pos.IPosDisplayService;
import pik.domain.pos.NoOpDisplayService;

import javax.inject.Singleton;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 07/11/2025
 */
public class GuiceModule extends AbstractModule {
    private final ILoggerFactory loggerFactory;
    private final IngenicoConfig ingenicoConfig;

    public GuiceModule(ILoggerFactory loggerFactory, IngenicoConfig ingenicoConfig) {
        this.loggerFactory = loggerFactory;
        this.ingenicoConfig = ingenicoConfig;
    }

    @Override
    protected void configure() {
        // Bind the logger factory so it can be injected
        bind(ILoggerFactory.class).toInstance(loggerFactory);

        //********************************
        //********** Ingenico ************
        //********************************
        IngenicoIfsfModuleConfig.registerLogger(loggerFactory.get(ELogger.INGENICO_IFSF));
        IngenicoTransitModuleConfig.registerLogger(loggerFactory.get(ELogger.INGENICO_TRANSIT));

        // TODO: Use SamType.CM when develop branch dependency is available
        // Temporarily using BUS until epis5-duk-bck-core is updated with CM type
        SamDuk samDuk = new SamDuk(SamType.CM, ByteUtils.hexStringToBytes("EF 67 AB 64 52 E6 1A 32 2A 9E 0E 15 8A 04 29 C4"));
        bind(SamDuk.class).toInstance(samDuk);

        IngenicoReaderDevice ingenicoReaderDevice = new IngenicoReaderDevice(samDuk, ingenicoConfig.readerIpAddress());

        bind(IngenicoReaderDevice.class).toInstance(ingenicoReaderDevice);
        bind(IngenicoIfsfApp.class).toInstance(ingenicoReaderDevice.getIfsfApp());
        bind(IIngenicoIEmvTerminal.class).toInstance(ingenicoReaderDevice.getIfsfApp());
        bind(IngenicoTransitApp.class).toInstance(ingenicoReaderDevice.getTransitApp());
        bind(IIngenicoTransitApp.class).toInstance(ingenicoReaderDevice.getTransitApp());

        bind(IfsfDeviceOutputRegister.class).toInstance(new IfsfDeviceOutputRegister());

        IngenicoCardTappingState ingenicoCardTappingState = new IngenicoCardTappingState();
        bind(ICardTapping.class).toInstance(ingenicoCardTappingState);
        bind(IngenicoCardTappingState.class).toInstance(ingenicoCardTappingState);

        //********************************
        //****** SAM Unlock Support ******
        //********************************
        // Display service (no-op for REST API)
        bind(IPosDisplayService.class).to(NoOpDisplayService.class).in(Singleton.class);

        // Session management
        bind(UnlockSessionManager.class).in(Singleton.class);
        bind(SamUnlockOrchestrator.class).in(Singleton.class);
    }

    /**
     * Provides IOGeneral singleton
     * Passes IngenicoConfig to avoid IOGeneral reading directly from AppConfig
     */
    @Provides
    public IOGeneral provideIOGeneral(Injector injector) {
        return new IOGeneral(injector, ingenicoConfig);
    }

}
