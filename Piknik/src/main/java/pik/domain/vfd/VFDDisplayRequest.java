package pik.domain.vfd;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 26/09/2025
 */
public class VFDDisplayRequest {
    private String text;
    private Integer row;
    private Integer col;
    private Integer brightness;
    private Boolean showCursor;
    private String customCommand;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Integer getRow() { return row; }
    public void setRow(Integer row) { this.row = row; }

    public Integer getCol() { return col; }
    public void setCol(Integer col) { this.col = col; }

    public Integer getBrightness() { return brightness; }
    public void setBrightness(Integer brightness) { this.brightness = brightness; }

    public Boolean getShowCursor() { return showCursor; }
    public void setShowCursor(Boolean showCursor) { this.showCursor = showCursor; }

    public String getCustomCommand() { return customCommand; }
    public void setCustomCommand(String customCommand) { this.customCommand = customCommand; }
}
