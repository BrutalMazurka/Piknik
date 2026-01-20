package pik.domain.duk.card;

import com.google.inject.Injector;
import epis5.duk.bck.core.card.CardDuk;
import pik.domain.duk.card.opt.DukCardReaderOpt;
import pik.domain.duk.card.opt.EmvCardDetectorOpt;
import pik.domain.pos.processing.OrderProcessorBase;

/**
 * Processor for card reading operations.
 * Detects card type and reads DUK card data.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadOrderProcessor extends OrderProcessorBase {
    private final CardReadOrder order;
    private CardDuk cardDuk;

    public CardReadOrderProcessor(Injector injector, CardReadOrder order) {
        super(injector);
        this.order = order;
        this.cardDuk = null;
    }

    public CardDuk getCardDuk() {
        return cardDuk;
    }

    @Override
    public void process() {
        try {
            stopCardTapping(true);

            if (!verifyIngenicoReaderReady(true)) {
                return;
            }

            if (!verifySamDukAuthenticated()) {
                return;
            }

            logger.info("Processing card read with card UID: " + order.getCardDetectedData().getUid());

            // Step 1: Detect if EMV card
            EmvCardDetectorOpt emvDetector = getInstance(EmvCardDetectorOpt.class);
            if (!executeOpt(emvDetector)) {
                return;
            }

            if (emvDetector.isEmvCardDetected()) {
                showErrorMessageAndFinish("Not a DUK card (EMV card detected)");
                return;
            }

            // Step 2: Read DUK card
            DukCardReaderOpt cardReader = getInstance(DukCardReaderOpt.class);

            // Map read schema
            DukCardReaderOpt.ReadSchema schema = "BASIC".equalsIgnoreCase(order.getReadSchema())
                    ? DukCardReaderOpt.ReadSchema.EP_PAYMENT
                    : DukCardReaderOpt.ReadSchema.CARD_INFO;

            cardReader.setParams(schema, order.getCardDetectedData());

            if (!executeOpt(cardReader)) {
                return;
            }

            // Get card data
            cardDuk = cardReader.getCardDuk();

            // Set result OK so orchestrator knows read succeeded
            setResultOk();
            logger.info("Card read completed successfully");

        } catch (Exception e) {
            logger.fatal("Processing CardReadOrderProcessor", e);
            showErrorMessageAndFinish(e.getMessage());
        }
    }

    private boolean verifySamDukAuthenticated() {
        if (!ingenicoReader.getSamDuk().getAuth().isAuthenticated()) {
            showErrorMessageAndFinish("SAM not authenticated - cannot read card");
            return false;
        }
        return true;
    }
}