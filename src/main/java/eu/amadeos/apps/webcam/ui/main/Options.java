package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoSpeed;

/** The switches of the control bar and the View menu. */
public record Options(boolean mirror, boolean grid, boolean qrCodes, Look look, PhotoTimer timer, Shots shots,
                      VideoSpeed speed) {
}
