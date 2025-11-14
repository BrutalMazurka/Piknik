package pik.domain.ingenico;

public class IngenicoEventArgs {
    public enum EType {
        INIT_STATE,
    }

    private final EType type;
    private final IngenicoReaderDevice source;

    public IngenicoEventArgs(EType type, IngenicoReaderDevice source) {
        this.type = type;
        this.source = source;
    }

    public EType getType() {
        return type;
    }

    public IngenicoReaderDevice getSource() {
        return source;
    }
}
