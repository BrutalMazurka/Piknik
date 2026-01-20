package pik.domain.duk.card;

import epis5.pos.ordering.Order;
import pik.domain.ingenico.CardDetectedData;

/**
 * Order object for card reading operation.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 20/01/2026
 */
public class CardReadOrder extends Order {
    private final CardDetectedData cardDetectedData;
    private final String readSchema;

    public CardReadOrder(CardDetectedData cardDetectedData, String readSchema) {
        this.cardDetectedData = cardDetectedData;
        this.readSchema = readSchema;
    }

    public CardDetectedData getCardDetectedData() {
        return cardDetectedData;
    }

    public String getReadSchema() {
        return readSchema;
    }
}