package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.FileNames;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.QrContent;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.apps.webcam.repository.WebcamPreferences;
import eu.amadeos.apps.webcam.service.CameraFinder;
import eu.amadeos.apps.webcam.service.FrameSource;
import eu.amadeos.apps.webcam.service.Microphones;
import eu.amadeos.apps.webcam.service.PhotoSaver;
import eu.amadeos.apps.webcam.service.QrReader;
import eu.amadeos.apps.webcam.service.RecentMedia;
import eu.amadeos.apps.webcam.service.Recorder;
import eu.amadeos.apps.webcam.service.ShutterSound;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * What the webcam window does. Public methods run on the EDT; camera frames arrive on the reading thread and
 * are handed to the window one at a time (a frame that arrives while the window has not drawn the last one
 * replaces it, so a slow screen never builds up a queue).
 */
public final class MainPresenter implements FrameSource.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** How often the cameras are counted, to notice one being plugged in or pulled out. */
    static final Duration PLUG_CHECK = Duration.ofSeconds(2);
    /** How often a frame is searched for a QR code. */
    static final long QR_INTERVAL_MS = 300;
    /** A dismissed code comes back only after it was out of the picture this long. */
    static final long QR_FORGET_MS = 4000;
    static final int RECENT = 3;
    public static final String IMAGE_EDITOR = "amade-imageeditor";

    /** What the presenter uses; tests replace the parts that touch devices, programs and time. */
    public record Services(CameraFinder finder, FrameSource source, Recorder recorder, PhotoSaver saver,
                           RecentMedia recent, Microphones microphones, QrReader qr, ShutterSound sound,
                           Opener opener, WebcamPreferences preferences, Path defaultPhotos, Path defaultVideos,
                           Executor background, Executor ui, Ticker ticker, Supplier<LocalDateTime> clock,
                           boolean testSound, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services s;
    private final WebcamPreferences prefs;

    // EDT state
    private List<CameraDevice> cameras = List.of();
    private List<CameraDevice> nodes = List.of();
    private CameraDevice camera;
    private VideoMode mode;
    private List<VideoMode> modes = List.of();
    private List<CameraControl> controls = List.of();
    private CaptureMode captureMode = CaptureMode.PHOTO;
    private boolean fullScreen;
    private boolean settingsOpen;
    private Ticker.Cancel countdown;
    private Ticker.Cancel recordingClock;
    private Ticker.Cancel plugCheck;
    private boolean shooting;
    private Optional<QrContent> shownQr = Optional.empty();
    private String dismissedQr;
    private long qrLastSeen;
    private List<MainView.Recent> recent = List.of();

    // shared with the frame thread
    private final AtomicInteger generation = new AtomicInteger();
    private volatile FrameSource.Running running;
    private volatile Look look;
    private volatile boolean qrOn;
    private volatile boolean saveMirrored;
    private volatile Recorder.Recording recording;
    private volatile BufferedImage latest;
    private volatile long lastQrScan;
    private final AtomicBoolean qrBusy = new AtomicBoolean();
    private final AtomicReference<BufferedImage> pendingFrame = new AtomicReference<>();
    private final AtomicReference<Double> pendingLevel = new AtomicReference<>();

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.s = services;
        this.prefs = services.preferences();
        this.look = prefs.look();
        this.qrOn = prefs.qrCodes();
        this.saveMirrored = prefs.saveMirrored();
    }

    // ---------------------------------------------------------------- start and cameras

    /** Finds the cameras, opens the one used last (or the first), and starts watching for plugged cameras. */
    public void start() {
        view.showTitle(msg.get("window.title"));
        view.showOptions(options());
        view.showMode(captureMode, false, false);
        view.showNoPicture(MainView.NoPicture.STARTING, "");
        refreshRecent();
        s.background().execute(() -> {
            List<CameraDevice> found = s.finder().cameras();
            List<CameraDevice> seen = s.finder().nodes();
            s.ui().execute(() -> {
                nodes = seen;
                camerasFound(found);
                plugCheck = s.ticker().every(PLUG_CHECK, this::checkPlugged);
            });
        });
    }

    // --8<-- [start:plug]
    /** Runs on the ticker thread: a cheap look at the nodes, the expensive search only when they changed. */
    void checkPlugged() {
        List<CameraDevice> now = s.finder().nodes();
        s.ui().execute(() -> {
            if (now.equals(nodes)) return;
            nodes = now;
            s.background().execute(() -> {
                List<CameraDevice> found = s.finder().cameras();
                s.ui().execute(() -> camerasFound(found));
            });
        });
    }
    // --8<-- [end:plug]

    private void camerasFound(List<CameraDevice> found) {
        cameras = List.copyOf(found);
        boolean currentStays = camera != null && cameras.stream().anyMatch(c -> c.node().equals(camera.node()));
        if (!currentStays) {
            if (camera != null) {
                stopRecordingIfAny();
                stopStream();
                camera = null;
                mode = null;
            }
            Optional<CameraDevice> pick = prefs.lastCamera()
                    .flatMap(key -> cameras.stream().filter(c -> c.key().equals(key)).findFirst())
                    .or(() -> cameras.stream().findFirst());
            if (pick.isPresent()) {
                open(pick.get());
            } else {
                view.showNoPicture(MainView.NoPicture.NO_CAMERA, "");
                view.showMode(captureMode, false, false);
                view.showStatus(msg.get("status.no-camera"));
            }
        }
        view.showCameras(cameras, Optional.ofNullable(camera));
        refreshSettings();
    }

    /** Opens a camera from the Camera menu or the settings. */
    public void chooseCamera(CameraDevice chosen) {
        if (recording != null || (camera != null && camera.node().equals(chosen.node()))) return;
        prefs.setLastCamera(chosen);
        open(chosen);
        view.showCameras(cameras, Optional.of(chosen));
    }

    private void open(CameraDevice chosen) {
        stopStream();
        camera = chosen;
        mode = null;
        view.showNoPicture(MainView.NoPicture.STARTING, "");
        view.showMode(captureMode, false, false);
        s.background().execute(() -> {
            List<VideoMode> available = s.finder().formats(chosen);
            Optional<VideoMode> saved = prefs.mode(chosen).filter(available::contains);
            Optional<VideoMode> pick = saved.or(() -> VideoMode.preferred(available));
            applySavedControls(chosen);
            List<CameraControl> read = s.finder().controls(chosen);
            s.ui().execute(() -> {
                if (camera != chosen) return;
                modes = available;
                controls = read;
                if (pick.isEmpty()) {
                    view.showNoPicture(MainView.NoPicture.NOT_SUPPORTED, "");
                    return;
                }
                startStream(chosen, pick.get());
                refreshSettings();
            });
        });
    }

    /** The values the user set for this camera; a camera forgets them when it is unplugged. */
    private void applySavedControls(CameraDevice chosen) {
        Map<String, Integer> saved = prefs.controls(chosen);
        // Automatic switches first, so that the manual values they release are accepted.
        List<String> order = new ArrayList<>(saved.keySet());
        order.sort((a, b) -> Boolean.compare(!isAutomatic(a), !isAutomatic(b)));
        for (String name : order) {
            try {
                s.finder().set(chosen, name, saved.get(name));
            } catch (IOException e) {
                LOG.debug("Camera refused {}={}: {}", name, saved.get(name), e.getMessage());
            }
        }
    }

    private static boolean isAutomatic(String control) {
        return new CameraControl(control, CameraControl.Type.BOOLEAN, 0, 1, 1, 0, 0, false, Map.of()).isAutomatic();
    }

    private void startStream(CameraDevice chosen, VideoMode chosenMode) {
        mode = chosenMode;
        int gen = generation.incrementAndGet();
        FrameSource.Listener listener = new FrameSource.Listener() {
            @Override
            public void frame(BufferedImage frame) {
                if (generation.get() == gen) MainPresenter.this.frame(frame);
            }

            @Override
            public void ended(Optional<FrameSource.Failure> failure, String detail) {
                if (generation.get() == gen) MainPresenter.this.ended(failure, detail);
            }
        };
        try {
            running = s.source().start(chosen, chosenMode, listener);
        } catch (IOException e) {
            view.showNoPicture(MainView.NoPicture.FAILED, e.getMessage());
            return;
        }
        view.showMode(captureMode, false, true);
        view.showStatus(statusText());
    }

    private void stopStream() {
        generation.incrementAndGet();
        FrameSource.Running r = running;
        running = null;
        latest = null;
        if (r != null) r.stop();
    }

    // ---------------------------------------------------------------- frames (reading thread)

    // --8<-- [start:frame]
    @Override
    public void frame(BufferedImage frame) {
        look.apply(frame);
        latest = frame;
        Recorder.Recording rec = recording;
        if (rec != null) rec.offer(saveMirrored ? PhotoSaver.mirror(frame) : frame);
        if (qrOn) scanForQr(frame);
        // Only one frame waits for the window; a newer one replaces it.
        if (pendingFrame.getAndSet(frame) == null) {
            s.ui().execute(() -> {
                BufferedImage next = pendingFrame.getAndSet(null);
                if (next != null) view.showFrame(next);
            });
        }
    }
    // --8<-- [end:frame]

    @Override
    public void ended(Optional<FrameSource.Failure> failure, String detail) {
        if (failure.isEmpty()) return;
        s.ui().execute(() -> {
            running = null;
            latest = null;
            stopRecordingIfAny();
            view.showNoPicture(MainView.reason(failure.get()), detail);
            view.showMode(captureMode, false, false);
            view.showStatus(msg.get("status.stopped"));
        });
    }

    private void scanForQr(BufferedImage frame) {
        long now = System.currentTimeMillis();
        if (now - lastQrScan < QR_INTERVAL_MS || !qrBusy.compareAndSet(false, true)) return;
        lastQrScan = now;
        s.background().execute(() -> {
            try {
                Optional<String> text = s.qr().read(frame);
                s.ui().execute(() -> qrResult(text, now));
            } finally {
                qrBusy.set(false);
            }
        });
    }

    /** A code found (or none); the banner stays a while after the code leaves the picture. */
    void qrResult(Optional<String> text, long when) {
        if (!qrOn) return;
        if (text.isPresent()) {
            qrLastSeen = when;
            if (text.get().equals(dismissedQr)) return;
            QrContent content = QrContent.of(text.get());
            if (shownQr.map(c -> c.text().equals(content.text())).orElse(false)) return;
            shownQr = Optional.of(content);
            view.showQr(shownQr);
        } else if (dismissedQr != null && when - qrLastSeen > QR_FORGET_MS) {
            dismissedQr = null;
        }
    }

    // ---------------------------------------------------------------- photo

    /** The big button, Space or Enter. */
    public void shutter() {
        if (captureMode == CaptureMode.VIDEO) {
            toggleRecording();
        } else {
            takePhoto();
        }
    }

    public void takePhoto() {
        if (latest == null || shooting || countdown != null) return;
        PhotoTimer timer = prefs.timer();
        if (timer == PhotoTimer.OFF) {
            shoot();
            return;
        }
        int[] left = {timer.seconds()};
        view.showCountdown(left[0], timer.seconds());
        countdown = s.ticker().every(Duration.ofSeconds(1), () -> s.ui().execute(() -> {
            if (countdown == null) return;
            left[0]--;
            if (left[0] > 0) {
                view.showCountdown(left[0], timer.seconds());
            } else {
                cancelCountdown();
                shoot();
            }
        }));
    }

    /** Escape: stops the countdown, leaves full screen. */
    public void escape() {
        if (countdown != null) {
            cancelCountdown();
            view.showStatus(msg.get("status.countdown-cancelled"));
        } else if (fullScreen) {
            toggleFullScreen();
        }
    }

    private void cancelCountdown() {
        if (countdown != null) countdown.cancel();
        countdown = null;
        view.showCountdown(0, 0);
    }

    /** One photo, or a burst. */
    private void shoot() {
        Shots shots = prefs.shots();
        shooting = true;
        LocalDateTime time = s.clock().get();
        Path folder = photoFolder();
        PhotoFormat format = prefs.photoFormat();
        boolean mirrored = saveMirrored;
        List<Path> saved = new ArrayList<>();
        Runnable[] next = new Runnable[1];
        int[] taken = {0};
        next[0] = () -> {
            BufferedImage frame = latest;
            if (frame == null) {
                shooting = false;
                return;
            }
            int number = shots.count() == 1 ? 0 : taken[0] + 1;
            taken[0]++;
            if (prefs.shutterSound()) s.sound().play();
            if (prefs.flash()) view.flash();
            boolean last = taken[0] >= shots.count();
            s.background().execute(() -> {
                try {
                    Path file = s.saver().save(frame, FileNames.photo(folder, time, format, number), format, mirrored);
                    s.ui().execute(() -> {
                        saved.add(file);
                        if (last) photosSaved(saved);
                    });
                } catch (IOException e) {
                    LOG.warn("Could not save the photo", e);
                    s.ui().execute(() -> {
                        shooting = false;
                        view.showError(msg.format("error.save-photo", e.getMessage()));
                    });
                }
            });
            if (!last) s.ticker().after(Duration.ofMillis(Shots.BURST_INTERVAL_MS), () -> s.ui().execute(next[0]));
        };
        next[0].run();
    }

    private void photosSaved(List<Path> files) {
        shooting = false;
        if (files.isEmpty()) return;
        String text = files.size() == 1
                ? msg.format("status.photo-saved", files.getFirst().getFileName())
                : msg.format("status.burst-saved", files.size(), files.getFirst().getParent());
        view.showStatus(text);
        view.announceSaved(text, () -> openRecent(files.getLast()));
        refreshRecent();
    }

    // ---------------------------------------------------------------- video

    public void toggleRecording() {
        if (recording != null) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (latest == null || mode == null) return;
        BufferedImage frame = latest;
        VideoSpeed speed = prefs.speed();
        VideoFormat format = prefs.videoFormat();
        String mic = prefs.microphone();
        Optional<String> microphone = mic.equals(WebcamPreferences.NO_SOUND) ? Optional.empty() : Optional.of(mic);
        Path file = FileNames.video(videoFolder(), s.clock().get(), format, speed.isTimeLapse());
        Recorder.Settings settings = new Recorder.Settings(file, format, frame.getWidth(), frame.getHeight(),
                mode.fps(), speed, s.testSound() ? Optional.empty() : microphone, s.testSound());
        try {
            recording = s.recorder().start(settings, this::level);
        } catch (IOException e) {
            view.showError(msg.format("error.record", e.getMessage()));
            return;
        }
        view.showMode(captureMode, true, true);
        view.showRecording(Optional.of(Duration.ZERO), speed.isTimeLapse());
        recordingClock = s.ticker().every(Duration.ofMillis(250), () -> s.ui().execute(this::recordingTick));
    }

    private void recordingTick() {
        Recorder.Recording rec = recording;
        if (rec == null) return;
        view.showRecording(Optional.of(Duration.ofMillis(rec.elapsed())), rec.settings().speed().isTimeLapse());
        long bytes = rec.fileSize();
        String size = sizeText(bytes);
        // ffmpeg keeps the first seconds in memory while it lines up picture and sound; "0 KB" would look stuck.
        view.showStatus(rec.settings().speed().isTimeLapse()
                ? msg.format("status.time-lapse", rec.frames(), size)
                : bytes < 1024 ? msg.get("status.recording-buffering") : msg.format("status.recording", size));
    }

    /** Called by ffmpeg's level reader; the window gets the newest value only. */
    private void level(double value) {
        if (pendingLevel.getAndSet(value) == null) {
            s.ui().execute(() -> {
                Double next = pendingLevel.getAndSet(null);
                if (next != null) view.showLevel(next);
            });
        }
    }

    private void stopRecording() {
        Recorder.Recording rec = recording;
        recording = null;
        if (recordingClock != null) recordingClock.cancel();
        recordingClock = null;
        view.showRecording(Optional.empty(), false);
        view.showLevel(0);
        view.showMode(captureMode, false, running != null);
        view.showStatus(msg.get("status.finishing"));
        rec.stop().whenComplete((file, error) -> s.ui().execute(() -> {
            if (error != null) {
                LOG.warn("Recording failed", error);
                view.showError(msg.format("error.record", rootMessage(error)));
                view.showStatus(statusText());
            } else {
                String text = msg.format("status.video-saved", file.getFileName());
                view.showStatus(text);
                view.announceSaved(text, () -> openRecent(file));
                refreshRecent();
            }
        }));
    }

    private void stopRecordingIfAny() {
        if (recording != null) stopRecording();
    }

    // ---------------------------------------------------------------- choices

    public void setCaptureMode(CaptureMode newMode) {
        if (recording != null || shooting) {
            view.showMode(captureMode, recording != null, running != null);
            return;
        }
        cancelCountdown();
        captureMode = newMode;
        view.showMode(captureMode, false, running != null);
        view.showOptions(options());
    }

    public void setMirror(boolean on) {
        prefs.setMirror(on);
        view.showOptions(options());
    }

    public void setGrid(boolean on) {
        prefs.setGrid(on);
        view.showOptions(options());
    }

    public void setQrCodes(boolean on) {
        prefs.setQrCodes(on);
        qrOn = on;
        if (!on) {
            shownQr = Optional.empty();
            view.showQr(shownQr);
        }
        view.showOptions(options());
    }

    public void setLook(Look newLook) {
        prefs.setLook(newLook);
        look = newLook;
        view.showOptions(options());
    }

    public void setTimer(PhotoTimer timer) {
        prefs.setTimer(timer);
        view.showOptions(options());
    }

    public void setShots(Shots shots) {
        prefs.setShots(shots);
        view.showOptions(options());
    }

    public void setSpeed(VideoSpeed speed) {
        if (recording != null) return;
        prefs.setSpeed(speed);
        view.showOptions(options());
    }

    Options options() {
        return new Options(prefs.mirror(), prefs.grid(), qrOn, look, prefs.timer(), prefs.shots(), prefs.speed());
    }

    public void toggleFullScreen() {
        fullScreen = !fullScreen;
        view.showFullScreen(fullScreen);
    }

    // ---------------------------------------------------------------- QR banner

    public void qrOpen() {
        shownQr.ifPresent(c -> {
            try {
                if (c.kind() == QrContent.Kind.LINK || c.kind() == QrContent.Kind.MAIL) {
                    s.opener().browse(c.text());
                }
            } catch (IOException e) {
                view.showError(msg.format("error.open", e.getMessage()));
            }
        });
    }

    /** Copies the text; for a WLAN code the password, which is what one types elsewhere. */
    public void qrCopy() {
        shownQr.ifPresent(c -> {
            String text = c.kind() == QrContent.Kind.WIFI && !c.wifiPassword().isEmpty() ? c.wifiPassword() : c.text();
            view.copyText(text);
            view.showStatus(msg.get("status.copied"));
        });
    }

    public void qrDismiss() {
        shownQr.ifPresent(c -> dismissedQr = c.text());
        shownQr = Optional.empty();
        view.showQr(shownQr);
    }

    // ---------------------------------------------------------------- recent shots

    private void refreshRecent() {
        Path photos = photoFolder(), videos = videoFolder();
        s.background().execute(() -> {
            List<MainView.Recent> items = new ArrayList<>();
            for (Path file : s.recent().newest(photos, videos, RECENT)) {
                s.recent().thumbnail(file).ifPresent(t -> items.add(new MainView.Recent(file, t, FileNames.isVideo(file))));
            }
            s.ui().execute(() -> {
                recent = List.copyOf(items);
                view.showRecent(recent);
            });
        });
    }

    public void openRecent(Path file) {
        open(file);
    }

    public boolean canEdit(Path file) {
        return FileNames.isPhoto(file) && s.opener().has(IMAGE_EDITOR);
    }

    public void editRecent(Path file) {
        try {
            s.opener().openWith(IMAGE_EDITOR, file);
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
        }
    }

    public void showInFolder(Path file) {
        open(file.toAbsolutePath().getParent());
    }

    public void deleteRecent(Path file) {
        if (!view.confirmDelete(file.getFileName().toString())) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            view.showError(msg.format("error.delete", e.getMessage()));
        }
        refreshRecent();
    }

    public void openPhotoFolder() {
        openFolder(photoFolder());
    }

    public void openVideoFolder() {
        openFolder(videoFolder());
    }

    private void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
            return;
        }
        open(folder);
    }

    private void open(Path path) {
        try {
            s.opener().open(path);
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
        }
    }

    Path photoFolder() {
        return prefs.photoFolder().orElse(s.defaultPhotos());
    }

    Path videoFolder() {
        return prefs.videoFolder().orElse(s.defaultVideos());
    }

    // ---------------------------------------------------------------- settings dialog

    public void openSettings() {
        settingsOpen = true;
        s.background().execute(() -> {
            List<Microphones.Microphone> mics = s.microphones().list();
            s.ui().execute(() -> {
                microphones = mics;
                view.showSettings(settingsModel());
            });
        });
    }

    private List<Microphones.Microphone> microphones = List.of();

    public void settingsClosed() {
        settingsOpen = false;
    }

    private void refreshSettings() {
        if (settingsOpen) view.showSettings(settingsModel());
    }

    SettingsModel settingsModel() {
        return new SettingsModel(cameras, Optional.ofNullable(camera), modes, Optional.ofNullable(mode), controls,
                microphones, prefs.microphone(), prefs.photoFormat(), prefs.videoFormat(), photoFolder(),
                videoFolder(), prefs.shutterSound(), prefs.flash(), prefs.saveMirrored());
    }

    public void chooseMode(VideoMode chosen) {
        if (camera == null || chosen.equals(mode) || recording != null) return;
        prefs.setMode(camera, chosen);
        stopStream();
        view.showNoPicture(MainView.NoPicture.STARTING, "");
        startStream(camera, chosen);
        refreshSettings();
    }

    /** A slider, box or menu of the camera's own controls. */
    public void setControl(String name, int value) {
        CameraDevice target = camera;
        if (target == null) return;
        prefs.setControl(target, name, value);
        s.background().execute(() -> {
            try {
                s.finder().set(target, name, value);
            } catch (IOException e) {
                LOG.debug("Camera refused {}={}: {}", name, value, e.getMessage());
            }
            // An automatic switch changes which other controls are active; read them again.
            List<CameraControl> read = isAutomatic(name) ? s.finder().controls(target) : null;
            if (read != null) s.ui().execute(() -> {
                if (camera != target) return;
                controls = read;
                refreshSettings();
            });
        });
    }

    /** Sets every control back to what the camera itself starts with. */
    public void cameraDefaults() {
        CameraDevice target = camera;
        if (target == null) return;
        prefs.clearControls(target);
        List<CameraControl> current = controls;
        s.background().execute(() -> {
            for (CameraControl c : current) {
                if (!c.isAutomatic()) continue;
                set(target, c.name(), c.defaultValue());
            }
            for (CameraControl c : current) {
                if (c.isAutomatic()) continue;
                set(target, c.name(), c.defaultValue());
            }
            List<CameraControl> read = s.finder().controls(target);
            s.ui().execute(() -> {
                if (camera != target) return;
                controls = read;
                refreshSettings();
            });
        });
    }

    private void set(CameraDevice target, String name, int value) {
        try {
            s.finder().set(target, name, value);
        } catch (IOException e) {
            LOG.debug("Camera refused {}={}: {}", name, value, e.getMessage());
        }
    }

    public void setMicrophone(String name) {
        prefs.setMicrophone(name);
    }

    public void setPhotoFormat(PhotoFormat format) {
        prefs.setPhotoFormat(format);
    }

    public void setVideoFormat(VideoFormat format) {
        prefs.setVideoFormat(format);
    }

    public void setPhotoFolder(Path folder) {
        prefs.setPhotoFolder(folder);
        refreshRecent();
        refreshSettings();
    }

    public void setVideoFolder(Path folder) {
        prefs.setVideoFolder(folder);
        refreshRecent();
        refreshSettings();
    }

    public void setShutterSound(boolean on) {
        prefs.setShutterSound(on);
    }

    public void setFlash(boolean on) {
        prefs.setFlash(on);
    }

    public void setSaveMirrored(boolean on) {
        prefs.setSaveMirrored(on);
        saveMirrored = on;
    }

    // ---------------------------------------------------------------- the rest

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        Messages commons = Messages.commons(msg.locale());
        view.showStatus(commons.get("language.restart"));
    }

    public void about() {
        view.showAbout();
    }

    public void exit() {
        cancelCountdown();
        if (plugCheck != null) plugCheck.cancel();
        Recorder.Recording rec = recording;
        recording = null;
        stopStream();
        if (rec != null) {
            // The file must be finished before the program ends, or it would not play.
            view.showStatus(msg.get("status.finishing"));
            rec.stop().whenComplete((f, e) -> s.ui().execute(this::closeNow));
        } else {
            closeNow();
        }
    }

    private void closeNow() {
        view.close();
        s.exit().run();
    }

    // ---------------------------------------------------------------- texts

    private String statusText() {
        if (camera == null || mode == null) return msg.get("status.no-camera");
        return msg.format("status.camera", camera.label(), mode.sizeLabel(), mode.fpsLabel(),
                captureMode == CaptureMode.PHOTO ? photoFolder() : videoFolder());
    }

    static String sizeText(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(0, bytes / 1024) + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return Objects.requireNonNullElse(t.getMessage(), t.toString());
    }

    // for tests
    Optional<CameraDevice> camera() {
        return Optional.ofNullable(camera);
    }

    Optional<VideoMode> mode() {
        return Optional.ofNullable(mode);
    }

    boolean recordingRuns() {
        return recording != null;
    }

    CaptureMode captureMode() {
        return captureMode;
    }
}
