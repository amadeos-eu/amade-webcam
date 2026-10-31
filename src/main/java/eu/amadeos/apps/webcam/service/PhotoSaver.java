package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.PhotoFormat;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes photos: JPEG at quality 92, or PNG. Written to a temporary name first, so no half file stays. */
public class PhotoSaver {
    public static final float JPEG_QUALITY = 0.92f;

    public Path save(BufferedImage frame, Path file, PhotoFormat format, boolean mirrored) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        BufferedImage image = mirrored ? mirror(frame) : frame;
        Path temp = file.resolveSibling("." + file.getFileName() + ".part");
        try {
            if (format == PhotoFormat.JPEG) {
                writeJpeg(image, temp);
            } else if (!ImageIO.write(image, "png", temp.toFile())) {
                throw new IOException("No PNG writer");
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return file;
    }

    /** The picture flipped left to right, as a mirror shows it. */
    public static BufferedImage mirror(BufferedImage frame) {
        AffineTransform flip = AffineTransform.getScaleInstance(-1, 1);
        flip.translate(-frame.getWidth(), 0);
        BufferedImage out = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        new AffineTransformOp(flip, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(frame, out);
        return out;
    }

    private static void writeJpeg(BufferedImage image, Path file) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
