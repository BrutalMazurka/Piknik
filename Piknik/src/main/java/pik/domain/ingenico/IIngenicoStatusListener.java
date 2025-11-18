package pik.domain.ingenico;

/**
 * Observer interface for Ingenico reader status changes
 *
 * @author Martin Sustik <sustik@herman.cz>
 * @since 18/11/2025
 */
public interface IIngenicoStatusListener {
    void onStatusChanged(IngenicoStatusEvent event);
}
