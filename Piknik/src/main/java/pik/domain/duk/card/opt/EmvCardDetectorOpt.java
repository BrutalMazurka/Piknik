package pik.domain.duk.card.opt;

import epis5.ingenico.transit.cl.Ppse;
import epis5.ingenico.transit.prot.*;
import epis5.pos.processing.opt.OptBase;
import pik.domain.ingenico.IngenicoReaderDevice;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Operation for detecting EMV card type.logging
 * Ported from EVK project.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class EmvCardDetectorOpt extends OptBase {
    private static final int RESPONSE_TIMEOUT_MILLIS = 1_000;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter outputter;
    private boolean emvCardDetected;

    @Inject
    public EmvCardDetectorOpt(IngenicoReaderDevice reader, ITransitProtMsgOutputter outputter) {
        this.reader = reader;
        this.outputter = outputter;
        this.emvCardDetected = false;
    }

    public boolean isEmvCardDetected() {
        return emvCardDetected;
    }

    @Override
    protected void executeTask() {
        final CountDownLatch deliveredSignal = new CountDownLatch(1);

        byte[] apdu = Ppse.apduRequestBytes();

        Payload payload = new PayloadBuilder()
                .command(Command.CL_TRANSMIT)
                .apduRequest(apdu)
                .build();

        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(
                payload,
                reader.getTransitApp().getSocketAddress(),
                RESPONSE_TIMEOUT_MILLIS,
                new ITransitProtServiceProcessor() {
                    @Override
                    public void process(TransitProtMsg writtenMsg, TransitProtMsg incMsg) {
                        if (incMsg == null) {
                            getLogger().warn("EmvCardDetectorOpt: no response (null incMsg) from transit app");
                            return;
                        }
                        try {
                            emvCardDetected = Ppse.isEmvCard(incMsg.getPayload());
                        } catch (Exception e) {
                            setResultError("Card detection error: " + e.getMessage());
                            getLogger().error("EmvCardDetectorOpt callback error", e);
                        } finally {
                            deliveredSignal.countDown();
                        }
                    }
                }
        );
        outputter.outputMsg(transitProtMsg);

        boolean deliverSignalOk;
        try {
            deliverSignalOk = deliveredSignal.await(RESPONSE_TIMEOUT_MILLIS + 300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            getLogger().warn("EmvCardDetectorOpt interrupted", e);
            setResultError("EmvCardDetectorOpt: " + e.getMessage());
            return;
        }

        if (!deliverSignalOk) {
            setResultError("Card processing error: reader not responding (EMV detection)");
            return;
        }

        setResultOk();
    }
}