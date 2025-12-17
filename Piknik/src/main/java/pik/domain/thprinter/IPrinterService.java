package pik.domain.thprinter;

import java.io.IOException;

/**
 * Printer service interface for ESC/POS thermal printers
 * @author Martin Sustik <sustik@herman.cz>
 * @since 08/10/2025
 */
public interface IPrinterService {
    void initialize() throws IOException;
    boolean isInitialized();
    boolean isReady();
    void printText(String text) throws IOException, InterruptedException;
    void print(PrintRequest request) throws IOException;
    void cutPaper() throws IOException;
    boolean waitForOutputComplete(long timeoutMs);
    PrinterStatus getStatus();
    void close();
}
