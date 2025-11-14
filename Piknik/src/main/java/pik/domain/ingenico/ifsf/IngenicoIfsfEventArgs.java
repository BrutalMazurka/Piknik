package pik.domain.ingenico.ifsf;

public class IngenicoIfsfEventArgs {
    public enum EType {
        TCP_CONNECTION,
        APP_ALIVE,
        TERMINAL_ID,
        RESTART
    }

    private final EType type;
    private final IngenicoIfsfApp source;

    public IngenicoIfsfEventArgs(EType type, IngenicoIfsfApp source) {
        this.type = type;
        this.source = source;
    }

    public EType getType() {
        return type;
    }

    public IngenicoIfsfApp getSource() {
        return source;
    }
}
