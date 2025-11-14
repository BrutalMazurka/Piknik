package pik.domain.ingenico;

import epis5.ingenico.transit.prot.CardType;
import epis5.ingenico.transit.prot.ResponseCode;
import jCommons.ByteArrayFormatter;

public class CardDetectedData {
    public static final CardDetectedData NULL_INSTANCE = new CardDetectedData(ResponseCode.UNKNOWN, CardType.UNKNOWN, new byte[7]);

    private final ResponseCode responseCode;
    private final CardType cardType;
    private final byte[] uidBytes;
    private final String uid;

    public CardDetectedData(ResponseCode responseCode, CardType cardType, byte[] uidBytes) {
        this.responseCode = responseCode;
        this.cardType = cardType;
        this.uidBytes = uidBytes;
        this.uid = ByteArrayFormatter.toHexNoSpaces(uidBytes);
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getUid() {
        return uid;
    }

    public byte[] getUidBytes() {
        return uidBytes;
    }

    public boolean isNull() {
        return this == NULL_INSTANCE;
    }
}
