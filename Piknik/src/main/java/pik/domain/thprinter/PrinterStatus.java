package pik.domain.thprinter;

import java.util.Objects;

/** Printer status information
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrinterStatus {
    private boolean online;      // Network reachable (printer powered on and connected)
    private boolean ready;       // Ready to accept print jobs (online + cover closed + no errors)
    private boolean coverOpen;
    private boolean paperEmpty;
    private boolean paperNearEnd;
    private boolean error;
    private String errorMessage;
    private int powerState;
    private long lastUpdate;
    private boolean dummyMode;

    public PrinterStatus() {
        this.lastUpdate = System.currentTimeMillis();
    }

    public PrinterStatus(PrinterStatus other) {
        this.online = other.online;
        this.ready = other.ready;
        this.coverOpen = other.coverOpen;
        this.paperEmpty = other.paperEmpty;
        this.paperNearEnd = other.paperNearEnd;
        this.error = other.error;
        this.errorMessage = other.errorMessage;
        this.powerState = other.powerState;
        this.lastUpdate = other.lastUpdate;
        this.dummyMode = other.dummyMode;
    }

    // Getters and setters
    public boolean isOnline() {
        return online;
    }
    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isReady() {
        return ready;
    }
    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isCoverOpen() {
        return coverOpen;
    }
    public void setCoverOpen(boolean coverOpen) {
        this.coverOpen = coverOpen;
    }

    public boolean isPaperEmpty() {
        return paperEmpty;
    }
    public void setPaperEmpty(boolean paperEmpty) {
        this.paperEmpty = paperEmpty;
    }

    public boolean isPaperNearEnd() {
        return paperNearEnd;
    }
    public void setPaperNearEnd(boolean paperNearEnd) {
        this.paperNearEnd = paperNearEnd;
    }

    public boolean isError() {
        return error;
    }
    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getPowerState() {
        return powerState;
    }
    public void setPowerState(int powerState) {
        this.powerState = powerState;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }
    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean hasWarnings() {
        return coverOpen || paperNearEnd;
    }

    public boolean hasErrors() {
        return error || paperEmpty || !online;
    }

    public boolean isDummyMode() {
        return dummyMode;
    }

    public void setDummyMode(boolean dummyMode) {
        this.dummyMode = dummyMode;
    }

    @Override
    public String toString() {
        return String.format("PrinterStatus{online=%s, ready=%s, coverOpen=%s, paperEmpty=%s, paperNearEnd=%s, error=%s}",
                online, ready, coverOpen, paperEmpty, paperNearEnd, error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrinterStatus that = (PrinterStatus) o;
        return online == that.online &&
                ready == that.ready &&
                coverOpen == that.coverOpen &&
                paperEmpty == that.paperEmpty &&
                paperNearEnd == that.paperNearEnd &&
                error == that.error &&
                powerState == that.powerState &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(online, ready, coverOpen, paperEmpty, paperNearEnd, error, errorMessage, powerState);
    }

}
