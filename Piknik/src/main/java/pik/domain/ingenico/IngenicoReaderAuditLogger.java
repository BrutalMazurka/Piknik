package pik.domain.ingenico;

import epis.auditlogs.AuditLogAction;
import epis.commons.auditlogs.IAuditLogService;
import epis5.duk.bck.core.sam.SamDukEventArgs;
import epis5.duk.bck.core.sam.auth.AuthProcessState;
import epis5.duk.bck.core.sam.auth.AuthResult;
import epis5.ingenico.transit.sam.SamSlot;
import epis5.ingenico.transit.sam.SamSlotEventArgs;
import epis5.ingenicoifsf.prot.xml.IfsfPrivateData;
import pik.common.ELogger;
import pik.domain.auditlogs.AuditLogEventHandlerBase;
import pik.domain.ingenico.ifsf.IngenicoIfsfEventArgs;
import pik.domain.ingenico.transit.IngenicoTransitEventArgs;
import jCommons.ByteArrayFormatter;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;

import javax.inject.Inject;

public class IngenicoReaderAuditLogger extends AuditLogEventHandlerBase {
    private final ILogger ifsfLogger;
    private final ILogger transitLogger;
    private final IngenicoReaderDevice reader;

    @Inject
    public IngenicoReaderAuditLogger(IAuditLogService auditLogService, IngenicoReaderDevice reader) {
        super(auditLogService);
        this.reader = reader;

        this.ifsfLogger = LoggerFactory.get(ELogger.INGENICO_IFSF);
        this.transitLogger = LoggerFactory.get(ELogger.INGENICO_TRANSIT);
    }

    public void register() {
        this.reader.getIfsfApp().getTcpConnectionChanges().subscribe(this::onIfsfTcpConnectionChanged);
        this.reader.getIfsfApp().getAppAliveChanges().subscribe(this::onIfsfAppAliveChanged);
        this.reader.getIfsfApp().getTerminalIdChanges().subscribe(this::onIfsfTerminalIdhanged);
        this.reader.getIfsfApp().getRestartEvents().subscribe(this::onIfsfRestart);

        this.reader.getTransitApp().getTcpConnectionChanges().subscribe(this::onTransitTcpConnectionChanged);
        this.reader.getTransitApp().getAppAliveChanges().subscribe(this::onTransitAppAliveChanged);
        this.reader.getTransitApp().getTerminalStatusChanges().subscribe(this::onTransitTerminalStatusChanges);
        this.reader.getTransitApp().getSamSlotStatusChanges().subscribe(this::onSamSlotStatusChanged);
        this.reader.getTransitApp().getSamAtrChanges().subscribe(this::onSamAtrChanged);

        this.reader.getInitStateChanges().subscribe(this::onReaderInitStatusChanged);

        this.reader.getSamDuk().getSlotIndexChanges().subscribe(this::onSamDukSlotIndexChanged);
        this.reader.getSamDuk().getSlotStatusChanges().subscribe(this::onSamDukSlotStatusChanged);
        this.reader.getSamDuk().getSamAtrChanges().subscribe(this::onSamDukAtrChanged);
        this.reader.getSamDuk().getAuthChanges().subscribe(this::onSamDukAuthChanged);
        this.reader.getSamDuk().getUnlockStatusChanges().subscribe(this::onSamDukUnlockStatusChanged);
        this.reader.getSamDuk().getSamNumberChanges().subscribe(this::onSamDukSamNumberChanged);
    }

    private void onIfsfTcpConnectionChanged(IngenicoIfsfEventArgs ea) {
        String logMsg = "tcp=" + (ea.getSource().isConnected() ? "ok" : "chyba");
        auditLogService.system(AuditLogAction.EMV_TERMINAL_CONNECTION, logMsg);
        ifsfLogger.info("IFSF TCP connection: " + logMsg);
    }

    private void onIfsfAppAliveChanged(IngenicoIfsfEventArgs ea) {
        String logMsg = "stav_kom_ifsf_app=" + (ea.getSource().isAppAlive() ? "ok" : "chyba");
        auditLogService.system(AuditLogAction.EMV_TERMINAL_STATUS, logMsg);
        ifsfLogger.info("IFSF status: " + logMsg);
    }

    private void onIfsfTerminalIdhanged(IngenicoIfsfEventArgs ea) {
        String logMsg = "terminal_id=" + ea.getSource().getTerminalID();
        auditLogService.system(AuditLogAction.EMV_TERMINAL_ID, logMsg);
        ifsfLogger.info("IFSF: " + logMsg);
    }

    private void onIfsfRestart(IngenicoIfsfEventArgs ea) {
        String logMsg = String.format("LASTSTART=\"%s\"", ea.getSource().getPrivateData().get(IfsfPrivateData.KEY_LASTSTART));
        auditLogService.system(AuditLogAction.EMV_TERMINAL_RESTART, logMsg);
        ifsfLogger.info("IFSF restart: " + logMsg);
    }

