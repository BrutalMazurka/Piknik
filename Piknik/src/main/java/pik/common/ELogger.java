package pik.common;

import jCommons.logging.ILoggerID;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 07/11/2025
 */
public enum ELogger implements ILoggerID {
    APP("App"),
    UPDATER("Updater"),
    INGENICO_IFSF("IfsfProt"),
    INGENICO_TRANSIT("IngenicoTransitProt"),
    ;

    private final String name;

    ELogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
