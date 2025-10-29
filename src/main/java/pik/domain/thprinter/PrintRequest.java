package pik.domain.thprinter;

import pik.common.TM_T20IIIConstants;

import java.util.List;

/**
 * Print job request DTO
 * @author Martin Sustik <sustik@herman.cz>
 * @since 25/09/2025
 */
public class PrintRequest {
    private String text;
    private List<PrintItem> items;
    private boolean cutPaper = true;
    private int copies = 1;
    private PrintOptions options;

    public static class PrintItem {
        private String type; // TEXT, IMAGE, BARCODE, LINE
        private String content;
        private PrintItemOptions options;

        // Getters and setters
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }
        public void setContent(String content) {
            this.content = content;
        }

        public PrintItemOptions getOptions() {
            return options;
        }
        public void setOptions(PrintItemOptions options) {
            this.options = options; }
    }

    public static class PrintItemOptions {
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private int fontSize = 1; // 1-8
        private String alignment = "LEFT"; // LEFT, CENTER, RIGHT
        private int width;
        private int height;

        // Getters and setters
        public boolean isBold() {
            return bold;
        }
        public void setBold(boolean bold) {
            this.bold = bold;
        }

        public boolean isItalic() {
            return italic;
        }
        public void setItalic(boolean italic) {
            this.italic = italic;
        }

        public boolean isUnderline() {
            return underline;
        }
        public void setUnderline(boolean underline) {
            this.underline = underline;
        }

        public int getFontSize() {
            return fontSize;
        }
        public void setFontSize(int fontSize) {
            if (fontSize < TM_T20IIIConstants.MIN_FONT_SIZE ||
                    fontSize > TM_T20IIIConstants.MAX_FONT_SIZE) {
                throw new IllegalArgumentException(
                        "Font size must be between " + TM_T20IIIConstants.MIN_FONT_SIZE +
                                " and " + TM_T20IIIConstants.MAX_FONT_SIZE);
            }
            this.fontSize = fontSize;
        }

        public String getAlignment() {
            return alignment;
        }
        public void setAlignment(String alignment) {
            this.alignment = alignment;
        }

        public int getWidth() {
            return width;
        }
        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }
        public void setHeight(int height) {
            this.height = height;
        }
    }

    public static class PrintOptions {
        private int lineSpacing = 30;
        private boolean autoFeed = true;
        private String characterSet = "ISO8859-1";

        // Getters and setters
        public int getLineSpacing() {
            return lineSpacing;
        }
        public void setLineSpacing(int lineSpacing) {
            this.lineSpacing = lineSpacing;
        }

        public boolean isAutoFeed() {
            return autoFeed;
        }
        public void setAutoFeed(boolean autoFeed) {
            this.autoFeed = autoFeed;
        }

        public String getCharacterSet() {
            return characterSet;
        }
        public void setCharacterSet(String characterSet) {
            this.characterSet = characterSet;
        }
    }

    // Main class getters and setters
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public List<PrintItem> getItems() {
        return items;
    }
    public void setItems(List<PrintItem> items) {
        this.items = items;
    }

    public boolean isCutPaper() {
        return cutPaper;
    }
    public void setCutPaper(boolean cutPaper) {
        this.cutPaper = cutPaper;
    }

    public int getCopies() {
        return copies;
    }
    public void setCopies(int copies) {
        this.copies = copies;
    }

    public PrintOptions getOptions() {
        return options;
    }
    public void setOptions(PrintOptions options) {
        this.options = options;
    }
}
