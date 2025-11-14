package pik.domain.ingenico.tap;

import pik.domain.ingenico.CardDetectedData;

public interface ICardTapCallback {
    void onCardTapDetected(CardDetectedData cardDetectedData);
}
