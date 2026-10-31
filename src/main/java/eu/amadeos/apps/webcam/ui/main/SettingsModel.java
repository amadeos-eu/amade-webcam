package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.service.Microphones;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Everything the settings dialog shows. */
public record SettingsModel(List<CameraDevice> cameras, Optional<CameraDevice> camera, List<VideoMode> modes,
                            Optional<VideoMode> mode, List<CameraControl> controls,
                            List<Microphones.Microphone> microphones, String microphone,
                            PhotoFormat photoFormat, VideoFormat videoFormat, Path photoFolder, Path videoFolder,
                            boolean shutterSound, boolean flash, boolean saveMirrored) {
    public SettingsModel {
        cameras = List.copyOf(cameras);
        modes = List.copyOf(modes);
        controls = List.copyOf(controls);
        microphones = List.copyOf(microphones);
    }
}
