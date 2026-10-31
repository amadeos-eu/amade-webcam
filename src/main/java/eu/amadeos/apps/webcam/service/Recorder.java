package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.commons.process.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

/**
 * Records a video: the frames the preview shows go to a second ffmpeg through its standard input, together with
 * the sound of a PipeWire (PulseAudio) source. The camera stays open only once, which matters because most
 * webcams allow only one program to read them.
 *
 * <p>Normal videos stamp each frame with the clock when it arrives, so picture and sound stay together even if
 * the camera delivers fewer frames in a dark room; ffmpeg repeats or drops frames for a constant rate. A time
 * lapse keeps one frame every few seconds, has no sound and plays at the camera's frame rate.
 */
public class Recorder {
    private static final Logger LOG = LoggerFactory.getLogger(Recorder.class);
    /** Frames waiting for the encoder; when it falls behind, newer frames are dropped rather than queued. */
    private static final int QUEUE = 8;
    private static final String LEVEL_KEY = "lavfi.astats.Overall.Peak_level";

    /**
     * @param microphone a PipeWire/PulseAudio source name ("default" for the default source); empty for a video
     *                   without sound
     * @param testSound  a 440 Hz tone instead of a microphone, for tests and machines without sound
     */
    public record Settings(Path file, VideoFormat format, int width, int height, double fps, VideoSpeed speed,
                           Optional<String> microphone, boolean testSound) {
        public Settings {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(speed, "speed");
            microphone = speed.isTimeLapse() ? Optional.empty() : Objects.requireNonNull(microphone, "microphone");
            if (speed.isTimeLapse()) testSound = false;
        }

        public boolean withSound() {
            return microphone.isPresent() || testSound;
        }
    }

    private final CommandRunner runner;
    private final LongSupplier clock;

    public Recorder(CommandRunner runner) {
        this(runner, System::currentTimeMillis);
    }

    public Recorder(CommandRunner runner, LongSupplier clock) {
        this.runner = runner;
        this.clock = clock;
    }

