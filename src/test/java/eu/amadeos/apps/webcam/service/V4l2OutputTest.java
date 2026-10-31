package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.VideoMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4l2OutputTest {
    /** A typical laptop camera (v4l2-ctl 1.26). */
    static final String FORMATS = """
            ioctl: VIDIOC_ENUM_FMT
            \tType: Video Capture

            \t[0]: 'MJPG' (Motion-JPEG, compressed)
            \t\tSize: Discrete 1280x720
            \t\t\tInterval: Discrete 0.033s (30.000 fps)
            \t\tSize: Discrete 640x480
            \t\t\tInterval: Discrete 0.033s (30.000 fps)
            \t\t\tInterval: Discrete 0.067s (15.000 fps)
            \t[1]: 'YUYV' (YUYV 4:2:2)
            \t\tSize: Discrete 1280x720
            \t\t\tInterval: Discrete 0.100s (10.000 fps)
            \t\tSize: Discrete 640x480
            \t\t\tInterval: Discrete 0.033s (30.000 fps)
            """;

    static final String CONTROLS = """

            User Controls

                                 brightness 0x00980900 (int)    : min=-64 max=64 step=1 default=0 value=12
                                   contrast 0x00980901 (int)    : min=0 max=95 step=1 default=0 value=0
                    white_balance_automatic 0x0098090c (bool)   : default=1 value=1
                       power_line_frequency 0x00980918 (menu)   : min=0 max=2 default=1 value=1 (50 Hz)
            \t\t\t\t0: Disabled
            \t\t\t\t1: 50 Hz
            \t\t\t\t2: 60 Hz
                  white_balance_temperature 0x0098091a (int)    : min=2800 max=6500 step=10 default=4600 value=4600 flags=inactive
                                  sharpness 0x0098091b (int)    : min=0 max=7 step=1 default=2 value=2

            Camera Controls

                              auto_exposure 0x009a0901 (menu)   : min=0 max=3 default=3 value=3 (Aperture Priority Mode)
            \t\t\t\t1: Manual Mode
            \t\t\t\t3: Aperture Priority Mode
                     exposure_time_absolute 0x009a0902 (int)    : min=1 max=5000 step=1 default=157 value=157 flags=inactive
                            privacy 0x009a0910 (bool)   : default=0 value=0 flags=read-only
            """;

    @Test
    void formats() {
        assertEquals(List.of(new VideoMode("MJPG", 1280, 720, 30), new VideoMode("MJPG", 640, 480, 30),
                new VideoMode("MJPG", 640, 480, 15), new VideoMode("YUYV", 1280, 720, 10),
                new VideoMode("YUYV", 640, 480, 30)), V4l2Output.formats(FORMATS));
    }

    @Test
    void sizesWithoutRateCountAs30() {
        String text = """
                \t[0]: 'YUYV' (YUYV 4:2:2)
                \t\tSize: Discrete 320x240
                \t\tSize: Discrete 640x480
                """;
        assertEquals(List.of(new VideoMode("YUYV", 320, 240, 30), new VideoMode("YUYV", 640, 480, 30)),
                V4l2Output.formats(text));
    }

    @Test
    void stepwiseSizes() {
        String text = """
                \t[0]: 'YUYV' (YUYV 4:2:2)
                \t\tSize: Stepwise 32x32 - 1920x1080 with step 2/2
                """;
        assertEquals(List.of(new VideoMode("YUYV", 32, 32, 30), new VideoMode("YUYV", 1920, 1080, 30)),
                V4l2Output.formats(text));
    }

    @Test
    void noFormatsForAMetadataNode() {
        assertTrue(V4l2Output.formats("ioctl: VIDIOC_ENUM_FMT\n\tType: Video Capture\n").isEmpty());
    }

    @Test
    void controls() {
        List<CameraControl> controls = V4l2Output.controls(CONTROLS);
        assertEquals(List.of("brightness", "contrast", "white_balance_automatic", "power_line_frequency",
                "white_balance_temperature", "sharpness", "auto_exposure", "exposure_time_absolute", "privacy"),
                controls.stream().map(CameraControl::name).toList());
        CameraControl brightness = controls.get(0);
        assertEquals(CameraControl.Type.INTEGER, brightness.type());
        assertEquals(-64, brightness.min());
        assertEquals(64, brightness.max());
        assertEquals(0, brightness.defaultValue());
        assertEquals(12, brightness.value());
        assertFalse(brightness.inactive());
        CameraControl wb = controls.get(2);
        assertEquals(CameraControl.Type.BOOLEAN, wb.type());
        assertEquals(1, wb.value());
        assertEquals(1, wb.max());
        CameraControl power = controls.get(3);
        assertEquals(CameraControl.Type.MENU, power.type());
        assertEquals(Map.of(0, "Disabled", 1, "50 Hz", 2, "60 Hz"), power.menu());
        CameraControl temperature = controls.get(4);
        assertTrue(temperature.inactive());
        assertEquals(10, temperature.step());
        assertEquals(Map.of(1, "Manual Mode", 3, "Aperture Priority Mode"), controls.get(6).menu());
        assertTrue(controls.get(7).inactive());
        assertFalse(controls.get(8).inactive());
    }
}
