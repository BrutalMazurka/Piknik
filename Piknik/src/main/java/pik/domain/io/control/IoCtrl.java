package pik.domain.io.control;

import com.google.inject.Injector;
import jCommons.comm.protocol.IPeriodicalChecker;
import jCommons.comm.protocol.ProtCtrlBase;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;

public class IoCtrl extends ProtCtrlBase {
    private final ILogger logger;

    private final Injector injector;

    private IPeriodicalChecker[] checkers;

    public IoCtrl(Injector injector) {
        this.injector = injector;

        checkers = new IPeriodicalChecker[0];

        logger = LoggerFactory.getDefaultLogger();
    }

    @Override
    protected String getUniteName() {
        return "IoCtrl";
    }

    public void init(IoCtrlRegistrationBuilder registration) {
        checkers = registration.getCheckers().toArray(new IPeriodicalChecker[registration.getCheckers().size()]);

        super.init();

        logger.info("IoCtrl inited");
    }

    @Override
    public void deinit() {
        super.deinit();

        checkers = new IPeriodicalChecker[0];

        logger.info("IoCtrl deinited");
    }


    @Override
    protected void ctrlLoop() {
        while (isCtrlLoopRunning) {
            try {
                for (int i = 0; i < checkers.length; i++) {
                    checkers[i].periodicalCheck();
                }
                Thread.sleep(15);
            } catch (Exception e) {
                logger.error("IoCtrl loop", e);
            }
        }
        logger.info("IoCtrl loop finished");
    }

}