    // --8<-- [start:command]
    /** The ffmpeg command for the recording. */
    public static List<String> command(Settings s) {
        String size = s.width() + "x" + s.height();
        String fps = s.fps() == Math.rint(s.fps()) ? String.valueOf((int) s.fps()) : String.valueOf(s.fps());
        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-thread_queue_size", "64", "-f", "rawvideo", "-pix_fmt", "bgr24", "-video_size", size));
        if (s.speed().isTimeLapse()) {
            cmd.addAll(List.of("-framerate", fps));
        } else {
            cmd.addAll(List.of("-use_wallclock_as_timestamps", "1"));
        }
        cmd.addAll(List.of("-i", "pipe:0"));
        if (s.testSound()) {
            cmd.addAll(List.of("-re", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000"));
        } else if (s.microphone().isPresent()) {
            cmd.addAll(List.of("-thread_queue_size", "1024", "-f", "pulse", "-i", s.microphone().get()));
        }
        cmd.addAll(List.of("-map", "0:v"));
        if (s.withSound()) {
            // The peak level of each tenth of a second goes to standard output for the level meter.
            cmd.addAll(List.of("-map", "1:a", "-af", "asetnsamples=n=4800:p=0,astats=metadata=1:reset=1,"
                    + "ametadata=mode=print:key=" + LEVEL_KEY + ":file=pipe\\\\:1"));
            cmd.addAll(s.format().audioCodec());
        }
        cmd.addAll(List.of("-fps_mode", "cfr", "-r", fps));
        cmd.addAll(s.format().videoCodec());
        cmd.addAll(s.format().container());
        if (s.withSound()) cmd.add("-shortest");
        cmd.add(s.file().toString());
        return cmd;
    }
    // --8<-- [end:command]

    /** Starts ffmpeg. {@code level} gets the microphone level from 0 to 1, on a background thread. */
    public Recording start(Settings settings, DoubleConsumer level) throws IOException {
        Files.createDirectories(settings.file().toAbsolutePath().getParent());
        Process process = runner.start(command(settings));
        return new Recording(settings, process, level, clock);
    }

    /** A recording that runs. */
    public static final class Recording {
        private final Settings settings;
        private final Process process;
        private final LongSupplier clock;
        private final BlockingQueue<BufferedImage> queue = new ArrayBlockingQueue<>(QUEUE);
        private final CompletableFuture<Path> done = new CompletableFuture<>();
        private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        private final long started;
        private volatile boolean stopping;
        private volatile long lastKept = Long.MIN_VALUE;
        private volatile int frames;

        Recording(Settings settings, Process process, DoubleConsumer level, LongSupplier clock) {
            this.settings = settings;
            this.process = process;
            this.clock = clock;
            this.started = clock.getAsLong();
            Thread.ofPlatform().daemon().name("recorder-writer").start(this::write);
            Thread.ofVirtual().name("recorder-level").start(() -> readLevels(process.getInputStream(), level));
            Thread.ofVirtual().name("recorder-errors").start(() -> collect(process.getErrorStream()));
        }

        /**
         * Hands a frame to the encoder; called for every camera frame. A time lapse keeps only one frame every
         * few seconds. Returns true if the frame was kept.
         */
        // --8<-- [start:offer]
        public boolean offer(BufferedImage frame) {
            if (stopping) return false;
            if (settings.speed().isTimeLapse()) {
                long now = clock.getAsLong();
                if (lastKept != Long.MIN_VALUE && now - lastKept < settings.speed().secondsPerPicture() * 1000L) {
                    return false;
                }
                lastKept = now;
            }
            if (frame.getWidth() != settings.width() || frame.getHeight() != settings.height()) return false;
            if (!settings.speed().isTimeLapse()) return queue.offer(frame);
            // A time lapse keeps few frames, and each one counts: wait for room instead of dropping it.
            try {
                return queue.offer(frame, 2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        // --8<-- [end:offer]

        /** Milliseconds since the start. */
        public long elapsed() {
            return clock.getAsLong() - started;
        }

        /** Frames written so far; for a time lapse, the pictures kept. */
        public int frames() {
            return frames;
        }

        public Path file() {
            return settings.file();
        }

        public Settings settings() {
            return settings;
        }

        /** The size of the file so far, 0 if it does not exist yet. */
        public long fileSize() {
            try {
                return Files.size(settings.file());
            } catch (IOException e) {
                return 0;
            }
        }

        /**
         * Ends the recording: the frames still queued are written, then ffmpeg finishes the file. The future
         * completes with the file, or fails with ffmpeg's message.
         */
        public CompletableFuture<Path> stop() {
            stopping = true;
            return done;
        }

        // --8<-- [start:write]
        private void write() {
            try (OutputStream out = process.getOutputStream()) {
                while (!stopping || !queue.isEmpty()) {
                    BufferedImage frame = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame == null) continue;
                    out.write(((DataBufferByte) frame.getRaster().getDataBuffer()).getData());
                    frames++;
                }
            } catch (IOException e) {
                LOG.warn("The encoder stopped taking frames", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Closing standard input ends the picture; with -shortest the sound ends with it.
            finish();
        }
        // --8<-- [end:write]

        private void finish() {
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
                }
                int exit = process.exitValue();
                if (exit == 0 || (Files.exists(settings.file()) && Files.size(settings.file()) > 0 && stopping
                        && exit == 255)) {
                    done.complete(settings.file());
                } else {
                    String message;
                    synchronized (errors) {
                        message = errors.toString(StandardCharsets.UTF_8).strip();
                    }
                    done.completeExceptionally(new IOException(message.isEmpty()
                            ? "ffmpeg ended with code " + exit : FfmpegCamera.lastLine(message)));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                done.completeExceptionally(e);
            } catch (IOException | RuntimeException e) {
                done.completeExceptionally(e);
            }
        }

        private void collect(InputStream in) {
            byte[] buffer = new byte[1024];
            try (in) {
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    synchronized (errors) {
                        if (errors.size() < 16384) errors.write(buffer, 0, n);
                    }
                }
            } catch (IOException e) {
                // ended
            }
        }
    }

    /** Lines like {@code lavfi.astats.Overall.Peak_level=-18.2}. */
    static void readLevels(InputStream in, DoubleConsumer level) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLevel(line).ifPresent(level::accept);
            }
        } catch (IOException e) {
            // ffmpeg ended
        }
    }

    /** The level from 0 (−60 dB or quieter) to 1 (0 dB) of a level line. */
    static java.util.OptionalDouble parseLevel(String line) {
        int at = line.indexOf(LEVEL_KEY + "=");
        if (at < 0) return java.util.OptionalDouble.empty();
        String value = line.substring(at + LEVEL_KEY.length() + 1).strip();
        if (value.equals("-inf")) return java.util.OptionalDouble.of(0);
        try {
            double db = Double.parseDouble(value);
            return java.util.OptionalDouble.of(Math.max(0, Math.min(1, (db + 60) / 60)));
        } catch (NumberFormatException e) {
            return java.util.OptionalDouble.empty();
        }
    }
}
