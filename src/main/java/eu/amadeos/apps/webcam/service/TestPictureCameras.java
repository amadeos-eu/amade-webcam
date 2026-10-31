package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.commons.process.CommandRunner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One make-believe camera for {@code --test-picture}: with {@link FfmpegCamera} in test mode it shows ffmpeg's
 * moving test picture. Its controls only remember their values. Used for tests, screenshots and trying the
 * program on a computer without a camera.
 */
public class TestPictureCameras extends CameraFinder {
    public static final CameraDevice CAMERA = new CameraDevice(Path.of("/dev/video-test"), "Test Picture", "");
    public static final List<VideoMode> MODES = List.of(
            new VideoMode("MJPG", 1280, 720, 30), new VideoMode("MJPG", 640, 360, 30),
            new VideoMode("YUYV", 640, 480, 30), new VideoMode("YUYV", 640, 480, 15));

    private final Map<String, CameraControl> controls = new ConcurrentHashMap<>();

    public TestPictureCameras() {
        super(Path.of("/nonexistent"), Path.of("/dev"), CommandRunner.system());
        for (CameraControl c : List.of(
                new CameraControl("brightness", CameraControl.Type.INTEGER, 0, 255, 1, 128, 128, false, Map.of()),
                new CameraControl("contrast", CameraControl.Type.INTEGER, 0, 255, 1, 32, 32, false, Map.of()),
                new CameraControl("saturation", CameraControl.Type.INTEGER, 0, 100, 1, 64, 64, false, Map.of()),
                new CameraControl("sharpness", CameraControl.Type.INTEGER, 0, 7, 1, 3, 3, false, Map.of()),
                new CameraControl("white_balance_automatic", CameraControl.Type.BOOLEAN, 0, 1, 1, 1, 1, false, Map.of()),
                new CameraControl("white_balance_temperature", CameraControl.Type.INTEGER, 2800, 6500, 10, 4600, 4600,
                        true, Map.of()),
                new CameraControl("auto_exposure", CameraControl.Type.MENU, 0, 3, 1, 3, 3, false,
                        Map.of(1, "Manual Mode", 3, "Aperture Priority Mode")),
                new CameraControl("exposure_time_absolute", CameraControl.Type.INTEGER, 1, 5000, 1, 157, 157, true,
                        Map.of()),
                new CameraControl("power_line_frequency", CameraControl.Type.MENU, 0, 2, 1, 1, 1, false,
                        Map.of(0, "Disabled", 1, "50 Hz", 2, "60 Hz")))) {
            controls.put(c.name(), c);
        }
    }

    @Override
    public List<CameraDevice> nodes() {
        return List.of(CAMERA);
    }

    @Override
    public List<CameraDevice> cameras() {
        return List.of(CAMERA);
    }

    @Override
    public List<VideoMode> formats(CameraDevice camera) {
        return MODES;
    }

    @Override
    public List<CameraControl> controls(CameraDevice camera) {
        List<CameraControl> list = new ArrayList<>();
        for (String name : CameraControl.SHOWN) {
            if (controls.containsKey(name)) list.add(controls.get(name));
        }
        return list;
    }

    @Override
    public void set(CameraDevice camera, String control, int value) {
        controls.computeIfPresent(control, (k, c) -> c.withValue(c.clamp(value)));
        // Like a real camera: the manual value is active only while the automatic mode is off.
        CameraControl wb = controls.get("white_balance_automatic");
        controls.computeIfPresent("white_balance_temperature", (k, c) -> c.withInactive(wb.value() != 0));
        CameraControl ae = controls.get("auto_exposure");
        controls.computeIfPresent("exposure_time_absolute", (k, c) -> c.withInactive(ae.value() != 1));
    }
}
