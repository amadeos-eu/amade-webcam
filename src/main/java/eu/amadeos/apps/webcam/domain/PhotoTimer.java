package eu.amadeos.apps.webcam.domain;

/** The countdown before a photo. */
public enum PhotoTimer {
    OFF(0), THREE(3), FIVE(5), TEN(10);

    private final int seconds;

    PhotoTimer(int seconds) {
        this.seconds = seconds;
    }

    public int seconds() {
        return seconds;
    }
}
