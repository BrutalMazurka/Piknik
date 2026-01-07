package pik.domain.io;

import com.google.inject.Injector;
import epis5.ingenico.transit.prot.TransitProtLayerTransformer;
import epis5.ingenicoifsf.prot.IfsfProtLayerTransformer;
import jCommons.comm.io.access.IOTcpServerAccess;
import jCommons.comm.io.sett.IOTcpServerClientSett;
import jCommons.comm.io.sett.IOTcpServerSett;
import jCommons.comm.io.tcp.LengthDataTcpProtIOMsgDataBuilder;
import jCommons.logging.ILogger;
import jCommons.logging.ILoggerFactory;
import pik.common.ELogger;
import pik.dal.IngenicoConfig;
import pik.domain.GoogleEventBus;

public class IOGeneral {
    private final ILogger appLogger;
    private final IOTcpServerAccess ifsfTcpServerAccess;
    private final IOTcpServerAccess ifsfDevProxyTcpServerAccess;
    private final IOTcpServerAccess ingenicoTransitTcpServerAccess;
    private final Object initLock = new Object();
    private volatile boolean initialized = false;

    public IOGeneral(Injector injector, IngenicoConfig ingenicoConfig) {
        ILoggerFactory loggerFactory = injector.getInstance(ILoggerFactory.class);
        appLogger = loggerFactory.get(ELogger.APP);

        int ifsfTcpServerPort = ingenicoConfig.ifsfTcpServerPort();
        IOTcpServerSett tcpServerSett = new IOTcpServerSett(ifsfTcpServerPort, 5_000, 500, 1, loggerFactory.get(ELogger.INGENICO_IFSF));
        IOTcpServerClientSett tcpServerClientSett = new IOTcpServerClientSett(66 * 1024, 5, 1, loggerFactory.get(ELogger.INGENICO_IFSF));
        tcpServerClientSett.setMonitorActivityOnTx(false);
        tcpServerClientSett.enableCloseInactiveConnection(18);
        ifsfTcpServerAccess = new IOTcpServerAccess(tcpServerClientSett,
                new LengthDataTcpProtIOMsgDataBuilder(IfsfProtLayerTransformer.DATA_LENGTH_BYTE_COUNT),
                tcpServerSett,
                new GoogleEventBus());

        int ifsfDevProxyTcpServerPort = ingenicoConfig.ifsfDevProxyTcpServerPort();
        tcpServerSett = new IOTcpServerSett(ifsfDevProxyTcpServerPort, 5_000, 500, 1, loggerFactory.get(ELogger.INGENICO_IFSF));
        tcpServerSett.setServerCodeName("DevProxyS ");
        tcpServerClientSett = new IOTcpServerClientSett(66 * 1024, 5, 20, loggerFactory.get(ELogger.INGENICO_IFSF));
        tcpServerClientSett.setMonitorActivityOnTx(false);
        tcpServerClientSett.enableCloseInactiveConnection(80);
        tcpServerClientSett.setServerCodeName("DevProxyC ");
        ifsfDevProxyTcpServerAccess = new IOTcpServerAccess(tcpServerClientSett,
                new LengthDataTcpProtIOMsgDataBuilder(IfsfProtLayerTransformer.DATA_LENGTH_BYTE_COUNT),
                tcpServerSett,
                new GoogleEventBus());

        int ingenicoTransitTcpServerPort = ingenicoConfig.transitTcpServerPort();
        tcpServerSett = new IOTcpServerSett(ingenicoTransitTcpServerPort, 5_000, 500, 1, loggerFactory.get(ELogger.INGENICO_TRANSIT));
        tcpServerClientSett = new IOTcpServerClientSett(66 * 1024, 5, 1, loggerFactory.get(ELogger.INGENICO_TRANSIT));
        tcpServerClientSett.setMonitorActivityOnTx(false);
        tcpServerClientSett.enableCloseInactiveConnection(18);
        LengthDataTcpProtIOMsgDataBuilder tcpIOMsgDataBuilder = new LengthDataTcpProtIOMsgDataBuilder(TransitProtLayerTransformer.DATA_LENGTH_BYTE_COUNT);
        tcpIOMsgDataBuilder.setLitleEndian(true);
        ingenicoTransitTcpServerAccess = new IOTcpServerAccess(tcpServerClientSett,
                tcpIOMsgDataBuilder,
                tcpServerSett,
                new GoogleEventBus());
    }

    public IOTcpServerAccess getIfsfTcpServerAccess() {
        return ifsfTcpServerAccess;
    }

    public IOTcpServerAccess getIfsfDevProxyTcpServerAccess() {
        return ifsfDevProxyTcpServerAccess;
    }

    public IOTcpServerAccess getTransitTcpServerAccess() {
        return ingenicoTransitTcpServerAccess;
    }

    public void init() {
        synchronized (initLock) {
            if (initialized) {
                appLogger.warn("IOGeneral already initialized, skipping");
                return;
            }

            ifsfTcpServerAccess.init();
            ifsfDevProxyTcpServerAccess.init();
            ingenicoTransitTcpServerAccess.init();

            initialized = true;
            appLogger.info("IOGeneral inited");
        }
    }

    public void deinit() {
        synchronized (initLock) {
            if (!initialized) {
                appLogger.warn("IOGeneral not initialized, skipping deinit");
                return;
            }

            ingenicoTransitTcpServerAccess.deinit();
            ifsfDevProxyTcpServerAccess.deinit();
            ifsfTcpServerAccess.deinit();

            initialized = false;
            appLogger.info("IOGeneral deinited");
        }
    }

}
