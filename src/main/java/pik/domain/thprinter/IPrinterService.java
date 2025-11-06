package pik.domain.thprinter;

import jpos.JposException;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 08/10/2025
 */
public interface IPrinterService {
    void initialize() throws JposException;
    boolean isInitialized();
    boolean isReady();
    void printText(String text) throws JposException, InterruptedException;
    void print(PrintRequest request) throws JposException;
    void cutPaper() throws JposException;
    boolean waitForOutputComplete(long timeoutMs);
    PrinterStatus getStatus();
    void close();
}
