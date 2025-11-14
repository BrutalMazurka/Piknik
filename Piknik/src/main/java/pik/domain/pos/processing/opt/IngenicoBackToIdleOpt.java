package pik.domain.pos.processing.opt;

import epis.commons.i18n.I18n;
import epis5.ingenico.transit.TerminalStatus;
import epis5.ingenico.transit.prot.*;
import epis5.pos.processing.opt.OptBase;
import pik.common.ELogger;
import pik.domain.ingenico.IngenicoReaderDevice;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import jCommons.timer.TickCounter;
import jCommons.utils.ThreadUtils;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IngenicoBackToIdleOpt extends OptBase {
    private static final int EXTRA_DELAY_AFTER_BACK_TO_IDLE_MILLS = 120;
    private static final int RESPONSE_TIMEOUT_MILLS = 1_000;
    private static final int DEFAULT_TASK_TIMEOUT_MILLS = 5_000;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter outputter;
    private final TickCounter taskTimeoutTc;
    private int taskTimeoutMills;
    private boolean extraDelayAfterBackToIdle;
    private TerminalStatus lastTerminalStatus;

    @Inject
    public IngenicoBackToIdleOpt(IngenicoReaderDevice reader, ITransitProtMsgOutputter outputter) {
        this.reader = reader;
        this.outputter = outputter;

        this.taskTimeoutTc = TickCounter.instanceFromNow();
        this.taskTimeoutMills = DEFAULT_TASK_TIMEOUT_MILLS;
        this.extraDelayAfterBackToIdle = false;
        this.lastTerminalStatus = TerminalStatus.NULL;
    }

    public void setTaskTimeoutMills(int taskTimeoutMills) {
        this.taskTimeoutMills = taskTimeoutMills;
    }

    @Override
    protected void executeTask() {
        taskTimeoutTc.recordNow();

        sendBackToIdle();

        if (isTaskError()) {
            return;
        }

        if (extraDelayAfterBackToIdle) {
            ThreadUtils.sleepIgnoreExc(EXTRA_DELAY_AFTER_BACK_TO_IDLE_MILLS);
        }

        while (true) {
            if (verifyStatusIdle()) {
                setResultOk();
                return;
            }

            if (isTaskError()) {
                return;
            }

            if (isTaskTimeout()) {
                //TODO i18n
                setResultError("Platební terminál nelze převést do módu pro platební karty.");
                return;
            }
        }
    }

    private void sendBackToIdle() {
        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        Payload payload = new PayloadBuilder().command(Command.BACK_TO_IDLE).build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, new ITransitProtServiceProcessor() {
            @Override
            public void process(TransitProtMsg writtenMsg, TransitProtMsg incMsg) {
                if (incMsg == null) {
                    getLogger().warn("IngenicoBackToIdleOpt - BACK_TO_IDLE: no response (null incMsg) from transit app");
                    return;
                }
                try {
                    if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                        //Pokud terminal odpovi OK na back to idle, znamena to, ze zahaji proces prevadeni do IDLE rezimu a to chvili trva
                        //Pokud je jiz preveden do IDLE modu, odpovi:  0xF4 ERR_CMD_EXEC Obecné selhání příkazu
                        extraDelayAfterBackToIdle = true;
                    }
                } catch (Exception e) {
                    setResultError("Chyba při zpracování karty: " + e.getMessage());
                    getLogger().error("IngenicoBackToIdleOpt callback error", e);
                } finally {
                    deliveredSignal.countDown();
                }
            }
        });
        outputter.outputMsg(transitProtMsg);

        boolean deliverSignalOk;
        try {
            deliverSignalOk = deliveredSignal.await(RESPONSE_TIMEOUT_MILLS + 300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            getLogger().warn("MonetTapPollOpt", e);
            setResultError("Chyba při zpracování karty: čtečka neodpovídá na povel (back-to-idle)");
            return;
        }

        if (!deliverSignalOk) {
            setResultError("Chyba při zpracování karty: čtečka neodpovídá na povel (back-to-idle)");
            return;
        }
    }

    private boolean verifyStatusIdle() {
        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        Payload payload = new PayloadBuilder().command(Command.GET_STATE).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, new ITransitProtServiceProcessor() {
            @Override
            public void process(TransitProtMsg writtenMsg, TransitProtMsg incMsg) {
                if (incMsg == null) {
                    getLogger().warn("IngenicoBackToIdleOpt - GET_STATE: no response (null incMsg) from transit app");
                    return;
                }
                try {
                    if (!ResponseUtils.isValidResponse(incMsg)) {
                        ResponseUtils.logInvalidResponse(writtenMsg, incMsg, Command.GET_STATE.toString(), getIngenicoTransitLogger());
                        return;
                    }

                    if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                        ResponseUtils.logResponseCode(incMsg.getPayload(), Command.GET_STATE.toString(), getIngenicoTransitLogger());
                        return;
                    }

                    TlvRecord statusRec = incMsg.getPayload().getRecord(TagType.STATUS);
                    if (statusRec == null) {
                        getIngenicoTransitLogger().warn("Rx payload for " + Command.GET_STATE + " has missing STATUS tag!");
                        return;
                    }

                    int statusAsInt = statusRec.getValueAsIntLittleEndian();
                    reader.getTransitApp().setTerminalStatusCode(statusAsInt);
                    lastTerminalStatus = TerminalStatus.fromCode(statusAsInt);
                } catch (Exception e) {
                    setResultError(I18n.get("evk_err_msg_emv_tap_poll", "Chyba při platbě:") + " " + e.getMessage());
                    getLogger().error("IngenicoBackToIdleOpt callback error", e);
                } finally {
                    deliveredSignal.countDown();
                }
            }
        });
        outputter.outputMsg(transitProtMsg);

        try {
            boolean signalOk = deliveredSignal.await(RESPONSE_TIMEOUT_MILLS + 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            getLogger().warn("MonetTapPollOpt", e);
        }

        return lastTerminalStatus == TerminalStatus.IDLE;
    }

    private boolean isTaskError() {
        return isResultError() && !isResultDescriptionNotApplicable();
    }

    private boolean isTaskTimeout() {
        return taskTimeoutTc.isElapsedMills(taskTimeoutMills);
    }

    private ILogger getIngenicoTransitLogger() {
        return LoggerFactory.get(ELogger.INGENICO_TRANSIT);
    }
}
