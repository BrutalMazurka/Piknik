package pik.domain.ingenico;

import epis5.ingenico.transit.prot.ITransitProtMsgOutputter;
import epis5.ingenicoifsf.EmvOfflineBufferState;
import epis5.ingenicoifsf.EmvUpdateSchema;
import epis5.ingenicoifsf.prot.IIfsfProtMsgOutputter;
import epis5.ingenicoifsf.prot.IfsfProtMsg;
import epis5.ingenicoifsf.prot.xml.IfsfPrivateData;
import epis5.ingenicoifsf.prot.xml.IfsfProtRxDtoBase;
import epis5.ingenicoifsf.prot.xml.device.DeviceOutputRequestDto;
import epis5.ingenicoifsf.prot.xml.service.*;
import epis5.ingenicoifsf.proxy.IIfsfDeviceOutputHandler;
import epis5.ingenicoifsf.proxy.IfsfDeviceOutputRegister;
import pik.dal.EmvPropertyFile;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;
import pik.domain.pos.processing.opt.IngenicoBackToIdleOpt;
import jCommons.comm.protocol.xml.DtoXmlReader;
import jCommons.comm.protocol.xml.DtoXmlWriter;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import jCommons.timer.TickCounter;
import jCommons.update.task.UpdateTask;
import jCommons.update.task.UpdateTaskResult;
import jCommons.utils.ThreadUtils;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IngenicoUpdateTask extends UpdateTask implements IIfsfDeviceOutputHandler {
    private final IngenicoReaderDevice readerDevice;
    private final IIfsfProtMsgOutputter outputter;
    private final ITransitProtMsgOutputter trasitOutputter;
    private EmvUpdateSchema schema;
    private final Map<EmvUpdateSchema.ESubTask, String> subTaskResults;
    private final Map<EmvUpdateSchema.ESubTask, IfsfProtRxDtoBase> dtoResponses;
    private final ILogger logger;
    private final DtoXmlWriter dtoXmlWriter;
    private final DtoXmlReader dtoXmlReader;
    private String lastActionName;
    private boolean finished;
    private IfsfDeviceOutputRegister deviceOutputRegister;
    private boolean registerToDeviceOutput;
    private boolean waitForTerminalConnection;
    private int terminalConnectionTimeoutSec;

    @Inject
    public IngenicoUpdateTask(IngenicoReaderDevice readerDevice, IIfsfProtMsgOutputter outputter, ITransitProtMsgOutputter trasitOutputter) {
        super("EMV terminal", 25 * 60_000);

        this.readerDevice = readerDevice;
        this.outputter = outputter;
        this.trasitOutputter = trasitOutputter;

        dtoXmlWriter = new DtoXmlWriter();
        dtoXmlReader = new DtoXmlReader();

        subTaskResults = new TreeMap<>();
        dtoResponses = new HashMap<>();

        this.schema = null;
        this.deviceOutputRegister = null;
        registerToDeviceOutput = false;
        lastActionName = "";
        finished = false;
        waitForTerminalConnection = false;
        terminalConnectionTimeoutSec = 30;
        logger = LoggerFactory.getUpdaterLogger();
    }

    public void setSchema(EmvUpdateSchema schema) {
        this.schema = schema;

        subTaskResults.clear();
        for (EmvUpdateSchema.ESubTask subTask : schema.getAll()) {
            subTaskResults.put(subTask, "N-A");
        }
    }

    public void setRegisterToDeviceOutput(IfsfDeviceOutputRegister deviceOutputRegister) {
        this.registerToDeviceOutput = true;
        this.deviceOutputRegister = deviceOutputRegister;
    }

    public void setWaitForTerminalConnection(int timeoutSec) {
        this.waitForTerminalConnection = true;
        this.terminalConnectionTimeoutSec = timeoutSec;
    }

    @Override
    public void cancel() {
        finished = true;
    }

    @Override
    protected void execute() {
        try {
            if (registerToDeviceOutput && deviceOutputRegister != null) {
                deviceOutputRegister.register(this);
            }

            if (schema == null) {
                setFinishWithError(13, "Preruseno, schema == null");
                return;
            }

            if (waitForTerminalConnection) {
                onNewAction("Čekání na spojení s platebním terminálem");
                waitForTerminalConnection();
            }

            onNewAction("Aktualizace platebního terminálu");

            if (!readerDevice.getIfsfApp().isConnectedAndAppAlive()) {
                setFinishWithError(15, "Platební terminál nepřipojen");
                return;
            }

            waitForInitAndAuthFinished();
            if (!isInitAndAuthFinished()) {
                setFinishWithError(16, "Nedokončena inicializace a autorizace");
                return;
            }

            onNewAction("Čekání na ukončení režimu skenování karty");
            backToIdle();

            EmvUpdateSchema.ESubTask subTask = EmvUpdateSchema.ESubTask.SEND_OFFLINE_TRANSACTION;
            if (!finished && schema.isRegistered(subTask)) {
                onNewAction("probíhá odesílání off-line transakcí");
                sendOfflineTransactions(subTask);
            }

            subTask = EmvUpdateSchema.ESubTask.CLOSURE;
            if (!finished && schema.isRegistered(subTask)) {
                onNewAction("probíhá uzávěrka");
                closure(subTask);
            }

            subTask = EmvUpdateSchema.ESubTask.TMS_CALL;
            if (!finished && schema.isRegistered(subTask)) {
                onNewAction("probíhá aktualizace platebního terminálu - volání TMS (Terminal Management System)");
                tms(subTask);
            }

            subTask = EmvUpdateSchema.ESubTask.DENY_LIST;
            if (!finished && schema.isRegistered(subTask)) {
                onNewAction("probíhá stažení stop list (deny list)");
                denyList(subTask);
            }

            if (!finished) {
                setDoneOk();
            }

        } catch (Exception e) {
            setSpec(50, e.toString());
            LoggerFactory.getUpdaterLogger().error("Executing " + getName(), e);
        } finally {
            if (registerToDeviceOutput && deviceOutputRegister != null) {
                deviceOutputRegister.unregister(this);
            }
        }
    }

    private void onNewAction(String actionName) {
        lastActionName = actionName;
        setActionName(lastActionName);
    }

    private void waitForTerminalConnection() {
        final TickCounter timeoutTc = TickCounter.instanceFromNow();
        while (!readerDevice.getIfsfApp().isConnectedAndAppAlive()) {
            if (timeoutTc.isElapsedSec(terminalConnectionTimeoutSec)) {
                return;
            }
            ThreadUtils.sleepIgnoreExc(1000);
        }
    }

    private void waitForInitAndAuthFinished() {
        final TickCounter timeoutTc = TickCounter.instanceFromNow();
        while (!isInitAndAuthFinished()) {
            if (timeoutTc.isElapsedSec(20_000)) {
                return;
            }
            ThreadUtils.sleepIgnoreExc(1000);
        }
    }

    private boolean isInitAndAuthFinished() {
        return readerDevice.isInitStatusDone() && readerDevice.getSamDuk().getAuth().isProcessStateFinished();
    }

    private void backToIdle() {
        IngenicoBackToIdleOpt backToIdleOpt = new IngenicoBackToIdleOpt(readerDevice, trasitOutputter);
        backToIdleOpt.execute();

        if (backToIdleOpt.isResultError()) {
            logger.warn("IngenicoUpdateTask - IngenicoBackToIdleOpt error: " + backToIdleOpt.getResultDescription());
        }
    }

    @Override
    public void processDeviceOutput(DeviceOutputRequestDto dto) {
        if (finished) {
            return;
        }
        setActionName(dto.getTextAsHtml(lastActionName));
    }

    private void sendOfflineTransactions(final EmvUpdateSchema.ESubTask subTask) {
        logSubTaskStarted(subTask);

        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        SendOfflineTransRequestDto dtoReq = new SendOfflineTransRequestDto();

        IfsfProtMsg protMsg = IfsfProtMsg.createRequest(dtoReq, dtoXmlWriter.getBytes(dtoReq), readerDevice.getIfsfApp().getSocketAddress(),
                IngenicoIfsfApp.EMV_SEND_OFFLINE_TRANS_MS, (IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) ->
                {
                    if (incMsg == null)
                        return;

                    try {
                        SendOfflineTransResponseDto respDto = dtoXmlReader.parse(incMsg.getData(), SendOfflineTransResponseDto.getParser());
                        dtoResponses.put(subTask, respDto);
                    } catch (Exception exc) {
                        setFinishWithError(subTask, exc.getMessage());
                    } finally {
                        deliveredSignal.countDown();
                    }
                });
        outputter.outputMsg(protMsg);

        try {
            boolean signalOk = deliveredSignal.await(IngenicoIfsfApp.EMV_SEND_OFFLINE_TRANS_MS + 1000, TimeUnit.MILLISECONDS);
            if (!signalOk) {
                setFinishWithError(subTask, "time-out");
            }
        } catch (InterruptedException e) {
            setFinishWithError(subTask, e.getMessage());
        }

        if (!finished) {
            IfsfProtRxDtoBase respDto = dtoResponses.get(subTask);
            if (respDto == null) {
                setFinishWithError(subTask, "neplatna odpoved");
            } else if (!respDto.isOverallResultSuccess()) {
                setFinishWithError(subTask, respDto.getOverallResult());
            } else {
                subTaskResults.put(subTask, "OK");
                readerDevice.getIfsfApp().setOfflineBufferState(EmvOfflineBufferState.NULL_INSTANCE);
            }
        }
    }

    private void closure(EmvUpdateSchema.ESubTask subTask) {
        logSubTaskStarted(subTask);

        //umela prodleva - jinak ctecka vraci Busy, po zmene fw ctecky odstranit
        ThreadUtils.sleepIgnoreExc(IngenicoIfsfApp.DELAY_BETWEEN_SERVICE_OPER_MILLS);

        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        ReconciliationClosureRequestDto dtoReq = new ReconciliationClosureRequestDto();

        IfsfProtMsg protMsg = IfsfProtMsg.createRequest(dtoReq, dtoXmlWriter.getBytes(dtoReq), readerDevice.getIfsfApp().getSocketAddress(),
                IngenicoIfsfApp.EMV_CLOSURE_TIMEOUT_MS, (IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) ->
                {
                    if (incMsg == null)
                        return;

                    try {
                        ReconciliationClosureResponseDto respDto = dtoXmlReader.parse(incMsg.getData(), ReconciliationClosureResponseDto.getParser());
                        dtoResponses.put(subTask, respDto);
                    } catch (Exception exc) {
                        setFinishWithError(subTask, exc.getMessage());
                    } finally {
                        deliveredSignal.countDown();
                    }
                });
        outputter.outputMsg(protMsg);

        try {
            boolean signalOk = deliveredSignal.await(IngenicoIfsfApp.EMV_CLOSURE_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
            if (!signalOk) {
                setFinishWithError(subTask, "time-out");
            }
        } catch (InterruptedException e) {
            setFinishWithError(subTask, e.getMessage());
        }

        if (!finished) {
            IfsfProtRxDtoBase respDto = dtoResponses.get(subTask);
            if (respDto == null) {
                setFinishWithError(subTask, "neplatna odpoved");
            } else if (!respDto.isOverallResultSuccess()) {
                setFinishWithError(subTask, respDto.getOverallResult());
            } else {
                subTaskResults.put(subTask, "OK");
            }
        }
    }

    private void tms(EmvUpdateSchema.ESubTask subTask) {
        logSubTaskStarted(subTask);

        //umela prodleva - jinak ctecka vraci Busy, po zmene fw ctecky odstranit
        ThreadUtils.sleepIgnoreExc(IngenicoIfsfApp.DELAY_BETWEEN_SERVICE_OPER_MILLS);

        logTmsParams("start");

        final String startTms = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMS);
        final String startAC = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMSAC);
        final String startLastTmsStart = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMSSTART);
        final TickCounter timeoutTc = TickCounter.instanceFromNow();

        final CountDownLatch deliveredSignal = new CountDownLatch(1);
        final int requestTimeoutMill = 5_000;

        OnlineAgentRequestDto dtoReq = new OnlineAgentRequestDto();

        IfsfProtMsg protMsg = IfsfProtMsg.createRequest(dtoReq, dtoXmlWriter.getBytes(dtoReq), readerDevice.getIfsfApp().getSocketAddress(),
                requestTimeoutMill, (IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) ->
                {
                    if (incMsg == null)
                        return;

                    try {
                        OnlineAgentResponseDto respDto = dtoXmlReader.parse(incMsg.getData(), OnlineAgentResponseDto.getParser());
                        dtoResponses.put(subTask, respDto);
                    } catch (Exception exc) {
                        setFinishWithError(subTask, exc.getMessage());
                    } finally {
                        deliveredSignal.countDown();
                    }
                });
        outputter.outputMsg(protMsg);

        try {
            boolean signalOk = deliveredSignal.await(requestTimeoutMill + 1000, TimeUnit.MILLISECONDS);
            if (!signalOk) {
                setFinishWithError(subTask, "time-out zadost");
            }
        } catch (InterruptedException e) {
            setFinishWithError(subTask, e.getMessage());
        }

        if (!finished) {
            IfsfProtRxDtoBase respDto = dtoResponses.get(subTask);
            if (respDto == null) {
                setFinishWithError(subTask, "neplatna odpoved");
            } else if (!respDto.isOverallResultSuccess()) {
                setFinishWithError(subTask, respDto.getOverallResult());
            }
        }
        if (finished) {
            return;
        }

        boolean operationFinished = false;
        while (!operationFinished) {
            if (timeoutTc.isElapsedMills(IngenicoIfsfApp.EMV_TMS_TIMEOUT_MS)) {
                operationFinished = true;
                setFinishWithError(subTask, "time-out dokonceni");
            } else if (readerDevice.getIfsfApp().isConnectedAndAppAlive()) {
                String actualTms = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMS);
                String actualAC = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMSAC);
                String actualLastTmsStart = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMSSTART);
                if (!startTms.equals(actualTms) || !startAC.equals(actualAC) || !startLastTmsStart.equals(actualLastTmsStart)) {
                    operationFinished = true;
                }
            }

            if (!operationFinished) {
                ThreadUtils.sleepIgnoreExc(2_000);
            }
        }

        logTmsParams("finished");

        if (!finished) {
            if (!startTms.equals(readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMS))) {
                subTaskResults.put(subTask, "OK");
                EmvPropertyFile.saveLastTms(DateTime.now());
            } else {
                setFinishWithError(subTask, readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTTMSAC));
            }
        }
    }

    private void denyList(EmvUpdateSchema.ESubTask subTask) {
        logSubTaskStarted(subTask);

        //umela prodleva - jinak ctecka vraci Busy, po zmene fw ctecky odstranit
        ThreadUtils.sleepIgnoreExc(IngenicoIfsfApp.DELAY_BETWEEN_SERVICE_OPER_MILLS);

        final String startVF = readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTDENYL);

        logDenyListParams("start");

        final CountDownLatch deliveredSignal = new CountDownLatch(1);
        final int requestTimeoutMill = IngenicoIfsfApp.EMV_DENY_LIST_TIMEOUT_MS;

        OnlineAgentRequestDto dtoReq = new OnlineAgentRequestDto();
        dtoReq.setAgentDenyList();

        IfsfProtMsg protMsg = IfsfProtMsg.createRequest(dtoReq, dtoXmlWriter.getBytes(dtoReq), readerDevice.getIfsfApp().getSocketAddress(),
                requestTimeoutMill, (IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) ->
                {
                    if (incMsg == null) {
                        return;
                    }

                    try {
                        OnlineAgentResponseDto respDto = dtoXmlReader.parse(incMsg.getData(), OnlineAgentResponseDto.getParser());
                        dtoResponses.put(subTask, respDto);

                        if (!respDto.isOverallResultSuccess()) {
                            setFinishWithError(subTask, "OverallResult=" + respDto.getOverallResult());
                        }
                    } catch (Exception exc) {
                        setFinishWithError(subTask, exc.getMessage());
                    } finally {
                        deliveredSignal.countDown();
                    }
                });
        outputter.outputMsg(protMsg);

        try {
            boolean signalOk = deliveredSignal.await(requestTimeoutMill + 1000, TimeUnit.MILLISECONDS);
            if (!signalOk) {
                setFinishWithError(subTask, "time-out zadost");
            }
        } catch (InterruptedException e) {
            setFinishWithError(subTask, e.getMessage());
        }

        if (finished) {
            //preruseno
            return;
        }

        /*
            je chování čtečky jiné než u TMS volání ( Agent - DownloadParams) ?  Byť jde o stejnou službu OnlineAgent ....

            Ano, jen po TMS nejsme schopni zajistit finální response do pokladny, proto tam indikujeme jen start, nikoliv dokončení.
            U ostatních služeb včetně denylistu, které využíváte jsme schopni indikovat dokončení.
        */

        logDenyListParams("finished");

        if (!startVF.equals(readerDevice.getIfsfApp().getPrivateData().get(IfsfPrivateData.KEY_LASTDENYL))) {
            subTaskResults.put(subTask, "OK");
        } else {
            //pokud neni na serveru novy stop list nestahne se a polozka se naktualizuje
            subTaskResults.put(subTask, "neni novejsi DENYL");
        }
    }

    private void logSubTaskStarted(EmvUpdateSchema.ESubTask subTask) {
        logger.infoFormat("IngenicoUpdateTask: started sub-task: %s", subTask.getDescription());
    }

    private void setFinishWithError(EmvUpdateSchema.ESubTask subTask, String description) {
        subTaskResults.put(subTask, description);
        setFinishWithError(20, subTask.getDescription() + ": " + description);
    }

    private void setFinishWithError(int specCode, String description) {
        finished = true;
        setSpec(specCode, description);
        logger.warnFormat("IngenicoUpdateTask: finished with error: %s, code: %d", description, specCode);
    }

    private void setDoneOk() {
        finished = true;
        setSpecCode(UpdateTaskResult.DONE.getCode());
        String overview = getOverview();
        setSpecTxt("OK, dokonceno: " + overview);
        logger.info("IngenicoUpdateTask: finished OK [" + overview + "]");
    }

    private String getOverview() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EmvUpdateSchema.ESubTask, String> entry : subTaskResults.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey().getDescription()).append("=\"").append(entry.getValue()).append("\"");
        }
        return sb.toString();
    }

    private void logTmsParams(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("EMV TMS params - ").append(title).append(": ");

        String[] keys = {IfsfPrivateData.KEY_LASTTMS, IfsfPrivateData.KEY_LASTTMSAC, IfsfPrivateData.KEY_LASTTMSSTART};
        for (String key : keys) {
            sb.append(key).append("=").append(readerDevice.getIfsfApp().getPrivateData().get(key));
            sb.append(" | ");
        }
        logger.info(sb);
    }

    private void logDenyListParams(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("EMV DenyList params - ").append(title).append(": ");

        String key = IfsfPrivateData.KEY_LASTDENYL;
        sb.append(key).append("=").append(readerDevice.getIfsfApp().getPrivateData().get(key));

        logger.info(sb);
    }
}
