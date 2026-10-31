package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.QrContent;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Remembers what the presenter showed. */
final class FakeView implements MainView {
    BufferedImage frame;
    int frames;
    NoPicture noPicture;
    String noPictureDetail;
    List<CameraDevice> cameras = List.of();
    Optional<CameraDevice> currentCamera = Optional.empty();
    String status = "";
    String title = "";
    CaptureMode mode;
    boolean recording;
    boolean camera;
    Options options;
    final List<Integer> countdowns = new ArrayList<>();
    Optional<Duration> recordingTime = Optional.empty();
    double level;
    int flashes;
    Optional<QrContent> qr = Optional.empty();
    int qrShown;
    List<Recent> recent = List.of();
    boolean fullScreen;
    SettingsModel settings;
    int settingsShown;
    String copied;
    boolean confirmDelete = true;
    final List<String> errors = new ArrayList<>();
    int abouts;
    boolean closed;

    @Override
    public void showFrame(BufferedImage f) {
        frame = f;
        frames++;
        noPicture = null;
    }

    @Override
    public void showNoPicture(NoPicture reason, String detail) {
        noPicture = reason;
        noPictureDetail = detail;
        frame = null;
    }

    @Override
    public void showCameras(List<CameraDevice> list, Optional<CameraDevice> current) {
        cameras = list;
        currentCamera = current;
    }

    @Override
    public void showStatus(String text) {
        status = text;
    }

    @Override
    public void showTitle(String t) {
        title = t;
    }

    @Override
    public void showMode(CaptureMode m, boolean rec, boolean cam) {
        mode = m;
        recording = rec;
        camera = cam;
    }

    @Override
    public void showOptions(Options o) {
        options = o;
    }

    @Override
    public void showCountdown(int secondsLeft, int ofSeconds) {
        countdowns.add(secondsLeft);
    }

    @Override
    public void showRecording(Optional<Duration> elapsed, boolean timeLapse) {
        recordingTime = elapsed;
    }

    @Override
    public void showLevel(double value) {
        level = value;
    }

    @Override
    public void flash() {
        flashes++;
    }

    @Override
    public void showQr(Optional<QrContent> content) {
        qr = content;
        if (content.isPresent()) qrShown++;
    }

    @Override
    public void showRecent(List<Recent> list) {
        recent = list;
    }

    @Override
    public void showFullScreen(boolean on) {
        fullScreen = on;
    }

    @Override
    public void showSettings(SettingsModel model) {
        settings = model;
        settingsShown++;
    }

    @Override
    public void copyText(String text) {
        copied = text;
    }

    @Override
    public boolean confirmDelete(String fileName) {
        return confirmDelete;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showAbout() {
        abouts++;
    }

    @Override
    public void close() {
        closed = true;
    }
}
