package eu.amadeos.apps.webcam.domain;

/**
 * Normal videos, or a time lapse that keeps one picture every few seconds and plays them at the camera's
 * frame rate: with one picture every 5 s, an hour becomes 24 seconds at 30 fps.
 */
public enum VideoSpeed {
    NORMAL(0), LAPSE_1(1), LAPSE_5(5), LAPSE_10(10), LAPSE_30(30);

    private final int secondsPerPicture;

    VideoSpeed(int secondsPerPicture) {
        this.secondsPerPicture = secondsPerPicture;
    }

    /** Seconds between two kept pictures; 0 for a normal video. */
    public int secondsPerPicture() {
        return secondsPerPicture;
    }

    public boolean isTimeLapse() {
        return secondsPerPicture > 0;
    }
}
