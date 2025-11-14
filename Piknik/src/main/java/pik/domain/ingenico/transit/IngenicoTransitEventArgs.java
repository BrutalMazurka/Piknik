package pik.domain.ingenico.transit;

public class IngenicoTransitEventArgs {
    public enum EType {
        TCP_CONNECTION,
        APP_ALIVE,
        TERMINAL_STATUS,
        MESSAGE_ID_SYNCED_AFTER_ERROR
    }

    private final EType type;
    private final IngenicoTransitApp source;

    public IngenicoTransitEventArgs(EType type, IngenicoTransitApp source) {
        this.type = type;
        this.source = source;
    }

    public EType getType() {
        return type;
    }

    public IngenicoTransitApp getSource() {
        return source;
    }
}
