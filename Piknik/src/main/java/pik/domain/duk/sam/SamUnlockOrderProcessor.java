package pik.domain.duk.sam;

import com.google.inject.Injector;
import epis5.duk.bck.core.sam.SamPin;
import pik.domain.pos.processing.OrderProcessorBase;

public class SamUnlockOrderProcessor extends OrderProcessorBase {
    private final SamUnlockOrder order;

    public SamUnlockOrderProcessor(Injector injector, SamUnlockOrder order) {
        super(injector);

        this.order = order;
    }

    @Override
    public void process() {
        try {
            stopCardTapping(true);

            if (!verifyIngenicoReaderReady(true)) {
                return;
            }

            if (!verifySamDukReadyForUnlock()) {
                return;
            }

            if (!SamPin.isValidFormat(order.getPin())) {
                showErrorMessageAndFinish("Neplatný formát zadaného PINu!");
                return;
            }

            // Note: Display service is no-op in Piknik (REST API only)
        // In EVK this would show "NECHTE KARTU PŘILOŽENOU" on SWING UI
        logger.info("Processing SAM unlock with card UID: " + order.getCardDetectedData().getUid());

            SamVerifyPinOpt verifyPinOpt = getInstance(SamVerifyPinOpt.class);
            verifyPinOpt.setOrder(order);
            if (!executeOpt(verifyPinOpt)) {
                return;
            }

            // Note: showResult is no-op in Piknik (REST API only)
        // In EVK this would display "SAM ODEMČEN" for 1.8 seconds on SWING UI
        // Result is tracked via session status in SamUnlockOrchestrator
        logger.info("SAM unlock completed successfully");
        } catch (Exception e) {
            logger.fatal("Processing SamUnlockOrderProcessor", e);
            showErrorMessageAndFinish(e.getMessage());
        }
    }

    private boolean verifySamDukReadyForUnlock() {
        if (!ingenicoReader.getSamDuk().getAuth().isAuthenticated()) {
            showErrorMessageAndFinish("Neproběhla autentizace SAM - nelze odemčít!");
            return false;
        }
        if (ingenicoReader.getSamDuk().isUnlockStatusCompleted()) {
            showErrorMessageAndFinish("SAM již odemčen!");
            return false;
        }
        return true;
    }
}
