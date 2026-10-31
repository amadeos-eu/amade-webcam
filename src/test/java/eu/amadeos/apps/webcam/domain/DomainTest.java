package eu.amadeos.apps.webcam.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {
    @Test
    void cameraLabelDropsTheRepeatedPart() {
        CameraDevice c = new CameraDevice(Path.of("/dev/video0"), "Integrated Camera: Integrated C", "04f2:b6dd");
        assertEquals("Integrated Camera", c.label());
        assertEquals("04f2-b6dd", c.key());
    }

    @Test
    void cameraWithoutUsbIdIsKeyedByName() {
        CameraDevice c = new CameraDevice(Path.of("/dev/video2"), "HD Pro Webcam C920", null);
        assertEquals("hd-pro-webcam-c920", c.key());
        assertEquals("HD Pro Webcam C920", c.label());
        assertEquals("", c.usbId());
    }

    @Test
    void cameraWithoutNameUsesTheNode() {
        assertEquals("video4", new CameraDevice(Path.of("/dev/video4"), " ", "").label());
    }

    @Test
    void videoModeLabels() {
        VideoMode m = new VideoMode("MJPG", 1280, 720, 30);
        assertEquals("1280 × 720", m.sizeLabel());
        assertEquals("MJPEG", m.formatLabel());
        assertEquals("30 fps", m.fpsLabel());
        assertEquals("30", m.fpsArgument());
        assertEquals("mjpeg", m.ffmpegInputFormat().orElseThrow());
        assertTrue(m.compressed());
        VideoMode slow = new VideoMode("YUYV", 640, 480, 7.5);
        assertEquals("7.5 fps", slow.fpsLabel());
        assertEquals("7.500", slow.fpsArgument());
        assertEquals("yuyv422", slow.ffmpegInputFormat().orElseThrow());
        assertFalse(slow.compressed());
        assertTrue(new VideoMode("ABCD", 1, 1, 1).ffmpegInputFormat().isEmpty());
    }

    @Test
    void videoModeRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new VideoMode("MJPG", 0, 720, 30));
        assertThrows(IllegalArgumentException.class, () -> new VideoMode("MJPG", 1280, 720, 0));
    }

    @Test
    void preferredModeIsCloseTo720pFastAndCompressed() {
        List<VideoMode> modes = List.of(new VideoMode("YUYV", 1280, 720, 10), new VideoMode("MJPG", 1280, 720, 30),
                new VideoMode("MJPG", 1920, 1080, 30), new VideoMode("YUYV", 640, 480, 30));
        assertEquals(new VideoMode("MJPG", 1280, 720, 30), VideoMode.preferred(modes).orElseThrow());
        assertEquals(new VideoMode("YUYV", 640, 480, 30),
                VideoMode.preferred(List.of(new VideoMode("YUYV", 640, 480, 30), new VideoMode("YUYV", 320, 240, 30)))
                        .orElseThrow());
        assertTrue(VideoMode.preferred(List.of()).isEmpty());
    }

    @Test
    void controlClampsToRangeAndStep() {
        CameraControl c = new CameraControl("white_balance_temperature", CameraControl.Type.INTEGER, 2800, 6500, 10,
                4600, 4600, true, Map.of());
        assertEquals(2800, c.clamp(100));
        assertEquals(6500, c.clamp(9999));
        assertEquals(4610, c.clamp(4606));
        CameraControl b = new CameraControl("white_balance_automatic", CameraControl.Type.BOOLEAN, 0, 1, 1, 1, 1,
                false, Map.of());
        assertEquals(1, b.clamp(7));
        assertEquals(0, b.clamp(0));
        assertTrue(b.isAutomatic());
        assertFalse(c.isAutomatic());
        assertEquals(5000, c.withValue(5000).value());
        assertFalse(c.withInactive(false).inactive());
    }

    @Test
    void automaticControls() {
        for (String name : List.of("auto_exposure", "focus_automatic_continuous", "focus_auto", "exposure_auto",
                "white_balance_temperature_auto")) {
            assertTrue(new CameraControl(name, CameraControl.Type.BOOLEAN, 0, 1, 1, 0, 0, false, Map.of())
                    .isAutomatic(), name);
        }
    }

    @Test
    void fileNamesSortByTimeAndNeverOverwrite(@TempDir Path dir) throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 10, 31, 11, 42, 7);
        Path first = FileNames.photo(dir, t, PhotoFormat.JPEG, 0);
        assertEquals("Photo 2026-10-31 11-42-07.jpg", first.getFileName().toString());
        Files.createFile(first);
        assertEquals("Photo 2026-10-31 11-42-07 (1).jpg", FileNames.photo(dir, t, PhotoFormat.JPEG, 0).getFileName().toString());
        assertEquals("Photo 2026-10-31 11-42-07 (3).png", FileNames.photo(dir, t, PhotoFormat.PNG, 3).getFileName().toString());
        assertEquals("Video 2026-10-31 11-42-07.mp4", FileNames.video(dir, t, VideoFormat.MP4, false).getFileName().toString());
        assertEquals("Time Lapse 2026-10-31 11-42-07.webm",
                FileNames.video(dir, t, VideoFormat.WEBM, true).getFileName().toString());
    }

    @Test
    void mediaFiles() {
        assertTrue(FileNames.isPhoto(Path.of("a.JPG")));
        assertTrue(FileNames.isPhoto(Path.of("a.png")));
        assertTrue(FileNames.isVideo(Path.of("a.webm")));
        assertTrue(FileNames.isMedia(Path.of("a.mp4")));
        assertFalse(FileNames.isMedia(Path.of("a.txt")));
    }

    @Test
    void formatsParse() {
        assertEquals(PhotoFormat.PNG, PhotoFormat.parse(" png ", PhotoFormat.JPEG));
        assertEquals(PhotoFormat.JPEG, PhotoFormat.parse("tiff", PhotoFormat.JPEG));
        assertEquals(PhotoFormat.JPEG, PhotoFormat.parse(null, PhotoFormat.JPEG));
        assertEquals(VideoFormat.WEBM, VideoFormat.parse("webm", VideoFormat.MP4));
        assertEquals(VideoFormat.MP4, VideoFormat.parse("avi", VideoFormat.MP4));
        assertEquals("jpg", PhotoFormat.JPEG.extension());
        assertEquals("jpeg", PhotoFormat.JPEG.imageIoName());
        assertTrue(VideoFormat.MP4.container().contains("+faststart"));
        assertTrue(VideoFormat.WEBM.videoCodec().contains("libvpx-vp9"));
        assertTrue(VideoFormat.WEBM.audioCodec().contains("libopus"));
    }

    @Test
    void timersShotsAndSpeeds() {
        assertEquals(0, PhotoTimer.OFF.seconds());
        assertEquals(10, PhotoTimer.TEN.seconds());
        assertEquals(5, Shots.BURST_5.count());
        assertFalse(VideoSpeed.NORMAL.isTimeLapse());
        assertTrue(VideoSpeed.LAPSE_5.isTimeLapse());
        assertEquals(30, VideoSpeed.LAPSE_30.secondsPerPicture());
        assertEquals("look.black-and-white", Look.BLACK_AND_WHITE.textKey());
    }
}
