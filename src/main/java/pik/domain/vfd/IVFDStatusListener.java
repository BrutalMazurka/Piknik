package pik.domain.vfd;

import pik.domain.thprinter.PrinterStatusEvent;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 09/10/2025
 */
public interface IVFDStatusListener {
    void onStatusChanged(VFDStatusEvent event);
}
