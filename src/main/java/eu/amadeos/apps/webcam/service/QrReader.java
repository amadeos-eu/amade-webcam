package eu.amadeos.apps.webcam.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds a QR code in a camera frame with ZXing. The frame is turned into a grey picture of at most 800 pixels
 * width first: a code held up to a webcam fills a good part of the picture, and the smaller picture keeps the
 * search fast enough to run a few times a second.
 */
public class QrReader {
    private static final int MAX_WIDTH = 800;
    private static final Map<DecodeHintType, Object> HINTS = Map.of(
            DecodeHintType.TRY_HARDER, Boolean.TRUE,
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET, "UTF-8");

    private final QRCodeReader reader = new QRCodeReader();

    // --8<-- [start:read]
    /** The text of the code in the frame, empty if there is none (or it cannot be read). */
    public Optional<String> read(BufferedImage frame) {
        int step = Math.max(1, (frame.getWidth() + MAX_WIDTH - 1) / MAX_WIDTH);
        int w = frame.getWidth() / step, h = frame.getHeight() / step;
        byte[] grey = grey(frame, step, w, h);
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(grey, w, h, 0, 0, w, h, false);
        try {
            return Optional.of(reader.decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS).getText());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (ReaderException | RuntimeException e) {
            return Optional.empty();
        } finally {
            reader.reset();
        }
    }
    // --8<-- [end:read]

    private static byte[] grey(BufferedImage frame, int step, int w, int h) {
        byte[] grey = new byte[w * h];
        if (frame.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] px = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
            int stride = frame.getWidth() * 3;
            for (int y = 0; y < h; y++) {
                int row = y * step * stride;
                for (int x = 0; x < w; x++) {
                    int i = row + x * step * 3;
                    int b = px[i] & 0xff, g = px[i + 1] & 0xff, r = px[i + 2] & 0xff;
                    grey[y * w + x] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
                }
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = frame.getRGB(x * step, y * step);
                    int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                    grey[y * w + x] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
                }
            }
        }
        return grey;
    }
}
