package pik;

import com.google.inject.AbstractModule;
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
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.ingenico.tap.ICardTapping;
import pik.domain.ingenico.tap.IngenicoCardTappingState;
import pik.domain.ingenico.transit.IngenicoTransitApp;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 07/11/2025
 */
public class GuiceModule extends AbstractModule {
    private final ILoggerFactory loggerFactory;

    public GuiceModule(ILoggerFactory loggerFactory) {
        this.loggerFactory = loggerFactory;
    }

    @Override
    protected void configure() {
        //********************************
        //********** Ingenico ************
        //********************************
        IngenicoIfsfModuleConfig.registerLogger(loggerFactory.get(ELogger.INGENICO_IFSF));
        IngenicoTransitModuleConfig.registerLogger(loggerFactory.get(ELogger.INGENICO_TRANSIT));

        SamDuk samDuk = new SamDuk(SamType.BUS, ByteUtils.hexStringToBytes(""));
        bind(SamDuk.class).toInstance(samDuk);

        IngenicoReaderDevice ingenicoReaderDevice = new IngenicoReaderDevice(samDuk);

        bind(IngenicoReaderDevice.class).toInstance(ingenicoReaderDevice);
        bind(IngenicoIfsfApp.class).toInstance(ingenicoReaderDevice.getIfsfApp());
        bind(IIngenicoIEmvTerminal.class).toInstance(ingenicoReaderDevice.getIfsfApp());
        bind(IngenicoTransitApp.class).toInstance(ingenicoReaderDevice.getTransitApp());
        bind(IIngenicoTransitApp.class).toInstance(ingenicoReaderDevice.getTransitApp());

        bind(IfsfDeviceOutputRegister.class).toInstance(new IfsfDeviceOutputRegister());

        IngenicoCardTappingState ingenicoCardTappingState = new IngenicoCardTappingState();
        bind(ICardTapping.class).toInstance(ingenicoCardTappingState);
        bind(IngenicoCardTappingState.class).toInstance(ingenicoCardTappingState);
    }

}
