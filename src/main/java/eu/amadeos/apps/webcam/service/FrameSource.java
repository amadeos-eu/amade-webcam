package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

/** Delivers the pictures of a camera, one {@link BufferedImage#TYPE_3BYTE_BGR} image per frame. */
public interface FrameSource {
    /** Why the pictures stopped. */
    enum Failure {
        /** Another program has the camera open. */
        BUSY,
        /** The camera was pulled out. */
        GONE,
        /** The camera does not offer the chosen mode. */
        NOT_SUPPORTED,
        /** ffmpeg is not installed. */
        PROGRAM_MISSING,
        OTHER
    }

    /** Called on the reading thread, not on the EDT. */
    interface Listener {
        void frame(BufferedImage frame);

        /** The pictures ended: empty after {@link Running#stop()}, else why. {@code detail} is ffmpeg's message. */
        void ended(Optional<Failure> failure, String detail);
    }

    interface Running {
        /** Stops the pictures; {@link Listener#ended} follows with no failure. */
        void stop();
    }

    Running start(CameraDevice camera, VideoMode mode, Listener listener) throws IOException;
}