    private void onTransitTcpConnectionChanged(IngenicoTransitEventArgs ea) {
        String logMsg = "tcp=" + (ea.getSource().isConnected() ? "ok" : "chyba");
        auditLogService.system(AuditLogAction.INGENICO_TRANSIT_TCP_CONNECTION, logMsg);
        transitLogger.info("Transit TCP connection: " + logMsg);
    }

    private void onTransitAppAliveChanged(IngenicoTransitEventArgs ea) {
        String logMsg = "stav_kom_transit_app=" + (ea.getSource().isAppAlive() ? "ok" : "chyba");
        auditLogService.system(AuditLogAction.INGENICO_TRANSIT_APP_ALIVE, logMsg);
        transitLogger.info("Transit app: " + logMsg);
    }

    private void onTransitTerminalStatusChanges(IngenicoTransitEventArgs ea) {
        String logMsg = ea.getSource().getTerminalStatusAsAuditLog();
        auditLogService.system(AuditLogAction.INGENICO_TRANSIT_TERMINAL_STATUS, logMsg);
        transitLogger.info("Terminal status: " + logMsg);
    }

    private void onReaderInitStatusChanged(IngenicoEventArgs ev) {
        EReaderInitState initStatus = reader.getInitStatus();
        String logMsg = "kod=" + initStatus.getCode() + ", stav=\"" + initStatus.getDescription() + "\"";
        auditLogService.system(AuditLogAction.INGENICO_INIT_STATUS, logMsg);
        transitLogger.info("Ingenico init status: " + logMsg);
    }

    private void onSamSlotStatusChanged(SamSlotEventArgs ev) {
        String logMsg = reader.getTransitApp().getSamSlots().asAuditLog();
        auditLogService.system(AuditLogAction.INGENICO_TRANSIT_SAM_SLOT_STATUS, logMsg);
        transitLogger.info("Ingenico SAM slot status: " + logMsg);
    }

    private void onSamAtrChanged(SamSlotEventArgs ev) {
        SamSlot samSlot = ev.getSource();
        String logMsg = String.format("slot=%d, ATR: %s", samSlot.getSlotIndex(), ByteArrayFormatter.toHex(samSlot.getSamAtr().toByteArray()));
        auditLogService.system(AuditLogAction.INGENICO_TRANSIT_SAM_ATR, logMsg);
        transitLogger.info("Ingenico SAM ATR: " + logMsg);
    }

    private void onSamDukSlotIndexChanged(SamDukEventArgs ev) {
        String logMsg = reader.getSamDuk().getAuditSlotIndex();
        auditLogService.system(AuditLogAction.SAM_SLOT_INDEX, logMsg);
        transitLogger.info("SAM DUK - slot index: " + logMsg);
    }

    private void onSamDukSlotStatusChanged(SamDukEventArgs ev) {
        String logMsg = reader.getSamDuk().getAuditSlotStatus();
        auditLogService.system(AuditLogAction.SAM_SLOT_STATUS, logMsg);
        transitLogger.info("SAM DUK - slot status: " + logMsg);
    }

    private void onSamDukAtrChanged(SamDukEventArgs ev) {
        String logMsg = reader.getSamDuk().getAuditATR();
        auditLogService.system(AuditLogAction.SAM_ATR, logMsg);
        transitLogger.info("SAM DUK - ATR: " + logMsg);
    }

    private void onSamDukAuthChanged(SamDukEventArgs ev) {
        AuthProcessState state = reader.getSamDuk().getAuth().getProcessState();
        String logMsg = "kod=" + state.getCode() + ", stav=\"" + state.getDescription() + "\"";
        if (state == AuthProcessState.FINISHED) {
            AuthResult result = reader.getSamDuk().getAuth().getLastAuthResult();
            logMsg += ", vysledek=" + (result.isSuccess() ? "ok" : "chyba") + " (" + result.getMessage() + ")";
        }
        auditLogService.system(AuditLogAction.SAM_AUTH, logMsg);
        transitLogger.info("SAM DUK - Auth: " + logMsg);
    }

    private void onSamDukUnlockStatusChanged(SamDukEventArgs ev) {
        String logMsg = reader.getSamDuk().getAuditUnlockStatus();
        auditLogService.system(AuditLogAction.SAM_UNLOCK_STATUS, logMsg);
        transitLogger.info("SAM DUK - unlock status: " + logMsg);
    }

    private void onSamDukSamNumberChanged(SamDukEventArgs ev) {
        String logMsg = reader.getSamDuk().getAuditSamNumber();
        auditLogService.system(AuditLogAction.SAM_NUMBER, logMsg);
        transitLogger.info("SAM DUK - number: " + logMsg);
    }

}
