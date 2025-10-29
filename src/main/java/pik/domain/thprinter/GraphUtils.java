package pik.domain.thprinter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pik.common.TM_T20IIIConstants;

/**
 * @author Martin Sustik <sustik@herman.cz>
 * @since 28/08/2025
 */
public final class GraphUtils {
    private static final Logger logger = LoggerFactory.getLogger(GraphUtils.class);

    private GraphUtils() {
        throw new AssertionError("Utility class cannot be instantiated.");
    }

    /**
     * Load and process image file
     */
    public static BufferedImage loadAndProcessImage(String imagePath) throws IOException {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            if (originalImage == null) {
                throw new IOException("Failed to load image: " + imagePath);
            }
            // Convert to monochrome for thermal printer
            return convertToMonochromeImage(originalImage);
    }

    /**
     * Convert image to monochrome (black and white)
     */
    public static BufferedImage convertToMonochromeImage(BufferedImage originalImage) {
        BufferedImage monoImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY);

        Graphics2D g2d = monoImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();

        return monoImage;
    }

    /**
     * Convert image to monochrome bitmap.
     * Each row is padded to the nearest byte boundary.
     */
    public static byte[] convertToMonochromeBitmap(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Convert to monochrome bitmap format expected by printer
        int bytesPerRow = (width + 7) / 8;  // Round up to nearest byte
        byte[] bitmapData = new byte[bytesPerRow * height];
        int byteIndex = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += 8) {
                byte pixelByte = 0;
                for (int bit = 0; bit < 8 && (x + bit) < width; bit++) {
                    int rgb = originalImage.getRGB(x + bit, y);
                    int gray = (int) (0.299 * ((rgb >> 16) & 0xFF) +
                            0.587 * ((rgb >> 8) & 0xFF) +
                            0.114 * (rgb & 0xFF));

                    if (gray < TM_T20IIIConstants.GRAYSCALE_THRESHOLD) { // Black pixel
                        pixelByte |= (0x80 >> bit);
                    }
                }
                bitmapData[byteIndex++] = pixelByte;
            }
        }

        return bitmapData;
    }

    /**
     * Resize image while maintaining aspect ratio
     */
    public static BufferedImage resizeImage(BufferedImage originalImage, int maxWidth) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Calculate new dimensions
        double scale = (double) maxWidth / originalWidth;
        int newWidth = maxWidth;
        int newHeight = (int) (originalHeight * scale);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = resizedImage.createGraphics();

        // Enable high-quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * Convert BufferedImage to bitmap byte array
     */
    public static byte[] convertImageToBitmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Calculate bytes per row (width rounded up to nearest byte)
        int bytesPerRow = (width + 7) / 8;
        byte[] bitmapData = new byte[bytesPerRow * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get pixel color
                int rgb = image.getRGB(x, y);

                // Convert to grayscale and threshold
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF) +
                        0.587 * ((rgb >> 8) & 0xFF) +
                        0.114 * (rgb & 0xFF));

                // If pixel is dark enough, set bit
                if (gray < TM_T20IIIConstants.GRAYSCALE_THRESHOLD) {
                    int byteIndex = y * bytesPerRow + x / 8;
                    int bitIndex = 7 - (x % 8);
                    bitmapData[byteIndex] |= (1 << bitIndex);
                }
            }
        }

        return bitmapData;
    }

    /**
     * Generate QR code image
     */
    public static BufferedImage generateQRCode(String text, int width, int height) {
        try {
            // Configure QR code parameters
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            // Generate QR code matrix
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            // Convert to BufferedImage
            BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    qrImage.setRGB(x, y, bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            return qrImage;

        } catch (WriterException e) {
            logger.error("Error generating QR code: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create text image with custom font
     */
    public static BufferedImage createTextImage(String text, Font font, int width, int height) {
        BufferedImage textImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = textImage.createGraphics();

        // Set rendering hints for better text quality
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Fill background with white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Set font and color for text
        g2d.setFont(font);
        g2d.setColor(Color.BLACK);

        // Calculate text position for centering
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        int x = (width - textWidth) / 2;
        int y = (height - textHeight) / 2 + fm.getAscent();

        // Draw text
        g2d.drawString(text, x, y);
        g2d.dispose();

        return textImage;
    }

}
