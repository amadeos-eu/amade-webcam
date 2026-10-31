package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.QrContent;
import eu.amadeos.apps.webcam.service.FrameSource;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    /** Why there is no picture. */
    enum NoPicture { STARTING, NO_CAMERA, BUSY, GONE, NOT_SUPPORTED, PROGRAM_MISSING, FAILED }

    /** A photo or video in the strip of recent shots. */
    record Recent(Path file, BufferedImage thumbnail, boolean video) {
    }

    /** The latest camera picture, with the look applied, not mirrored. */
    void showFrame(BufferedImage frame);

    /** No picture, and why; {@code detail} is the technical message, may be empty. */
    void showNoPicture(NoPicture reason, String detail);

    /** The cameras for the Camera menu; {@code current} is ticked. */
    void showCameras(List<CameraDevice> cameras, Optional<CameraDevice> current);

    void showStatus(String text);

    void showTitle(String title);

    /** Photo or video, and whether a recording runs (the shutter turns into a stop button then). */
    void showMode(CaptureMode mode, boolean recording, boolean camera);

    /** The switches and choices of the control bar and the menus. */
    void showOptions(Options options);

    /** The countdown before a photo; 0 hides it. */
    void showCountdown(int secondsLeft, int ofSeconds);

    /** Time of the running recording; empty when none runs. */
    void showRecording(Optional<Duration> elapsed, boolean timeLapse);

    /** Microphone level from 0 to 1. */
    void showLevel(double level);

    /** The short white flash of a photo. */
    void flash();

    /** A QR code found in the picture; empty hides the banner. */
    void showQr(Optional<QrContent> content);

    void showRecent(List<Recent> recent);

    void showFullScreen(boolean on);

    /** Opens the settings dialog, or refreshes it if it is open. */
    void showSettings(SettingsModel model);

    void copyText(String text);

    boolean confirmDelete(String fileName);

    void showError(String message);

    void showAbout();

    void close();

    /** Turns a source failure into the reason shown. */
    static NoPicture reason(FrameSource.Failure failure) {
        return switch (failure) {
            case BUSY -> NoPicture.BUSY;
            case GONE -> NoPicture.GONE;
            case NOT_SUPPORTED -> NoPicture.NOT_SUPPORTED;
            case PROGRAM_MISSING -> NoPicture.PROGRAM_MISSING;
            case OTHER -> NoPicture.FAILED;
        };
    }
}
