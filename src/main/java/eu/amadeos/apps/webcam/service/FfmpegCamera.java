package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads a camera with ffmpeg: ffmpeg opens the V4L2 device, decodes MJPEG or YUYV and writes raw BGR frames to
 * its standard output, which this class turns into images. No native library is needed, and the frames are
 * plain {@link BufferedImage}s that Swing draws and ImageIO saves.
 *
 * <p>With {@code testPicture} set, ffmpeg's moving test picture replaces the camera; tests, screenshots and
 * machines without a camera use it.
 */
// --8<-- [start:class]
public class FfmpegCamera implements FrameSource {
    private static final Logger LOG = LoggerFactory.getLogger(FfmpegCamera.class);

    private final CommandRunner runner;
    private final boolean testPicture;
    private final java.util.Optional<java.nio.file.Path> stillPicture;

    public FfmpegCamera(CommandRunner runner, boolean testPicture) {
        this(runner, testPicture, java.util.Optional.empty());
    }

    /** With {@code stillPicture}, the test picture is that image instead of ffmpeg's colour pattern. */
    public FfmpegCamera(CommandRunner runner, boolean testPicture, java.util.Optional<java.nio.file.Path> stillPicture) {
        this.runner = runner;
        this.testPicture = testPicture;
        this.stillPicture = stillPicture;
    }

    /** The ffmpeg command for the camera and mode. */
    public List<String> command(CameraDevice camera, VideoMode mode) {
        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "error"));
        if (testPicture && stillPicture.isPresent()) {
            cmd.addAll(List.of("-re", "-loop", "1", "-framerate", mode.fpsArgument(), "-i",
                    stillPicture.get().toString()));
        } else if (testPicture) {
            cmd.addAll(List.of("-re", "-f", "lavfi", "-i", "testsrc2=size=" + mode.width() + "x" + mode.height()
                    + ":rate=" + mode.fpsArgument()));
        } else {
            cmd.addAll(List.of("-f", "v4l2"));
            mode.ffmpegInputFormat().ifPresent(f -> cmd.addAll(List.of("-input_format", f)));
            cmd.addAll(List.of("-video_size", mode.width() + "x" + mode.height(),
                    "-framerate", mode.fpsArgument(), "-i", camera.node().toString()));
        }
        // The scale keeps the frame size fixed even if the camera delivers another one than asked for.
        cmd.addAll(List.of("-vf", "scale=" + mode.width() + ":" + mode.height(),
                "-f", "rawvideo", "-pix_fmt", "bgr24", "pipe:1"));
        return cmd;
    }
    // --8<-- [end:class]

    @Override
    public Running start(CameraDevice camera, VideoMode mode, Listener listener) throws IOException {
        Process process;
        try {
            process = runner.start(command(camera, mode));
        } catch (ProgramMissingException e) {
            listener.ended(Optional.of(Failure.PROGRAM_MISSING), "ffmpeg");
            return () -> { };
        }
        process.getOutputStream().close();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread.ofVirtual().name("ffmpeg-errors").start(() -> keepTail(process.getErrorStream(), errors));
        Reader reader = new Reader(process, mode, listener, errors);
        Thread.ofPlatform().daemon().name("camera-frames").start(reader);
        return reader::stop;
    }

    // --8<-- [start:reader]
    private static final class Reader implements Runnable {
        private final Process process;
        private final VideoMode mode;
        private final Listener listener;
        private final ByteArrayOutputStream errors;
        private volatile boolean stopped;

        Reader(Process process, VideoMode mode, Listener listener, ByteArrayOutputStream errors) {
            this.process = process;
            this.mode = mode;
            this.listener = listener;
            this.errors = errors;
        }

        @Override
        public void run() {
            int frameBytes = mode.width() * mode.height() * 3;
            try (InputStream in = process.getInputStream()) {
                while (!stopped) {
                    BufferedImage frame = new BufferedImage(mode.width(), mode.height(), BufferedImage.TYPE_3BYTE_BGR);
                    byte[] pixels = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
                    if (in.readNBytes(pixels, 0, frameBytes) < frameBytes) break;
                    listener.frame(frame);
                }
            } catch (IOException e) {
                if (!stopped) LOG.debug("Camera stream ended", e);
            } catch (RuntimeException e) {
                LOG.warn("Frame listener failed", e);
            }
            finish();
        }
        // --8<-- [end:reader]

        void stop() {
            stopped = true;
            process.destroy();
        }

        private void finish() {
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            if (stopped) {
                listener.ended(Optional.empty(), "");
                return;
            }
            String detail;
            synchronized (errors) {
                detail = errors.toString(java.nio.charset.StandardCharsets.UTF_8).strip();
            }
            listener.ended(Optional.of(classify(detail)), lastLine(detail));
        }
    }

    // --8<-- [start:classify]
    /** What ffmpeg's message means for the user. */
    public static Failure classify(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("device or resource busy")) return Failure.BUSY;
        if (m.contains("no such file or directory") || m.contains("no such device")) return Failure.GONE;
        if (m.contains("invalid argument") || m.contains("not supported") || m.contains("cannot find a proper")) {
            return Failure.NOT_SUPPORTED;
        }
        return Failure.OTHER;
    }
    // --8<-- [end:classify]

    static String lastLine(String text) {
        String[] lines = text.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].strip();
    }

    /** Keeps only the last 4 KB of ffmpeg's messages. */
    private static void keepTail(InputStream in, ByteArrayOutputStream out) {
        byte[] buffer = new byte[1024];
        try (in) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                synchronized (out) {
                    if (out.size() > 4096) {
                        byte[] all = out.toByteArray();
                        out.reset();
                        out.write(all, all.length - 2048, 2048);
                    }
                    out.write(buffer, 0, n);
                }
            }
        } catch (IOException e) {
            // ffmpeg ended
        }
    }
}
