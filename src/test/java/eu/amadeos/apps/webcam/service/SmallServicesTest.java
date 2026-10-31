package eu.amadeos.apps.webcam.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallServicesTest {
    @Test
    void microphonesWithoutMonitors() {
        String json = """
                [{"index":54,"name":"alsa_output.pci-0000_00_1f.3.analog-stereo.monitor","description":"Monitor of Built-in Audio"},
                 {"index":55,"name":"alsa_input.pci-0000_00_1f.3.analog-stereo","description":"Built-in Audio Analog Stereo"},
                 {"index":60,"name":"alsa_input.usb-046d_HD_Webcam","description":""}]
                """;
        assertEquals(List.of(new Microphones.Microphone("alsa_input.pci-0000_00_1f.3.analog-stereo",
                        "Built-in Audio Analog Stereo"),
                new Microphones.Microphone("alsa_input.usb-046d_HD_Webcam", "alsa_input.usb-046d_HD_Webcam")),
                Microphones.parse(json));
    }

    @Test
    void microphonesFromPactl() {
        List<String>[] asked = new List[1];
        Microphones mics = new Microphones(c -> {
            asked[0] = c;
            return FinishedProcess.printing("[{\"name\":\"mic\",\"description\":\"Mic\"}]");
        });
        assertEquals(1, mics.list().size());
        assertEquals(List.of("pactl", "-f", "json", "list", "sources"), asked[0]);
        assertTrue(new Microphones(c -> FinishedProcess.failing(1, "Connection refused")).list().isEmpty());
        assertTrue(new Microphones(c -> FinishedProcess.printing("not json")).list().isEmpty());
    }

    /** A code drawn into a camera-like frame, a bit off centre and with a grey background. */
    static BufferedImage frameWithCode(String text, int width, int height) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
        BufferedImage f = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = f.createGraphics();
        g.setColor(new Color(0x9a9080));
        g.fillRect(0, 0, width, height);
        int ox = width / 2 - 100, oy = height / 2 - 160;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                g.setColor(matrix.get(x, y) ? new Color(20, 20, 20) : new Color(235, 235, 230));
                g.fillRect(ox + x, oy + y, 1, 1);
            }
        }
        g.dispose();
        return f;
    }

    @Test
    void qrCodeInAFrame() throws Exception {
        QrReader reader = new QrReader();
        assertEquals(Optional.of("https://amadeos.eu/webcam"),
                reader.read(frameWithCode("https://amadeos.eu/webcam", 1280, 720)));
        assertEquals(Optional.of("WIFI:S:Home;T:WPA;P:secret;;"),
                reader.read(frameWithCode("WIFI:S:Home;T:WPA;P:secret;;", 640, 480)));
        assertTrue(reader.read(new BufferedImage(640, 480, BufferedImage.TYPE_3BYTE_BGR)).isEmpty());
    }

    @Test
    void qrCodeInAnRgbPicture() throws Exception {
        BufferedImage bgr = frameWithCode("Hello", 640, 480);
        BufferedImage rgb = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(bgr, 0, 0, null);
        assertEquals(Optional.of("Hello"), new QrReader().read(rgb));
    }

    @Test
    void photosAreSavedAsJpegAndPng(@TempDir Path dir) throws Exception {
        BufferedImage f = new BufferedImage(40, 20, BufferedImage.TYPE_3BYTE_BGR);
        f.setRGB(0, 0, Color.RED.getRGB());
        PhotoSaver saver = new PhotoSaver();
        Path jpg = saver.save(f, dir.resolve("a/b/p.jpg"), PhotoFormat.JPEG, false);
        assertEquals(40, ImageIO.read(jpg.toFile()).getWidth());
        Path png = saver.save(f, dir.resolve("p.png"), PhotoFormat.PNG, true);
        BufferedImage back = ImageIO.read(png.toFile());
        assertEquals(Color.RED.getRGB(), back.getRGB(39, 0), "mirrored: the red pixel moved to the right");
        try (var files = Files.list(dir)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void recentMediaNewestFirstWithThumbnails(@TempDir Path dir) throws Exception {
        Path photos = Files.createDirectories(dir.resolve("Pictures/Camera"));
        Path videos = Files.createDirectories(dir.resolve("Videos/Camera"));
        BufferedImage picture = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 3; i++) {
            Path p = photos.resolve("Photo " + i + ".jpg");
            ImageIO.write(picture, "jpg", p.toFile());
            Files.setLastModifiedTime(p, FileTime.fromMillis(1_000_000L * (i + 1)));
        }
        Files.writeString(photos.resolve("notes.txt"), "x");
        Files.writeString(photos.resolve(".Photo x.jpg.part"), "x");
        Path video = videos.resolve("Video 1.mp4");
        Process p = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-f", "lavfi", "-i",
                "testsrc2=size=320x180:rate=10", "-t", "1", "-pix_fmt", "yuv420p", video.toString()).start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0);
        Files.setLastModifiedTime(video, FileTime.fromMillis(9_000_000L));
        RecentMedia recent = new RecentMedia(CommandRunner.system());
        List<Path> newest = recent.newest(photos, videos, 3);
        assertEquals(List.of(video, photos.resolve("Photo 2.jpg"), photos.resolve("Photo 1.jpg")), newest);
        assertEquals(RecentMedia.THUMB_WIDTH, recent.thumbnail(newest.get(1)).orElseThrow().getWidth());
        assertEquals(RecentMedia.THUMB_WIDTH, recent.thumbnail(video).orElseThrow().getWidth());
        assertTrue(recent.thumbnail(photos.resolve("notes.txt")).isEmpty());
        assertTrue(recent.newest(dir.resolve("none"), dir.resolve("none2"), 3).isEmpty());
    }

    @Test
    void shutterSoundIsAShortClick() {
        byte[] s = ShutterSound.samples();
        assertEquals((int) (44100 * 0.16) * 2, s.length);
        int loud = 0;
        for (int i = 0; i < 400; i += 2) loud = Math.max(loud, Math.abs((short) ((s[i] & 0xff) | (s[i + 1] << 8))));
        assertTrue(loud > 5000, "starts loud");
        new ShutterSound().play();
    }

    @Test
    void testPictureCameraBehavesLikeOne() {
        TestPictureCameras cams = new TestPictureCameras();
        assertEquals(List.of(TestPictureCameras.CAMERA), cams.cameras());
        assertEquals(TestPictureCameras.MODES, cams.formats(TestPictureCameras.CAMERA));
        CameraControl temp = cams.controls(TestPictureCameras.CAMERA).stream()
                .filter(c -> c.name().equals("white_balance_temperature")).findFirst().orElseThrow();
        assertTrue(temp.inactive());
        cams.set(TestPictureCameras.CAMERA, "white_balance_automatic", 0);
        cams.set(TestPictureCameras.CAMERA, "brightness", 999);
        List<CameraControl> after = cams.controls(TestPictureCameras.CAMERA);
        assertFalse(after.stream().filter(c -> c.name().equals("white_balance_temperature")).findFirst().orElseThrow().inactive());
        assertEquals(255, after.stream().filter(c -> c.name().equals("brightness")).findFirst().orElseThrow().value());
    }
}
