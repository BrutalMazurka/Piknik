package pik.domain.duk.sam;

import epis5.pos.ordering.Order;
import pik.domain.ingenico.CardDetectedData;

public class SamUnlockOrder extends Order {
    private final CardDetectedData cardDetectedData;
    private final String pin;

    public SamUnlockOrder(CardDetectedData cardDetectedData, String pin) {
        this.cardDetectedData = cardDetectedData;
        this.pin = pin;
    }

    public CardDetectedData getCardDetectedData() {
        return cardDetectedData;
    }

    public String getPin() {
        return pin;
    }
}
