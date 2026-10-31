package eu.amadeos.apps.webcam.repository;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebcamPreferencesTest {
    static final CameraDevice CAM = new CameraDevice(Path.of("/dev/video0"), "Integrated Camera", "04f2:b6dd");

    @Test
    void defaults(@TempDir Path dir) {
        WebcamPreferences p = new WebcamPreferences(dir.resolve("webcam.properties"));
        assertEquals(VideoFormat.MP4, p.videoFormat(), "MP4 plays everywhere");
        assertEquals(PhotoFormat.JPEG, p.photoFormat());
        assertEquals("default", p.microphone());
        assertTrue(p.mirror());
        assertFalse(p.grid());
        assertFalse(p.qrCodes());
        assertTrue(p.shutterSound());
        assertTrue(p.flash());
        assertFalse(p.saveMirrored());
        assertEquals(Look.NORMAL, p.look());
        assertEquals(PhotoTimer.OFF, p.timer());
        assertEquals(Shots.SINGLE, p.shots());
        assertEquals(VideoSpeed.NORMAL, p.speed());
        assertTrue(p.lastCamera().isEmpty());
        assertTrue(p.mode(CAM).isEmpty());
        assertTrue(p.photoFolder().isEmpty());
        assertTrue(p.controls(CAM).isEmpty());
    }

    @Test
    void everythingIsKept(@TempDir Path dir) {
        Path file = dir.resolve("webcam.properties");
        WebcamPreferences p = new WebcamPreferences(file);
        p.setLastCamera(CAM);
        p.setMode(CAM, new VideoMode("YUYV", 640, 480, 7.5));
        p.setControl(CAM, "brightness", 20);
        p.setControl(CAM, "white_balance_automatic", 0);
        p.setVideoFormat(VideoFormat.WEBM);
        p.setPhotoFormat(PhotoFormat.PNG);
        p.setMicrophone(WebcamPreferences.NO_SOUND);
        p.setPhotoFolder(dir.resolve("p"));
        p.setVideoFolder(dir.resolve("v"));
        p.setMirror(false);
        p.setGrid(true);
        p.setQrCodes(true);
        p.setShutterSound(false);
        p.setFlash(false);
        p.setSaveMirrored(true);
        p.setLook(Look.SEPIA);
        p.setTimer(PhotoTimer.FIVE);
        p.setShots(Shots.BURST_10);
        p.setSpeed(VideoSpeed.LAPSE_5);

        WebcamPreferences q = new WebcamPreferences(file);
        assertEquals(Optional.of("04f2-b6dd"), q.lastCamera());
        assertEquals(Optional.of(new VideoMode("YUYV", 640, 480, 7.5)), q.mode(CAM));
        assertEquals(Map.of("brightness", 20, "white_balance_automatic", 0), q.controls(CAM));
        assertEquals(VideoFormat.WEBM, q.videoFormat());
        assertEquals(PhotoFormat.PNG, q.photoFormat());
        assertEquals("none", q.microphone());
        assertEquals(Optional.of(dir.resolve("p").toAbsolutePath()), q.photoFolder());
        assertEquals(Optional.of(dir.resolve("v").toAbsolutePath()), q.videoFolder());
        assertFalse(q.mirror());
        assertTrue(q.grid());
        assertTrue(q.qrCodes());
        assertFalse(q.shutterSound());
        assertFalse(q.flash());
        assertTrue(q.saveMirrored());
        assertEquals(Look.SEPIA, q.look());
        assertEquals(PhotoTimer.FIVE, q.timer());
        assertEquals(Shots.BURST_10, q.shots());
        assertEquals(VideoSpeed.LAPSE_5, q.speed());

        q.clearControls(CAM);
        assertTrue(new WebcamPreferences(file).controls(CAM).isEmpty());
    }

    @Test
    void brokenValuesFallBack(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("webcam.properties");
        Files.writeString(file, """
                view.look=PURPLE
                photo.timer=99
                camera.04f2-b6dd.mode=MJPG big 30
                camera.04f2-b6dd.control.brightness=lots
                camera.04f2-b6dd.control.contrast=5
                video.format=avi
                """);
        WebcamPreferences p = new WebcamPreferences(file);
        assertEquals(Look.NORMAL, p.look());
        assertEquals(PhotoTimer.OFF, p.timer());
        assertTrue(p.mode(CAM).isEmpty());
        assertEquals(Map.of("contrast", 5), p.controls(CAM));
        assertEquals(VideoFormat.MP4, p.videoFormat());
    }

    @Test
    void modeText() {
        assertEquals(Optional.of(new VideoMode("MJPG", 1280, 720, 30)), WebcamPreferences.parseMode("MJPG 1280x720 30"));
        assertTrue(WebcamPreferences.parseMode("MJPG 1280x720").isEmpty());
        assertTrue(WebcamPreferences.parseMode("MJPG 0x720 30").isEmpty());
    }
}
