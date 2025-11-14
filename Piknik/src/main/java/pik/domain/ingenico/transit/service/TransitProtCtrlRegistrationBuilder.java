package pik.domain.ingenico.transit.service;

import com.google.inject.Injector;
import epis5.ingenico.transit.prot.TransitProtCtrlRegistration;
import pik.domain.ingenico.IngenicoReaderInitStateMachine;
import pik.domain.ingenico.SamDukAuthStateMachine;
import pik.domain.ingenico.tap.IngenicoCardTappingController;
import jCommons.comm.protocol.IPeriodicalChecker;

import java.util.Collection;
import java.util.LinkedList;

public class TransitProtCtrlRegistrationBuilder {
    private final Injector injector;

    private final Collection<IPeriodicalChecker> checkers;

    public TransitProtCtrlRegistrationBuilder(Injector injector) {
        this.injector = injector;

        checkers = new LinkedList<>();
    }

    public TransitProtCtrlRegistration build() {
        registerChecker(injector.getInstance(IngenicoReaderInitStateMachine.class));
        registerChecker(injector.getInstance(SamDukAuthStateMachine.class));
        registerChecker(injector.getInstance(IngenicoCardTappingController.class));

        registerChecker(injector.getInstance(TransitServiceGetState.class));

        return new TransitProtCtrlRegistration(checkers);
    }

    private void registerChecker(IPeriodicalChecker checker) {
        checkers.add(checker);
    }
}
