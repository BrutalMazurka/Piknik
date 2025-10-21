package pik.domain.vfd;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
public interface IVFDStatusListener {
    void onStatusChanged(VFDStatusEvent event);
}
