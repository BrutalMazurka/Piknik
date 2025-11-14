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

        return this;
    }
}
