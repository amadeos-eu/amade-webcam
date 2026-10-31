package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.FileNames;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramResult;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** The newest photos and videos in the two folders, and small pictures of them for the strip. */
public class RecentMedia {
    public static final int THUMB_WIDTH = 160;

    private final CommandRunner runner;

    public RecentMedia(CommandRunner runner) {
        this.runner = runner;
    }

    /** The newest {@code count} photos and videos of both folders, newest first. */
    public List<Path> newest(Path photos, Path videos, int count) {
        List<Path> all = new ArrayList<>();
        for (Path folder : photos.equals(videos) ? List.of(photos) : List.of(photos, videos)) {
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile).filter(FileNames::isMedia)
                        .filter(p -> !p.getFileName().toString().startsWith(".")).forEach(all::add);
            } catch (IOException e) {
                // an unreadable folder has no recent files
            }
        }
        all.sort(Comparator.comparing(RecentMedia::modified).reversed());
        return all.subList(0, Math.min(count, all.size()));
    }

    /** A picture {@value #THUMB_WIDTH} pixels wide; empty if the file cannot be read. */
    public Optional<BufferedImage> thumbnail(Path file) {
        try {
            BufferedImage image = FileNames.isVideo(file) ? videoFrame(file) : photo(file);
            return image == null ? Optional.empty() : Optional.of(scale(image));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static BufferedImage photo(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                ImageReadParam param = reader.getDefaultReadParam();
                int sub = Math.max(1, reader.getWidth(0) / (THUMB_WIDTH * 2));
                param.setSourceSubsampling(sub, sub, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** A frame from half a second into the video, through ffmpeg. */
    private BufferedImage videoFrame(Path file) throws IOException {
        try {
            ProgramResult result = runner.run(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-ss", "0.5",
                    "-i", file.toString(), "-frames:v", "1", "-vf", "scale=" + THUMB_WIDTH + ":-2",
                    "-f", "image2pipe", "-c:v", "png", "pipe:1"), Duration.ofSeconds(10));
            if (!result.succeeded() || result.stdout().length == 0) return null;
            return ImageIO.read(new ByteArrayInputStream(result.stdout()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage image) {
        int w = THUMB_WIDTH, h = Math.max(1, Math.round(image.getHeight() * (float) w / image.getWidth()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
