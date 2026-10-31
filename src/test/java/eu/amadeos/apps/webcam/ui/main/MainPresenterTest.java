package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.QrContent;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.apps.webcam.repository.WebcamPreferences;
import eu.amadeos.apps.webcam.service.FrameSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path home;

    private Fixture started() {
        Fixture f = new Fixture(home);
        f.presenter.start();
        return f;
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) throw new AssertionError("timed out");
            Thread.sleep(20);
        }
    }

    private static List<Path> files(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted().toList();
        }
    }

    // ---------------------------------------------------------------- cameras

    @Test
    void startOpensTheFirstCameraAtTheBestMode() {
        Fixture f = started();
        assertEquals(Fixture.CAM_A, f.source.camera);
        assertEquals(new VideoMode("MJPG", 1280, 720, 30), f.source.mode);
        assertEquals(List.of(Fixture.CAM_A, Fixture.CAM_B), f.view.cameras);
        assertEquals(Optional.of(Fixture.CAM_A), f.view.currentCamera);
        assertTrue(f.view.camera);
        assertEquals(CaptureMode.PHOTO, f.view.mode);
        assertEquals("Webcam", f.view.title);
        assertTrue(f.view.status.startsWith("Integrated Camera   1280 × 720   30 fps"), f.view.status);
    }

    @Test
    void theCameraUsedLastAndItsModeComeBack() {
        new WebcamPreferences(home.resolve("webcam.properties")).setLastCamera(Fixture.CAM_B);
        new WebcamPreferences(home.resolve("webcam.properties")).setMode(Fixture.CAM_B, new VideoMode("YUYV", 640, 480, 30));
        Fixture f = started();
        assertEquals(Fixture.CAM_B, f.source.camera);
        assertEquals(new VideoMode("YUYV", 640, 480, 30), f.source.mode);
    }

    @Test
    void aSavedModeTheCameraLostIsIgnored() {
        new WebcamPreferences(home.resolve("webcam.properties")).setMode(Fixture.CAM_A, new VideoMode("H264", 3840, 2160, 30));
        assertEquals(new VideoMode("MJPG", 1280, 720, 30), started().source.mode);
    }

    @Test
    void savedControlsAreSetAutomaticFirst() {
        WebcamPreferences p = new WebcamPreferences(home.resolve("webcam.properties"));
        p.setControl(Fixture.CAM_A, "white_balance_temperature", 3000);
        p.setControl(Fixture.CAM_A, "white_balance_automatic", 0);
        Fixture f = started();
        assertEquals(List.of("video0 white_balance_automatic=0", "video0 white_balance_temperature=3000"), f.finder.sets);
    }

    @Test
    void noCamera() {
        Fixture f = new Fixture(home);
        f.finder.cameras.clear();
        f.presenter.start();
        assertEquals(MainView.NoPicture.NO_CAMERA, f.view.noPicture);
        assertFalse(f.view.camera);
        assertEquals(0, f.source.starts);
        assertEquals("No camera", f.view.status);
    }

    @Test
    void aCameraWithoutFormats() {
        Fixture f = new Fixture(home);
        f.finder.formats.put(Fixture.CAM_A.key(), List.of());
        f.presenter.start();
        assertEquals(MainView.NoPicture.NOT_SUPPORTED, f.view.noPicture);
    }

    @Test
    void plugAndUnplug() {
        Fixture f = new Fixture(home);
        f.finder.cameras.clear();
        f.presenter.start();
        f.finder.cameras.add(Fixture.CAM_B);
        f.ticker.advance(MainPresenter.PLUG_CHECK);
        assertEquals(Fixture.CAM_B, f.source.camera, "a plugged camera opens");
        f.source.send(Fixture.frame());
        f.finder.cameras.clear();
        f.ticker.advance(MainPresenter.PLUG_CHECK);
        assertEquals(MainView.NoPicture.NO_CAMERA, f.view.noPicture);
        assertEquals(1, f.source.stops);
        assertTrue(f.view.cameras.isEmpty());
    }

    @Test
    void anotherCameraPluggedInDoesNotTakeOver() {
        Fixture f = new Fixture(home);
        f.finder.cameras.remove(Fixture.CAM_B);
        f.presenter.start();
        f.finder.cameras.add(Fixture.CAM_B);
        f.ticker.advance(MainPresenter.PLUG_CHECK);
        assertEquals(1, f.source.starts);
        assertEquals(List.of(Fixture.CAM_A, Fixture.CAM_B), f.view.cameras);
    }

    @Test
    void chooseAnotherCamera() {
        Fixture f = started();
        f.presenter.chooseCamera(Fixture.CAM_B);
        assertEquals(Fixture.CAM_B, f.source.camera);
        assertEquals(1, f.source.stops);
        assertEquals(Optional.of("046d-0825"), f.preferences.lastCamera());
        f.presenter.chooseCamera(Fixture.CAM_B);
        assertEquals(2, f.source.starts, "the same camera again changes nothing");
    }

    @Test
    void framesReachTheWindowWithTheLook() {
        Fixture f = started();
        f.presenter.setLook(Look.BLACK_AND_WHITE);
        BufferedImage frame = new BufferedImage(4, 4, BufferedImage.TYPE_3BYTE_BGR);
        frame.setRGB(0, 0, new Color(200, 30, 60).getRGB());
        f.source.send(frame);
        assertEquals(1, f.view.frames);
        Color c = new Color(f.view.frame.getRGB(0, 0));
        assertEquals(c.getRed(), c.getBlue());
        assertEquals(Look.BLACK_AND_WHITE, f.preferences.look());
    }

    @Test
    void failuresAreShown() {
        Fixture f = started();
        f.source.listener.ended(Optional.of(FrameSource.Failure.BUSY), "busy");
        assertEquals(MainView.NoPicture.BUSY, f.view.noPicture);
        assertFalse(f.view.camera);
        f.source.listener.ended(Optional.empty(), "");
        assertEquals(MainView.NoPicture.BUSY, f.view.noPicture, "a normal end shows nothing new");
    }

    @Test
    void framesOfAStoppedStreamAreIgnored() {
        Fixture f = started();
        FrameSource.Listener old = f.source.listener;
        f.presenter.chooseCamera(Fixture.CAM_B);
        old.frame(Fixture.frame());
        old.ended(Optional.of(FrameSource.Failure.GONE), "");
        assertEquals(0, f.view.frames);
        assertTrue(f.view.noPicture == null || f.view.noPicture == MainView.NoPicture.STARTING);
    }

    // ---------------------------------------------------------------- photos

    @Test
    void aPhotoIsSavedWithFlashAndShowsUpInTheStrip() throws Exception {
        Fixture f = started();
        f.source.send(Fixture.frame());
        f.presenter.shutter();
        Path photos = home.resolve("Pictures/Camera");
        assertEquals(List.of(photos.resolve("Photo 2026-10-31 12-00-00.jpg")), files(photos));
        assertEquals(1, f.view.flashes);
        assertEquals("Saved Photo 2026-10-31 12-00-00.jpg", f.view.status);
        assertEquals(1, f.view.recent.size());
        assertFalse(f.view.recent.getFirst().video());
    }

    @Test
    void noPhotoWithoutAPicture() throws Exception {
        Fixture f = started();
        f.presenter.takePhoto();
        assertTrue(files(home.resolve("Pictures/Camera")).isEmpty());
    }

    @Test
    void pngWithoutFlashIntoAnotherFolder() throws Exception {
        Fixture f = started();
        f.presenter.setPhotoFormat(PhotoFormat.PNG);
        f.presenter.setFlash(false);
        f.presenter.setShutterSound(false);
        f.presenter.setPhotoFolder(home.resolve("mine"));
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        assertEquals(List.of(home.resolve("mine/Photo 2026-10-31 12-00-00.png")), files(home.resolve("mine")));
        assertEquals(0, f.view.flashes);
    }

    @Test
    void timerCountsDownThenShoots() throws Exception {
        Fixture f = started();
        f.presenter.setTimer(PhotoTimer.THREE);
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        assertEquals(List.of(3), f.view.countdowns);
        f.ticker.advance(Duration.ofSeconds(2));
        assertEquals(List.of(3, 2, 1), f.view.countdowns);
        assertTrue(files(home.resolve("Pictures/Camera")).isEmpty());
        f.ticker.advance(Duration.ofSeconds(1));
        assertEquals(0, (int) f.view.countdowns.getLast());
        assertEquals(1, files(home.resolve("Pictures/Camera")).size());
    }

    @Test
    void escapeCancelsTheCountdown() throws Exception {
        Fixture f = started();
        f.presenter.setTimer(PhotoTimer.FIVE);
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        f.presenter.escape();
        f.ticker.advance(Duration.ofSeconds(10));
        assertTrue(files(home.resolve("Pictures/Camera")).isEmpty());
        assertEquals("Countdown cancelled", f.view.status);
    }

    @Test
    void burstTakesFivePhotos() throws Exception {
        Fixture f = started();
        f.presenter.setShots(Shots.BURST_5);
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        f.ticker.advance(Duration.ofSeconds(2));
        List<Path> files = files(home.resolve("Pictures/Camera"));
        assertEquals(5, files.size());
        assertEquals("Photo 2026-10-31 12-00-00 (1).jpg", files.getFirst().getFileName().toString());
        assertEquals(5, f.view.flashes);
        assertTrue(f.view.status.startsWith("5 photos saved"), f.view.status);
        assertEquals(3, f.view.recent.size());
    }

    @Test
    void aSavedBurstIsAnnouncedAndItsOpenOpensTheLastPhoto() throws Exception {
        Fixture f = started();
        f.presenter.setShots(Shots.BURST_5);
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        f.ticker.advance(Duration.ofSeconds(2));
        assertEquals(List.of(f.view.status), f.view.announced);
        f.view.openAnnounced.run();
        assertEquals(List.of("open " + home.resolve("Pictures/Camera/Photo 2026-10-31 12-00-00 (5).jpg")),
                f.opener.opened);
    }

    // ---------------------------------------------------------------- video

    @Test
    void recordAVideo() throws Exception {
        Fixture f = started();
        f.presenter.setCaptureMode(CaptureMode.VIDEO);
        assertEquals(CaptureMode.VIDEO, f.view.mode);
        f.source.send(Fixture.frame());
        f.presenter.shutter();
        assertTrue(f.view.recording);
        assertTrue(f.presenter.recordingRuns());
        List<String> cmd = f.ffmpeg.getFirst();
        assertTrue(cmd.contains("pulse") && cmd.contains("default"), cmd.toString());
        assertTrue(cmd.getLast().endsWith("Videos/Camera/Video 2026-10-31 12-00-00.mp4"), cmd.getLast());
        f.source.send(Fixture.frame());
        f.ticker.advance(Duration.ofMillis(500));
        assertTrue(f.view.recordingTime.isPresent());
        f.presenter.setCaptureMode(CaptureMode.PHOTO);
        assertEquals(CaptureMode.VIDEO, f.presenter.captureMode(), "no switch while recording");
        f.presenter.chooseCamera(Fixture.CAM_B);
        assertEquals(Fixture.CAM_A, f.source.camera, "no camera change while recording");
        f.presenter.shutter();
        assertFalse(f.view.recording);
        waitFor(() -> f.view.status.startsWith("Saved Video"));
        assertTrue(f.view.recordingTime.isEmpty());
    }

    @Test
    void videoWithoutSoundAsWebm() {
        Fixture f = started();
        f.presenter.setMicrophone(WebcamPreferences.NO_SOUND);
        f.presenter.setVideoFormat(VideoFormat.WEBM);
        f.presenter.setCaptureMode(CaptureMode.VIDEO);
        f.source.send(Fixture.frame());
        f.presenter.toggleRecording();
        List<String> cmd = f.ffmpeg.getFirst();
        assertFalse(cmd.contains("pulse"));
        assertTrue(cmd.getLast().endsWith(".webm"));
        f.presenter.toggleRecording();
    }

    @Test
    void timeLapse() {
        Fixture f = started();
        f.presenter.setSpeed(VideoSpeed.LAPSE_10);
        f.presenter.setCaptureMode(CaptureMode.VIDEO);
        f.source.send(Fixture.frame());
        f.presenter.toggleRecording();
        List<String> cmd = f.ffmpeg.getFirst();
        assertTrue(cmd.getLast().contains("Time Lapse 2026-10-31"), cmd.getLast());
        assertTrue(cmd.contains("-framerate"));
        f.presenter.setSpeed(VideoSpeed.NORMAL);
        assertEquals(VideoSpeed.LAPSE_10, f.preferences.speed(), "no change while recording");
        f.ticker.advance(Duration.ofMillis(250));
        assertTrue(f.view.status.startsWith("Time lapse:"), f.view.status);
        f.presenter.toggleRecording();
    }

    @Test
    void theRecordingStopsWhenTheCameraFails() {
        Fixture f = started();
        f.presenter.setCaptureMode(CaptureMode.VIDEO);
        f.source.send(Fixture.frame());
        f.presenter.toggleRecording();
        f.source.listener.ended(Optional.of(FrameSource.Failure.GONE), "");
        assertFalse(f.presenter.recordingRuns());
        assertEquals(MainView.NoPicture.GONE, f.view.noPicture);
    }

    @Test
    void exitFinishesTheVideoFirst() throws Exception {
        Fixture f = started();
        f.presenter.setCaptureMode(CaptureMode.VIDEO);
        f.source.send(Fixture.frame());
        f.presenter.toggleRecording();
        f.presenter.exit();
        waitFor(() -> f.view.closed);
        assertEquals(1, f.exits[0]);
    }

    @Test
    void exitWithoutRecording() {
        Fixture f = started();
        f.presenter.exit();
        assertTrue(f.view.closed);
        assertEquals(1, f.source.stops);
    }

    // ---------------------------------------------------------------- QR codes

    @Test
    void qrCodesAreShownCopiedAndDismissed() {
        Fixture f = started();
        f.presenter.setQrCodes(true);
        f.qrText = "WIFI:S:Home;T:WPA;P:secret;;";
        f.source.send(Fixture.frame());
        assertEquals(QrContent.Kind.WIFI, f.view.qr.orElseThrow().kind());
        f.presenter.qrCopy();
        assertEquals("secret", f.view.copied);
        f.presenter.qrDismiss();
        assertTrue(f.view.qr.isEmpty());
        f.presenter.qrResult(Optional.of("WIFI:S:Home;T:WPA;P:secret;;"), System.currentTimeMillis());
        assertTrue(f.view.qr.isEmpty(), "a dismissed code stays away");
        f.presenter.qrResult(Optional.of("https://amadeos.eu"), System.currentTimeMillis());
        f.presenter.qrOpen();
        assertEquals(List.of("browse https://amadeos.eu"), f.opener.opened);
        f.presenter.qrCopy();
        assertEquals("https://amadeos.eu", f.view.copied);
    }

    @Test
    void aDismissedCodeComesBackAfterItLeftThePicture() {
        Fixture f = started();
        f.presenter.setQrCodes(true);
        long t = 1_000_000;
        f.presenter.qrResult(Optional.of("Hello"), t);
        f.presenter.qrDismiss();
        f.presenter.qrResult(Optional.empty(), t + MainPresenter.QR_FORGET_MS + 1);
        f.presenter.qrResult(Optional.of("Hello"), t + MainPresenter.QR_FORGET_MS + 2);
        assertEquals(2, f.view.qrShown);
    }

    @Test
    void qrOffHidesTheBanner() {
        Fixture f = started();
        f.presenter.setQrCodes(true);
        f.presenter.qrResult(Optional.of("Hello"), 1);
        f.presenter.setQrCodes(false);
        assertTrue(f.view.qr.isEmpty());
        f.presenter.qrResult(Optional.of("Other"), 2);
        assertTrue(f.view.qr.isEmpty());
        assertFalse(f.view.options.qrCodes());
    }

    // ---------------------------------------------------------------- settings dialog

    @Test
    void settingsShowCameraModesControlsAndMicrophones() {
        Fixture f = started();
        f.presenter.openSettings();
        SettingsModel m = f.view.settings;
        assertEquals(Optional.of(Fixture.CAM_A), m.camera());
        assertEquals(Fixture.MODES, m.modes());
        assertEquals(2, m.controls().size());
        assertEquals("USB Mic", m.microphones().getFirst().description());
        assertEquals(VideoFormat.MP4, m.videoFormat());
        assertEquals(home.resolve("Pictures/Camera"), m.photoFolder());
    }

    @Test
    void chooseAModeRestartsTheCameraAndIsKept() {
        Fixture f = started();
        f.presenter.openSettings();
        f.presenter.chooseMode(new VideoMode("YUYV", 640, 480, 30));
        assertEquals(new VideoMode("YUYV", 640, 480, 30), f.source.mode);
        assertEquals(2, f.source.starts);
        assertEquals(Optional.of(new VideoMode("YUYV", 640, 480, 30)), f.preferences.mode(Fixture.CAM_A));
        assertEquals(Optional.of(new VideoMode("YUYV", 640, 480, 30)), f.view.settings.mode());
    }

    @Test
    void controlsAreSetAndKept() {
        Fixture f = started();
        f.presenter.openSettings();
        int reads = f.finder.controlReads;
        f.presenter.setControl("brightness", 200);
        assertEquals("video0 brightness=200", f.finder.sets.getLast());
        assertEquals(reads, f.finder.controlReads, "a plain slider does not read the controls again");
        f.presenter.setControl("white_balance_automatic", 0);
        assertEquals(reads + 1, f.finder.controlReads, "an automatic switch does");
        assertEquals(java.util.Map.of("brightness", 200, "white_balance_automatic", 0), f.preferences.controls(Fixture.CAM_A));
        f.presenter.cameraDefaults();
        assertTrue(f.preferences.controls(Fixture.CAM_A).isEmpty());
        assertTrue(f.finder.sets.containsAll(List.of("video0 brightness=128", "video0 white_balance_automatic=1")));
    }

    @Test
    void settingsClosedAreNotRefreshed() {
        Fixture f = started();
        f.presenter.openSettings();
        f.presenter.settingsClosed();
        int shown = f.view.settingsShown;
        f.presenter.chooseMode(new VideoMode("MJPG", 640, 480, 30));
        assertEquals(shown, f.view.settingsShown);
    }

    @Test
    void savingSwitches() {
        Fixture f = started();
        f.presenter.setSaveMirrored(true);
        f.presenter.setVideoFolder(home.resolve("v"));
        assertTrue(f.preferences.saveMirrored());
        assertEquals(home.resolve("v"), f.presenter.videoFolder());
    }

    // ---------------------------------------------------------------- the rest

    @Test
    void viewSwitchesAreKept() {
        Fixture f = started();
        f.presenter.setMirror(false);
        f.presenter.setGrid(true);
        assertFalse(f.view.options.mirror());
        assertTrue(f.view.options.grid());
        assertFalse(f.preferences.mirror());
        f.presenter.toggleFullScreen();
        assertTrue(f.view.fullScreen);
        f.presenter.escape();
        assertFalse(f.view.fullScreen);
    }

    @Test
    void recentFilesOpenEditShowAndDelete() throws Exception {
        Fixture f = started();
        f.source.send(Fixture.frame());
        f.presenter.takePhoto();
        Path photo = f.view.recent.getFirst().file();
        f.presenter.openRecent(photo);
        assertTrue(f.presenter.canEdit(photo));
        f.presenter.editRecent(photo);
        f.presenter.showInFolder(photo);
        assertEquals(List.of("open " + photo, "amade-imageeditor " + photo, "open " + photo.getParent()), f.opener.opened);
        f.view.confirmDelete = false;
        f.presenter.deleteRecent(photo);
        assertTrue(Files.exists(photo));
        f.view.confirmDelete = true;
        f.presenter.deleteRecent(photo);
        assertFalse(Files.exists(photo));
        assertTrue(f.view.recent.isEmpty());
        f.opener.editor = false;
        assertFalse(f.presenter.canEdit(photo));
        assertFalse(f.presenter.canEdit(Path.of("a.mp4")));
    }

    @Test
    void foldersAreCreatedAndOpened() {
        Fixture f = started();
        f.presenter.openPhotoFolder();
        f.presenter.openVideoFolder();
        assertTrue(Files.isDirectory(home.resolve("Pictures/Camera")));
        assertTrue(Files.isDirectory(home.resolve("Videos/Camera")));
        assertEquals(2, f.opener.opened.size());
    }

    @Test
    void about() {
        Fixture f = started();
        f.presenter.about();
        assertEquals(1, f.view.abouts);
    }

    @Test
    void sizes() {
        assertEquals("512 KB", MainPresenter.sizeText(512 * 1024));
        assertEquals("38.0 MB", MainPresenter.sizeText(38L * 1024 * 1024));
    }

    @Test
    void optionsMirrorThePreferences() {
        Fixture f = started();
        assertNotNull(f.view.options);
        assertTrue(f.view.options.mirror());
        assertNull(f.view.copied);
    }
}
