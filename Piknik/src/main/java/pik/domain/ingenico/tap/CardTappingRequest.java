package pik.domain.ingenico.tap;

public class CardTappingRequest {
    public enum ESource {
        UNSPECIFIED,
        MAIN,
        SALE,
        CARD_INFO,
        SAM_UNLOCK
    }

    public static final CardTappingRequest NULL_INSTANCE = new CardTappingRequest(ESource.UNSPECIFIED,
            cardDetectedData -> {
            },
            () -> {
            });

    private final ESource source;
    private final ICardTapCallback callback;
    private final ICardTapErrorCallback tapErrorCallback;

    public CardTappingRequest(ESource source, ICardTapCallback callback, ICardTapErrorCallback tapErrorCallback) {
        this.source = source;
        this.callback = callback;
        this.tapErrorCallback = tapErrorCallback;
    }

    public ESource getSource() {
        return source;
    }

    public ICardTapCallback getCallback() {
        return callback;
    }

    public ICardTapErrorCallback getTapErrorCallback() {
        return tapErrorCallback;
    }

    public boolean isNull() {
        return this == NULL_INSTANCE;
    }
}
