package pik.domain.pos.processing;

/**
 * EVK result display message.
 * Support class for EVK compatibility (SamUnlockOrderProcessor uses this).
 * In Piknik, this is just a simple wrapper since we don't have actual display.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
public class EvkResultDisplay {
    private final String message;

    private EvkResultDisplay(String message) {
        this.message = message;
    }

    /**
     * Create general result display
     */
    public static EvkResultDisplay newGeneral(String msg) {
        return new EvkResultDisplay(msg);
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }
}