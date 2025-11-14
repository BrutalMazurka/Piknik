package pik.domain.ingenico.ifsf.service;

import com.google.inject.Inject;
import epis5.ingenicoifsf.prot.IIfsfProtMsgOutputter;
import epis5.ingenicoifsf.prot.IfsfProtMsg;
import epis5.ingenicoifsf.prot.IfsfProtServiceBase;
import epis5.ingenicoifsf.prot.xml.service.DiagnosisRequestDto;
import epis5.ingenicoifsf.prot.xml.service.DiagnosisResponseDto;
import pik.domain.ingenico.IngenicoReaderDevice;
import pik.domain.ingenico.ifsf.IngenicoIfsfApp;

public class IfsfServiceDiagnosis extends IfsfProtServiceBase {
    private final IngenicoReaderDevice reader;

    @Inject
    public IfsfServiceDiagnosis(IIfsfProtMsgOutputter outputter, IngenicoReaderDevice reader) {
        super(outputter, 7_000);

        this.reader = reader;

        this.reader.getIfsfApp().getTcpConnectionChanges().subscribe(ea -> makeCheckPeriodElapsed());
    }

    @Override
    protected void processOnPeriodElapsed() {
        checkAppAliveInactivity();

        if (!reader.getIfsfApp().isConnected()) {
            return;
        }

        DiagnosisRequestDto dto = new DiagnosisRequestDto();
        IfsfProtMsg protMsg = IfsfProtMsg.createOutgoingNoResponse(dto, dtoXmlWriter.getBytes(dto), reader.getIfsfApp().getSocketAddress());
        output(protMsg);
    }

    private void checkAppAliveInactivity() {
        if (!reader.getIfsfApp().isAppAlive())
            return;

        if (!reader.getIfsfApp().isConnected() || reader.getIfsfApp().isElapsedFromLastAppAliveSec(15)) {
            reader.getIfsfApp().setAppNotAlive();
        }
    }

    @Override
    public void process(IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) {
        if (incMsg == null)
            return;

        DiagnosisResponseDto dto = dtoXmlReader.parse(incMsg.getData(), DiagnosisResponseDto.getParser());
        if (!dto.isOverallResultSuccess()) {
            getLogger().warn("Diagnosis result = " + dto.getOverallResult());
        }

        final IngenicoIfsfApp ifsfApp = reader.getIfsfApp();

        ifsfApp.setTerminalID(dto.getTerminalID());
        ifsfApp.updatePrivateData(dto);
        ifsfApp.setAppAlive();
    }

}
