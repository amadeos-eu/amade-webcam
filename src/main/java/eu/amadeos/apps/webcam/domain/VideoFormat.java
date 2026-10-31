package eu.amadeos.apps.webcam.domain;

import java.util.List;
import java.util.Locale;

/**
 * The container and codecs of a video. MP4 is the default because it plays everywhere: Windows, macOS, phones,
 * browsers, TVs. WebM uses only open formats.
 */
public enum VideoFormat {
    MP4("mp4", List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p"),
            List.of("-c:a", "aac", "-b:a", "128k"), List.of("-movflags", "+faststart")),
    WEBM("webm", List.of("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1",
            "-b:v", "0", "-crf", "33", "-pix_fmt", "yuv420p"),
            List.of("-c:a", "libopus", "-b:a", "96k"), List.of());

    private final String extension;
    private final List<String> videoCodec;
    private final List<String> audioCodec;
    private final List<String> container;

    VideoFormat(String extension, List<String> videoCodec, List<String> audioCodec, List<String> container) {
        this.extension = extension;
        this.videoCodec = videoCodec;
        this.audioCodec = audioCodec;
        this.container = container;
    }

    public String extension() {
        return extension;
    }

    /** ffmpeg arguments for the picture. */
    public List<String> videoCodec() {
        return videoCodec;
    }

    /** ffmpeg arguments for the sound. */
    public List<String> audioCodec() {
        return audioCodec;
    }

    /** ffmpeg arguments for the file. */
    public List<String> container() {
        return container;
    }

    public static VideoFormat parse(String value, VideoFormat fallback) {
        try {
            return value == null ? fallback : valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
