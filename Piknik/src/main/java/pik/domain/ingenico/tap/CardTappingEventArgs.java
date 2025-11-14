package pik.domain.ingenico.tap;

public class CardTappingEventArgs {
    public enum EType {
        API_TAP_REQUEST,
        TAP_SESSION_STATE,
        MAINTENANCE_MODE
    }

    private final EType type;
    private final IngenicoCardTappingState source;

    public CardTappingEventArgs(EType type, IngenicoCardTappingState source) {
        this.type = type;
        this.source = source;
    }

    public EType getType() {
        return type;
    }

    public IngenicoCardTappingState getSource() {
        return source;
    }
}
