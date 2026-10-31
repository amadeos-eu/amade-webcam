package eu.amadeos.apps.webcam.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Names for new photos and videos: {@code Photo 2026-10-31 11-42-07.jpg}, {@code Video 2026-10-31 11-42-07.mp4}.
 * They sort by time in any file manager and contain no colon, which other systems do not allow. A burst adds a
 * number ({@code Photo 2026-10-31 11-42-07 (3).jpg}); a name that exists already gets the next free number.
 */
public final class FileNames {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");

    private FileNames() {
    }

    /** {@code number} 0 means a single photo, 1 and up the photos of a burst. */
    public static Path photo(Path folder, LocalDateTime time, PhotoFormat format, int number) {
        return free(folder, "Photo " + STAMP.format(time), number, format.extension());
    }

    public static Path video(Path folder, LocalDateTime time, VideoFormat format, boolean timeLapse) {
        return free(folder, (timeLapse ? "Time Lapse " : "Video ") + STAMP.format(time), 0, format.extension());
    }

    private static Path free(Path folder, String base, int number, String extension) {
        int n = number;
        while (true) {
            Path candidate = folder.resolve(base + (n > 0 ? " (" + n + ")" : "") + "." + extension);
            if (!Files.exists(candidate)) return candidate;
            n++;
        }
    }

    /** True for the photos and videos this program saves (and others in those formats). */
    public static boolean isMedia(Path file) {
        return isPhoto(file) || isVideo(file);
    }

    public static boolean isPhoto(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    public static boolean isVideo(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv");
    }
}
