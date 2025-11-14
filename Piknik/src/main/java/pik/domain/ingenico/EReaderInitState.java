package pik.domain.ingenico;

public enum EReaderInitState {
    STARTING(1, "Inicializace zahájena"),
    TRANSIT_TCP_CONNECTION(2, "Navazování TCP spojení (Transit)"),
    TRANSIT_APP_ALIVE(3, "Navazování spojení s Transit aplikací (GetState)"),
    TRANSIT_READING_VERSIONS(4, "Vyčítání verzí Transit aplikace (GetInfo)"),
    TRANSIT_SAM_SLOT_POLLING_ATR(5, "Vyčtení SAM ATR - identifikace SAM"),
    TRANSIT_SAM_SLOT_POLLING_STATUS(6, "Vyčtení stavu slotů"),

    IFSF_TCP_CONNECTION(7, "Navazování TCP spojení (IFSF)"),
    IFSF_APP_ALIVE(8, "Navazování spojení s IFSF aplikací (diagnostika)"),

    DONE(9, "Dokončeno");

    private final int code;
    private final String description;

    EReaderInitState(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
