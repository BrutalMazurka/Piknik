package pik.domain.ingenico.ifsf.service;

import epis5.ingenicoifsf.prot.IIfsfProtMsgOutputter;
import epis5.ingenicoifsf.prot.IfsfOverallResult;
import epis5.ingenicoifsf.prot.IfsfProtMsg;
import epis5.ingenicoifsf.prot.IfsfProtServiceProcessorBase;
import epis5.ingenicoifsf.prot.xml.device.DeviceOutputRequestDto;
import epis5.ingenicoifsf.prot.xml.device.DeviceOutputResponseDto;
import epis5.ingenicoifsf.proxy.IfsfDeviceOutputRegister;

import javax.inject.Inject;

public class IfsfServiceDeviceOutputProcessor extends IfsfProtServiceProcessorBase {
    private final IfsfDeviceOutputRegister register;

    @Inject
    public IfsfServiceDeviceOutputProcessor(IIfsfProtMsgOutputter outputter, IfsfDeviceOutputRegister register) {
        super(outputter);

        this.register = register;
    }

    @Override
    public void process(IfsfProtMsg writtenMsg, IfsfProtMsg incMsg) {
        if (incMsg == null)
            return;

        DeviceOutputRequestDto reqDto = dtoXmlReader.parse(incMsg.getData(), DeviceOutputRequestDto.getParser());

        DeviceOutputResponseDto respDto = new DeviceOutputResponseDto(
                incMsg.getRequestId(),
                IfsfOverallResult.SUCCESS,
                reqDto.getOutDeviceTarget(),
                reqDto.getSequenceID());

        IfsfProtMsg protMsg = IfsfProtMsg.createOutgoingNoResponse(respDto, dtoXmlWriter.getBytes(respDto), incMsg.getSocketAddress());
        outputter.outputDeviceProxyMsg(protMsg);

        if (reqDto.isOutDeviceTargetDisplay()) {
            register.process(reqDto);
        }
    }
}
