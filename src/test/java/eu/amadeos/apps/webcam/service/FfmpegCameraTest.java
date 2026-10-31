package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegCameraTest {
    static final CameraDevice CAM = new CameraDevice(Path.of("/dev/video0"), "Cam", "");

    @Test
    void cameraCommand() {
        List<String> cmd = new FfmpegCamera(c -> null, false).command(CAM, new VideoMode("MJPG", 1280, 720, 30));
        assertEquals(List.of("ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error", "-f", "v4l2",
                "-input_format", "mjpeg", "-video_size", "1280x720", "-framerate", "30", "-i", "/dev/video0",
                "-vf", "scale=1280:720", "-f", "rawvideo", "-pix_fmt", "bgr24", "pipe:1"), cmd);
    }

    @Test
    void testPictureCommand() {
        List<String> cmd = new FfmpegCamera(c -> null, true).command(CAM, new VideoMode("YUYV", 640, 480, 15));
        assertTrue(cmd.containsAll(List.of("lavfi", "testsrc2=size=640x480:rate=15")), cmd.toString());
    }

    @Test
    void stillPictureCommand() {
        List<String> cmd = new FfmpegCamera(c -> null, true, Optional.of(Path.of("/tmp/scene.png")))
                .command(CAM, new VideoMode("MJPG", 1280, 720, 30));
        assertTrue(String.join(" ", cmd).contains("-re -loop 1 -framerate 30 -i /tmp/scene.png"), cmd.toString());
    }

    @Test
    void failuresAreClassified() {
        assertEquals(FrameSource.Failure.BUSY, FfmpegCamera.classify(
                "[video4linux2,v4l2 @ 0x5] ioctl(VIDIOC_STREAMON): Device or resource busy\n/dev/video0: Device or resource busy"));
        assertEquals(FrameSource.Failure.GONE, FfmpegCamera.classify("/dev/video0: No such file or directory"));
        assertEquals(FrameSource.Failure.NOT_SUPPORTED, FfmpegCamera.classify("ioctl(VIDIOC_S_FMT): Invalid argument"));
        assertEquals(FrameSource.Failure.OTHER, FfmpegCamera.classify("Something else"));
        assertEquals("b", FfmpegCamera.lastLine("a\nb\n"));
    }

    @Test
    void missingFfmpegIsReported() throws IOException {
        AtomicReference<Optional<FrameSource.Failure>> failure = new AtomicReference<>();
        new FfmpegCamera(c -> {
            throw new ProgramMissingException("ffmpeg", new IOException("x"));
        }, false).start(CAM, new VideoMode("MJPG", 64, 48, 30), new FrameSource.Listener() {
            @Override
            public void frame(BufferedImage frame) {
            }

            @Override
            public void ended(Optional<FrameSource.Failure> f, String detail) {
                failure.set(f);
            }
        });
        assertEquals(Optional.of(FrameSource.Failure.PROGRAM_MISSING), failure.get());
    }

    @Test
    void anEndingProgramWithAMessageIsAFailure() throws Exception {
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<Optional<FrameSource.Failure>> failure = new AtomicReference<>();
        AtomicReference<String> detail = new AtomicReference<>();
        CommandRunner runner = c -> FinishedProcess.failing(1, "/dev/video0: Device or resource busy\n");
        new FfmpegCamera(runner, false).start(CAM, new VideoMode("MJPG", 64, 48, 30), new FrameSource.Listener() {
            @Override
            public void frame(BufferedImage frame) {
            }

            @Override
            public void ended(Optional<FrameSource.Failure> f, String d) {
                failure.set(f);
                detail.set(d);
                ended.countDown();
            }
        });
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(Optional.of(FrameSource.Failure.BUSY), failure.get());
        assertEquals("/dev/video0: Device or resource busy", detail.get());
    }

    /** The real ffmpeg with its test picture: frames arrive in the size asked for, and stop ends quietly. */
    @Test
    void realFramesFromTheTestPicture() throws Exception {
        List<BufferedImage> frames = new CopyOnWriteArrayList<>();
        CountDownLatch three = new CountDownLatch(3);
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<Optional<FrameSource.Failure>> failure = new AtomicReference<>();
        FrameSource.Running running = new FfmpegCamera(CommandRunner.system(), true).start(CAM,
                new VideoMode("MJPG", 320, 180, 30), new FrameSource.Listener() {
                    @Override
                    public void frame(BufferedImage frame) {
                        frames.add(frame);
                        three.countDown();
                    }

                    @Override
                    public void ended(Optional<FrameSource.Failure> f, String d) {
                        failure.set(f);
                        ended.countDown();
                    }
                });
        assertTrue(three.await(15, TimeUnit.SECONDS), "frames arrive");
        running.stop();
        assertTrue(ended.await(10, TimeUnit.SECONDS));
        assertEquals(Optional.empty(), failure.get());
        BufferedImage f = frames.getFirst();
        assertEquals(320, f.getWidth());
        assertEquals(180, f.getHeight());
        assertEquals(BufferedImage.TYPE_3BYTE_BGR, f.getType());
    }
}
