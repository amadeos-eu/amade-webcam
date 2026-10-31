package eu.amadeos.apps.webcam.domain;

/** How many photos one press takes: one, or a burst. */
public enum Shots {
    SINGLE(1), BURST_5(5), BURST_10(10);

    /** The pause between two photos of a burst. */
    public static final int BURST_INTERVAL_MS = 250;

    private final int count;

    Shots(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }
}
