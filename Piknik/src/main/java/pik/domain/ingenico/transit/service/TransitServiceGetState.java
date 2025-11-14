package pik.domain.ingenico.transit.service;

import epis5.ingenico.transit.prot.*;
import pik.domain.ingenico.IngenicoReaderDevice;

import javax.inject.Inject;

public class TransitServiceGetState extends TransitProtServiceCheckerBase {
    private static final Command COMMAND = Command.GET_STATE;
    private static final int PERIOD_MILLS = 7_000;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter outputter;

    @Inject
    public TransitServiceGetState(ITransitProtMsgOutputter outputter, IngenicoReaderDevice reader) {
        super(7_000);

        this.outputter = outputter;
        this.reader = reader;

        this.reader.getTransitApp().getTcpConnectionChanges().subscribe(e -> makeCheckPeriodElapsed());
        this.reader.getTransitApp().getMsgIdSyncedAfterErrorEvents().subscribe(e -> makeCheckPeriodElapsed());
    }

    @Override
    protected void processOnPeriodElapsed() {
        checkAppAliveInactivity();

        if (!reader.getTransitApp().isConnected()) {
            return;
        }

        if (!isConditionForRequest()) {
            return;
        }

        Payload payload = new PayloadBuilder().command(COMMAND).build();
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), 500, getProcessor());
        outputter.outputMsg(transitProtMsg);
    }

    private boolean isConditionForRequest() {
        if (!reader.getTransitApp().isAppAlive()) {
            return true;
        }
        return reader.getTransitApp().isElapsedFromLastRxMills(PERIOD_MILLS - 500);
    }

    private void checkAppAliveInactivity() {
        if (!reader.getTransitApp().isAppAlive())
            return;

        if (!reader.getTransitApp().isConnected() || reader.getTransitApp().isConditionForAppNotAlive()) {
            reader.getTransitApp().setAppNotAlive();
        }
    }

    private ITransitProtServiceProcessor getProcessor() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, COMMAND.toString(), getLogger());
                return;
            }

            if (!ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                ResponseUtils.logResponseCode(incMsg.getPayload(), COMMAND.toString(), getLogger());
                return;
            }

            TlvRecord statusRec = incMsg.getPayload().getRecord(TagType.STATUS);
            if (statusRec == null) {
                getLogger().warn("Rx payload for " + COMMAND + " has missing STATUS tag!");
                return;
            }

            int statusAsInt = statusRec.getValueAsIntLittleEndian();
            reader.getTransitApp().setTerminalStatusCode(statusAsInt);
        };
    }
}
