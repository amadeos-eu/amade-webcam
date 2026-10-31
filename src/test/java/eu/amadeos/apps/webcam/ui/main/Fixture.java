package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.bootstrap.WebcamModule;
import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.repository.WebcamPreferences;
import eu.amadeos.apps.webcam.service.CameraFinder;
import eu.amadeos.apps.webcam.service.FrameSource;
import eu.amadeos.apps.webcam.service.Microphones;
import eu.amadeos.apps.webcam.service.PhotoSaver;
import eu.amadeos.apps.webcam.service.QrReader;
import eu.amadeos.apps.webcam.service.RecentMedia;
import eu.amadeos.apps.webcam.service.Recorder;
import eu.amadeos.apps.webcam.service.ShutterSound;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.FinishedProcess;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A presenter wired to fakes: no devices, no programs, a clock moved by hand, everything on one thread. */
final class Fixture {
    static final CameraDevice CAM_A = new CameraDevice(Path.of("/dev/video0"), "Integrated Camera: Integrated C", "04f2:b6dd");
    static final CameraDevice CAM_B = new CameraDevice(Path.of("/dev/video2"), "USB Webcam", "046d:0825");
    static final List<VideoMode> MODES = List.of(new VideoMode("MJPG", 1280, 720, 30),
            new VideoMode("MJPG", 640, 480, 30), new VideoMode("YUYV", 640, 480, 30));

    final Path home;
    final FakeView view = new FakeView();
    final FakeFinder finder = new FakeFinder();
    final FakeSource source = new FakeSource();
    final ManualTicker ticker = new ManualTicker();
    final FakeOpener opener = new FakeOpener();
    final List<List<String>> ffmpeg = new CopyOnWriteArrayList<>();
    final WebcamPreferences preferences;
    final int[] exits = {0};
    final Messages msg = Messages.load(WebcamModule.class, WebcamModule.MESSAGES, Locale.ENGLISH);
    String qrText;
    final MainPresenter presenter;

    Fixture(Path home) {
        this(home, null);
    }

    /** With {@code target} the presenter drives that window instead of {@link #view}. */
    Fixture(Path home, MainView target) {
        this.home = home;
        preferences = new WebcamPreferences(home.resolve("webcam.properties"));
        QrReader qr = new QrReader() {
            @Override
            public Optional<String> read(BufferedImage frame) {
                return Optional.ofNullable(qrText);
            }
        };
        Recorder recorder = new Recorder(command -> {
            ffmpeg.add(command);
            return new FinishedProcess("lavfi.astats.Overall.Peak_level=-30\n", "", 0);
        });
        presenter = new MainPresenter(target == null ? view : target, msg, null, new MainPresenter.Services(finder, source, recorder,
                new PhotoSaver(), new RecentMedia(c -> FinishedProcess.failing(1, "")),
                new Microphones(c -> FinishedProcess.printing("[{\"name\":\"usbmic\",\"description\":\"USB Mic\"}]")),
                qr, new ShutterSound() {
                    @Override
                    public void play() {
                    }
                }, opener, preferences, home.resolve("Pictures/Camera"), home.resolve("Videos/Camera"),
                Runnable::run, Runnable::run, ticker, () -> LocalDateTime.of(2026, 10, 31, 12, 0, 0), false,
                () -> exits[0]++));
    }

    static BufferedImage frame() {
        return new BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR);
    }

    /** Cameras, formats and controls as configured; set() is recorded. */
    static final class FakeFinder extends CameraFinder {
        List<CameraDevice> cameras = new ArrayList<>(List.of(CAM_A, CAM_B));
        Map<String, List<VideoMode>> formats = new ConcurrentHashMap<>();
        List<CameraControl> controls = List.of(
                new CameraControl("brightness", CameraControl.Type.INTEGER, 0, 255, 1, 128, 128, false, Map.of()),
                new CameraControl("white_balance_automatic", CameraControl.Type.BOOLEAN, 0, 1, 1, 1, 1, false, Map.of()));
        final List<String> sets = new CopyOnWriteArrayList<>();
        int controlReads;

        FakeFinder() {
            super(Path.of("/nonexistent"), Path.of("/dev"), c -> null);
        }

        @Override
        public List<CameraDevice> nodes() {
            return List.copyOf(cameras);
        }

        @Override
        public List<CameraDevice> cameras() {
            return List.copyOf(cameras);
        }

        @Override
        public List<VideoMode> formats(CameraDevice camera) {
            return formats.getOrDefault(camera.key(), MODES);
        }

        @Override
        public List<CameraControl> controls(CameraDevice camera) {
            controlReads++;
            return controls;
        }

        @Override
        public void set(CameraDevice camera, String control, int value) throws IOException {
            sets.add(camera.node().getFileName() + " " + control + "=" + value);
        }
    }

    /** Keeps the listener so that tests can send frames and failures. */
    static final class FakeSource implements FrameSource {
        Listener listener;
        CameraDevice camera;
        VideoMode mode;
        int starts;
        int stops;

        @Override
        public Running start(CameraDevice cam, VideoMode m, Listener l) {
            camera = cam;
            mode = m;
            listener = l;
            starts++;
            return () -> stops++;
        }

        void send(BufferedImage frame) {
            listener.frame(frame);
        }
    }

    /** Runs what is due when {@link #advance} moves the clock. */
    static final class ManualTicker implements Ticker {
        private record Task(long due, long period, Runnable run, boolean[] cancelled) {
        }

        private final List<Task> tasks = new ArrayList<>();
        long now;

        @Override
        public Cancel after(Duration delay, Runnable task) {
            boolean[] cancelled = {false};
            tasks.add(new Task(now + delay.toMillis(), 0, task, cancelled));
            return () -> cancelled[0] = true;
        }

        @Override
        public Cancel every(Duration period, Runnable task) {
            boolean[] cancelled = {false};
            tasks.add(new Task(now + period.toMillis(), period.toMillis(), task, cancelled));
            return () -> cancelled[0] = true;
        }

        void advance(Duration d) {
            long end = now + d.toMillis();
            while (true) {
                Task next = null;
                for (Task t : tasks) {
                    if (!t.cancelled()[0] && t.due() <= end && (next == null || t.due() < next.due())) next = t;
                }
                if (next == null) break;
                tasks.remove(next);
                now = next.due();
                if (next.period() > 0) tasks.add(new Task(now + next.period(), next.period(), next.run(), next.cancelled()));
                next.run().run();
            }
            now = end;
            for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) if (it.next().cancelled()[0]) it.remove();
        }
    }

    static final class FakeOpener implements Opener {
        final List<String> opened = new ArrayList<>();
        boolean editor = true;

        @Override
        public void open(Path file) {
            opened.add("open " + file);
        }

        @Override
        public boolean has(String program) {
            return editor;
        }

        @Override
        public void openWith(String program, Path file) {
            opened.add(program + " " + file);
        }

        @Override
        public void browse(String uri) {
            opened.add("browse " + uri);
        }
    }
}
