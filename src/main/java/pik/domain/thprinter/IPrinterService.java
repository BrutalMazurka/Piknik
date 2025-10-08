package pik.domain.thprinter;

import jpos.JposException;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 08/10/2025
 */
public interface IPrinterService {
    void initialize() throws JposException;
    void printText(String text) throws JposException;
    void print(PrintRequest request) throws JposException;
    void cutPaper() throws JposException;
    PrinterStatus getStatus();
    void close();
}
