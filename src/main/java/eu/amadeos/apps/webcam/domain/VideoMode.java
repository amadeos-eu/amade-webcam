package eu.amadeos.apps.webcam.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One way a camera can deliver pictures: pixel format, size and frame rate.
 *
 * @param fourcc the V4L2 pixel format, {@code MJPG} or {@code YUYV}
 */
public record VideoMode(String fourcc, int width, int height, double fps) {
    /** What amade-webcam picks for a camera it has never used: 1280 × 720 if possible, else the closest. */
    public static final int PREFERRED_WIDTH = 1280;
    public static final int PREFERRED_HEIGHT = 720;

    public VideoMode {
        Objects.requireNonNull(fourcc, "fourcc");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size " + width + "x" + height);
        if (fps <= 0) throw new IllegalArgumentException("fps " + fps);
    }

    /**
     * The name of the format for ffmpeg's {@code -input_format}; empty for formats ffmpeg would pick by itself.
     */
    public Optional<String> ffmpegInputFormat() {
        return Optional.ofNullable(switch (fourcc) {
            case "MJPG" -> "mjpeg";
            case "YUYV" -> "yuyv422";
            case "H264" -> "h264";
            case "NV12" -> "nv12";
            case "YU12" -> "yuv420p";
            case "RGB3" -> "rgb24";
            case "BGR3" -> "bgr24";
            case "GREY" -> "gray";
            default -> null;
        });
    }

    /** Compressed formats deliver big pictures at full speed over USB 2; raw ones often only at 5 fps. */
    public boolean compressed() {
        return fourcc.equals("MJPG") || fourcc.equals("H264");
    }

    /** "1280 × 720" */
    public String sizeLabel() {
        return width + " × " + height;
    }

    /** "MJPEG", "YUYV" */
    public String formatLabel() {
        return fourcc.equals("MJPG") ? "MJPEG" : fourcc;
    }

    /** "30 fps", "7.5 fps" */
    public String fpsLabel() {
        return (fps == Math.rint(fps) ? String.valueOf((int) fps) : String.format(Locale.ROOT, "%.1f", fps)) + " fps";
    }

    /** The frame rate ffmpeg is asked for, "30" or "7.5". */
    public String fpsArgument() {
        return fps == Math.rint(fps) ? String.valueOf((int) fps) : String.format(Locale.ROOT, "%.3f", fps);
    }

    public long pixels() {
        return (long) width * height;
    }

    /**
     * The best mode for a camera without saved choice: the size closest to 1280 × 720 (bigger wins a tie), then
     * the highest frame rate, then a compressed format.
     */
    public static Optional<VideoMode> preferred(List<VideoMode> modes) {
        return modes.stream().min(Comparator
                .comparingLong((VideoMode m) -> Math.abs(m.pixels() - (long) PREFERRED_WIDTH * PREFERRED_HEIGHT))
                .thenComparing(m -> -m.pixels())
                .thenComparing(m -> -m.fps())
                .thenComparing(m -> m.compressed() ? 0 : 1));
    }
}
