package pik.domain.io.control;

import com.google.inject.Injector;
import jCommons.comm.protocol.IPeriodicalChecker;

import java.util.Collection;
import java.util.LinkedList;

public class IoCtrlRegistrationBuilder {
    private final Injector injector;

    private final Collection<IPeriodicalChecker> checkers;

    public IoCtrlRegistrationBuilder(Injector injector) {
        this.injector = injector;
        this.checkers = new LinkedList<>();
    }

    public Collection<IPeriodicalChecker> getCheckers() {
        return checkers;
    }

    public IoCtrlRegistrationBuilder build() {
        checkers.clear();

        // Register Ingenico protocol services (periodic checkers)
        try {
            checkers.add(injector.getInstance(pik.domain.ingenico.IngenicoReaderInitStateMachine.class));
            checkers.add(injector.getInstance(pik.domain.ingenico.SamDukAuthStateMachine.class));
            checkers.add(injector.getInstance(pik.domain.ingenico.transit.service.TransitServiceGetState.class));
            checkers.add(injector.getInstance(pik.domain.ingenico.ifsf.service.IfsfServiceDiagnosis.class));
            checkers.add(injector.getInstance(pik.domain.ingenico.tap.IngenicoCardTappingController.class));
        } catch (Exception e) {
            // Services might not be available in dummy mode, ignore
        }

        return this;
    }
}
