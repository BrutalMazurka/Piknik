package pik.domain.pos.processing;

/**
 * Card processing dialog message.
 * Support class for EVK compatibility (SamUnlockOrderProcessor uses this).
 * In Piknik, this is just a simple wrapper since we don't have actual dialogs.
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 2026-01-15
 */
public class CardProcessingDialogMsg {
    private final String message;

    private CardProcessingDialogMsg(String message) {
        this.message = message;
    }

    /**
     * Create DUK card processing message
     */
    public static CardProcessingDialogMsg newDuk(String msg) {
        return new CardProcessingDialogMsg(msg);
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }
}