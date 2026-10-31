package eu.amadeos.apps.webcam.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Choices of amade-webcam that last, in {@code ~/.config/amadeos/webcam/webcam.properties}: the camera, per
 * camera its mode and control values, the folders, formats, microphone and the switches of the window. Each
 * change is written at once; unknown or broken values fall back to the defaults.
 */
public class WebcamPreferences {
    public static final String FILE = "webcam.properties";
    /** The microphone value for "videos without sound". */
    public static final String NO_SOUND = "none";

    private final Path file;
    private final Properties properties;

    @Inject
    public WebcamPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public WebcamPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    // ---------------------------------------------------------------- camera

    /** The key of the camera used last. */
    public Optional<String> lastCamera() {
        return text("camera.last");
    }

    public void setLastCamera(CameraDevice camera) {
        set("camera.last", camera.key());
    }

    /** The mode chosen for the camera, if one was. */
    public Optional<VideoMode> mode(CameraDevice camera) {
        return text("camera." + camera.key() + ".mode").flatMap(WebcamPreferences::parseMode);
    }

    public void setMode(CameraDevice camera, VideoMode mode) {
        set("camera." + camera.key() + ".mode", mode.fourcc() + " " + mode.width() + "x" + mode.height() + " "
                + mode.fpsArgument());
    }

    /** "MJPG 1280x720 30" */
    static Optional<VideoMode> parseMode(String value) {
        String[] parts = value.strip().split("\\s+");
        if (parts.length != 3) return Optional.empty();
        try {
            String[] size = parts[1].split("x");
            return Optional.of(new VideoMode(parts[0], Integer.parseInt(size[0]), Integer.parseInt(size[1]),
                    Double.parseDouble(parts[2])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The control values the user changed for the camera, name to value. */
    public Map<String, Integer> controls(CameraDevice camera) {
        String prefix = "camera." + camera.key() + ".control.";
        Map<String, Integer> values = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            try {
                values.put(key.substring(prefix.length()), Integer.parseInt(properties.getProperty(key).strip()));
            } catch (NumberFormatException e) {
                // skip broken values
            }
        }
        return values;
    }

    public void setControl(CameraDevice camera, String control, int value) {
        set("camera." + camera.key() + ".control." + control, String.valueOf(value));
    }

    /** Forgets the changed controls, after "Camera Defaults". */
    public void clearControls(CameraDevice camera) {
        String prefix = "camera." + camera.key() + ".control.";
        properties.stringPropertyNames().stream().filter(k -> k.startsWith(prefix)).forEach(properties::remove);
        store();
    }

    // ---------------------------------------------------------------- saving

    public Optional<Path> photoFolder() {
        return text("photo.folder").map(Path::of);
    }

    public void setPhotoFolder(Path folder) {
        set("photo.folder", folder.toAbsolutePath().toString());
    }

    public Optional<Path> videoFolder() {
        return text("video.folder").map(Path::of);
    }

    public void setVideoFolder(Path folder) {
        set("video.folder", folder.toAbsolutePath().toString());
    }

    public PhotoFormat photoFormat() {
        return PhotoFormat.parse(properties.getProperty("photo.format"), PhotoFormat.JPEG);
    }

    public void setPhotoFormat(PhotoFormat format) {
        set("photo.format", format.name());
    }

    /** MP4 unless WebM was chosen: MP4 plays on every computer, phone and TV. */
    public VideoFormat videoFormat() {
        return VideoFormat.parse(properties.getProperty("video.format"), VideoFormat.MP4);
    }

    public void setVideoFormat(VideoFormat format) {
        set("video.format", format.name());
    }

    /** The PipeWire source for the sound of videos, "default" or {@link #NO_SOUND}. */
    public String microphone() {
        return text("video.microphone").orElse("default");
    }

    public void setMicrophone(String name) {
        set("video.microphone", name);
    }

    public boolean shutterSound() {
        return flag("photo.sound", true);
    }

    public void setShutterSound(boolean on) {
        set("photo.sound", String.valueOf(on));
    }

    public boolean flash() {
        return flag("photo.flash", true);
    }

    public void setFlash(boolean on) {
        set("photo.flash", String.valueOf(on));
    }

    /** Saved photos and videos as the preview shows them (mirrored) instead of as others see you. */
    public boolean saveMirrored() {
        return flag("save.mirrored", false);
    }

    public void setSaveMirrored(boolean on) {
        set("save.mirrored", String.valueOf(on));
    }

    // ---------------------------------------------------------------- window

    public boolean mirror() {
        return flag("view.mirror", true);
    }

    public void setMirror(boolean on) {
        set("view.mirror", String.valueOf(on));
    }

    public boolean grid() {
        return flag("view.grid", false);
    }

    public void setGrid(boolean on) {
        set("view.grid", String.valueOf(on));
    }

    public boolean qrCodes() {
        return flag("view.qr", false);
    }

    public void setQrCodes(boolean on) {
        set("view.qr", String.valueOf(on));
    }

    public Look look() {
        return parse(Look.class, "view.look", Look.NORMAL);
    }

    public void setLook(Look look) {
        set("view.look", look.name());
    }

    public PhotoTimer timer() {
        return parse(PhotoTimer.class, "photo.timer", PhotoTimer.OFF);
    }

    public void setTimer(PhotoTimer timer) {
        set("photo.timer", timer.name());
    }

    public Shots shots() {
        return parse(Shots.class, "photo.shots", Shots.SINGLE);
    }

    public void setShots(Shots shots) {
        set("photo.shots", shots.name());
    }

    public VideoSpeed speed() {
        return parse(VideoSpeed.class, "video.speed", VideoSpeed.NORMAL);
    }

    public void setSpeed(VideoSpeed speed) {
        set("video.speed", speed.name());
    }

    // ---------------------------------------------------------------- helpers

    private <E extends Enum<E>> E parse(Class<E> type, String key, E fallback) {
        try {
            String value = properties.getProperty(key);
            return value == null ? fallback : Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Optional<String> text(String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private boolean flag(String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : !"false".equals(value.strip());
    }

    private void set(String key, String value) {
        properties.setProperty(key, value);
        store();
    }

    private void store() {
        PropertiesFile.store(file, properties, "amade-webcam");
    }
}
