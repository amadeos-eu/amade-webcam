package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.commons.process.CommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecorderTest {
    private static Recorder.Settings settings(Path file, VideoFormat format, VideoSpeed speed, Optional<String> mic,
                                              boolean test) {
        return new Recorder.Settings(file, format, 320, 240, 30, speed, mic, test);
    }

    @Test
    void commandWithMicrophone() {
        List<String> cmd = Recorder.command(settings(Path.of("/v/a.mp4"), VideoFormat.MP4, VideoSpeed.NORMAL,
                Optional.of("default"), false));
        String line = String.join(" ", cmd);
        assertTrue(line.contains("-f rawvideo -pix_fmt bgr24 -video_size 320x240 -use_wallclock_as_timestamps 1 -i pipe:0"), line);
        assertTrue(line.contains("-f pulse -i default"), line);
        assertTrue(line.contains("-map 0:v -map 1:a"), line);
        assertTrue(line.contains("-c:v libx264"), line);
        assertTrue(line.contains("-c:a aac"), line);
        assertTrue(line.contains("-shortest"), line);
        assertEquals("/v/a.mp4", cmd.getLast());
    }

    @Test
    void timeLapseHasNoSoundAndAFixedRate() {
        Recorder.Settings s = settings(Path.of("/v/a.webm"), VideoFormat.WEBM, VideoSpeed.LAPSE_5,
                Optional.of("default"), true);
        assertFalse(s.withSound());
        String line = String.join(" ", Recorder.command(s));
        assertTrue(line.contains("-framerate 30 -i pipe:0"), line);
        assertFalse(line.contains("pulse"), line);
        assertFalse(line.contains("-shortest"), line);
        assertTrue(line.contains("libvpx-vp9"), line);
    }

    @Test
    void withoutSound() {
        String line = String.join(" ", Recorder.command(settings(Path.of("/v/a.mp4"), VideoFormat.MP4,
                VideoSpeed.NORMAL, Optional.empty(), false)));
        assertFalse(line.contains("1:a"), line);
        assertFalse(line.contains("-c:a"), line);
    }

    @Test
    void levels() {
        assertEquals(0.5, Recorder.parseLevel("lavfi.astats.Overall.Peak_level=-30.000000").orElseThrow(), 1e-9);
        assertEquals(1.0, Recorder.parseLevel("lavfi.astats.Overall.Peak_level=0.0").orElseThrow(), 1e-9);
        assertEquals(0.0, Recorder.parseLevel("lavfi.astats.Overall.Peak_level=-inf").orElseThrow(), 1e-9);
        assertEquals(0.0, Recorder.parseLevel("lavfi.astats.Overall.Peak_level=-90").orElseThrow(), 1e-9);
        assertTrue(Recorder.parseLevel("frame:3 pts:14400 pts_time:0.3").isEmpty());
        assertTrue(Recorder.parseLevel("lavfi.astats.Overall.Peak_level=x").isEmpty());
    }

    @Test
    void timeLapseKeepsOneFrameEveryFewSeconds(@TempDir Path dir) throws Exception {
        AtomicLong now = new AtomicLong(1_000_000);
        Recorder recorder = new Recorder(c -> new eu.amadeos.commons.process.FinishedProcess("", "", 0), now::get);
        Recorder.Recording rec = recorder.start(settings(dir.resolve("t.mp4"), VideoFormat.MP4, VideoSpeed.LAPSE_5,
                Optional.empty(), false), v -> { });
        BufferedImage frame = picture(Color.RED);
        assertTrue(rec.offer(frame));
        now.addAndGet(4000);
        assertFalse(rec.offer(frame));
        now.addAndGet(1000);
        assertTrue(rec.offer(frame));
        assertFalse(rec.offer(new BufferedImage(10, 10, BufferedImage.TYPE_3BYTE_BGR)), "wrong size");
        assertEquals(5000, rec.elapsed());
        rec.stop().get(10, TimeUnit.SECONDS);
        assertFalse(rec.offer(frame), "no frames after stop");
    }

    private static BufferedImage picture(Color color) {
        BufferedImage f = new BufferedImage(320, 240, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = f.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 320, 240);
        g.dispose();
        return f;
    }

    /** ffprobe's stream types of the file, "video audio". */
    private static String streams(Path file) throws Exception {
        Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of",
                "csv=p=0", file.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip().replace('\n', ' ');
        p.waitFor(10, TimeUnit.SECONDS);
        return out;
    }

    /** A real recording with the real ffmpeg: one and a half seconds of frames and the test tone. */
    @Test
    void realMp4WithSound(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/clip.mp4");
        AtomicLong levels = new AtomicLong();
        Recorder.Recording rec = new Recorder(CommandRunner.system()).start(settings(file, VideoFormat.MP4,
                VideoSpeed.NORMAL, Optional.empty(), true), v -> levels.incrementAndGet());
        long end = System.currentTimeMillis() + 1500;
        int i = 0;
        while (System.currentTimeMillis() < end) {
            rec.offer(picture(i++ % 2 == 0 ? Color.BLUE : Color.YELLOW));
            Thread.sleep(33);
        }
        assertEquals(file, rec.stop().get(30, TimeUnit.SECONDS));
        assertTrue(Files.size(file) > 1000);
        assertEquals("video audio", streams(file));
        assertTrue(levels.get() > 0, "the level meter got values");
        assertTrue(rec.frames() > 10);
    }

    @Test
    void realWebmTimeLapse(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lapse.webm");
        AtomicLong now = new AtomicLong();
        Recorder.Recording rec = new Recorder(CommandRunner.system(), now::get).start(settings(file,
                VideoFormat.WEBM, VideoSpeed.LAPSE_1, Optional.empty(), false), v -> { });
        for (int i = 0; i < 12; i++) {
            rec.offer(picture(new Color(i * 20, 80, 160)));
            now.addAndGet(1000);
        }
        rec.stop().get(30, TimeUnit.SECONDS);
        assertEquals("video", streams(file));
        assertEquals(12, rec.frames());
        assertTrue(rec.fileSize() > 0);
        assertTrue(Duration.ofMillis(rec.elapsed()).toSeconds() >= 12);
    }
}
