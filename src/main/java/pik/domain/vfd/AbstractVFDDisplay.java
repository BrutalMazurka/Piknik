package pik.domain.vfd;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pik.common.TM_T20IIIConstants;

/**
 * Abstract Base Class - Common functionality for all VFD displays
 * @author Martin Sustik <sustik@herman.cz>
 * @since 27/08/2025
 */
abstract class AbstractVFDDisplay implements IVFDDisplay {
    private static final Logger logger = LoggerFactory.getLogger(AbstractVFDDisplay.class);

    protected SerialPort serialPort;
    protected boolean isConnected = false;
    protected String displayModel;

    public AbstractVFDDisplay(String model) {
        this.displayModel = model;
    }

    @Override
    public boolean connect(String portName, int baudRate) {
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(1);
            serialPort.setParity(SerialPort.NO_PARITY);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 1000, 0);

            if (serialPort.openPort()) {
                isConnected = true;
                logger.info("Connected to {} at {} ({} baud)", displayModel, portName, baudRate);

                // Wait for connection to stabilize
                Thread.sleep(TM_T20IIIConstants.CONNECTION_STABILIZATION_DELAY_MS);

                // Initialize display with model-specific commands
                initializeDisplay();
                return true;
            } else {
                logger.error("Failed to open port: {}", portName);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error connecting to port {}: {}", portName, e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (isConnected && serialPort != null) {
            serialPort.closePort();
            isConnected = false;
            serialPort = null;
            logger.info("Disconnected from {}", displayModel);
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public String getDisplayModel() {
        return displayModel;
    }

    /**
     * Send raw bytes to the display
     */
    protected void sendRawData(byte[] data) {
        if (!isConnected) {
            logger.error("Not connected to display");
            return;
        }

        try {
            int bytesWritten = serialPort.writeBytes(data, data.length);
            if (bytesWritten != data.length) {
                logger.error("Warning: Not all bytes were written!");
            }
        } catch (Exception e) {
            logger.error("Error sending data: {}", e.getMessage());
        }
    }

    /**
     * Abstract method for model-specific initialization
     */
    protected abstract void initializeDisplay();

    @Override
    public int getMaxRows() {
        return getCommandSet().getMaxRows();
    }

    @Override
    public int getMaxColumns() {
        return getCommandSet().getMaxColumns();
    }

    /**
     * Get model-specific command set
     */
    protected abstract IVFDCommandSet getCommandSet();
}
