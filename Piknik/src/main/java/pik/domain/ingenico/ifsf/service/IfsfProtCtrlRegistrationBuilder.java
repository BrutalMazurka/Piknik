package pik.domain.ingenico.ifsf.service;

import com.google.inject.Injector;
import epis5.ingenicoifsf.prot.*;
import jCommons.comm.protocol.IPeriodicalChecker;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class IfsfProtCtrlRegistrationBuilder {
    private final Injector injector;

    private final Collection<IPeriodicalChecker> checkers;
    private final Map<IIfsfProtService, IIfsfProtServiceProcessor> processors;

    public IfsfProtCtrlRegistrationBuilder(Injector injector) {
        this.injector = injector;

        checkers = new LinkedList<>();
        processors = new HashMap<>();
    }

    public IfsfProtCtrlRegistration build() {
        for (EIfsfProtService service : EIfsfProtService.values()) {
            IfsfProtMsg.SERVICES.register(service);
        }

        registerBoth(EIfsfProtService.DIAGNOSIS, injector.getInstance(IfsfServiceDiagnosis.class));

        registerProcessor(EIfsfProtService.DEVICE_OUTPUT, injector.getInstance(IfsfServiceDeviceOutputProcessor.class));

        return new IfsfProtCtrlRegistration(checkers, processors);
    }

    private void registerChecker(IPeriodicalChecker checker) {
        checkers.add(checker);
    }

    private void registerProcessor(IIfsfProtService service, IIfsfProtServiceProcessor processor) {
        processors.put(service, processor);
    }

    private void registerBoth(IIfsfProtService service, IfsfProtServiceBase serviceBase) {
        checkers.add(serviceBase);
        processors.put(service, serviceBase);
    }
}
