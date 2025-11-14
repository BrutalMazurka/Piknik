package pik.domain.auditlogs;

import epis.commons.auditlogs.IAuditLogService;

public abstract class AuditLogEventHandlerBase {
    protected final IAuditLogService auditLogService;

    protected AuditLogEventHandlerBase(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
}
