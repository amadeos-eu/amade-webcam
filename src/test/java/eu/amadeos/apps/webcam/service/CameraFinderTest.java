package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraFinderTest {
    /** /sys/class/video4linux with a USB camera (video0 pictures, video1 metadata) and a capture card. */
    static Path sysfs(Path root) throws IOException {
        Path usb = root.resolve("devices/usb1/1-8");
        Files.createDirectories(usb.resolve("1-8:1.0"));
        Files.writeString(usb.resolve("idVendor"), "04f2\n");
        Files.writeString(usb.resolve("idProduct"), "b6dd\n");
        Path classDir = root.resolve("class");
        for (String node : List.of("video0", "video1", "video10")) {
            Path dir = Files.createDirectories(classDir.resolve(node));
            Files.writeString(dir.resolve("name"), node.equals("video10") ? "HDMI Capture\n"
                    : "Integrated Camera: Integrated C\n");
            if (!node.equals("video10")) Files.createSymbolicLink(dir.resolve("device"), usb.resolve("1-8:1.0"));
        }
        Files.createDirectories(classDir.resolve("something-else"));
        return classDir;
    }

    @Test
    void nodesAreReadFromSysfs(@TempDir Path root) throws IOException {
        CameraFinder finder = new CameraFinder(sysfs(root), Path.of("/dev"), command -> {
            throw new AssertionError("no program needed");
        });
        List<CameraDevice> nodes = finder.nodes();
        assertEquals(List.of(Path.of("/dev/video0"), Path.of("/dev/video1"), Path.of("/dev/video10")),
                nodes.stream().map(CameraDevice::node).toList());
        assertEquals("04f2:b6dd", nodes.get(0).usbId());
        assertEquals("", nodes.get(2).usbId());
        assertEquals("HDMI Capture", nodes.get(2).name());
    }

    @Test
    void onlyNodesWithFormatsAreCameras(@TempDir Path root) throws IOException {
        List<List<String>> calls = new ArrayList<>();
        CommandRunner runner = command -> {
            calls.add(command);
            String node = command.get(2);
            return new FinishedProcess(node.endsWith("video1") ? "ioctl: VIDIOC_ENUM_FMT\n" : V4l2OutputTest.FORMATS, "", 0);
        };
        CameraFinder finder = new CameraFinder(sysfs(root), Path.of("/dev"), runner);
        assertEquals(List.of(Path.of("/dev/video0"), Path.of("/dev/video10")),
                finder.cameras().stream().map(CameraDevice::node).toList());
        assertEquals(3, calls.size());
        finder.cameras();
        assertEquals(3, calls.size(), "formats are kept while the camera stays");
        assertEquals(5, finder.formats(finder.cameras().getFirst()).size());
    }

    @Test
    void controlsAndSet(@TempDir Path root) throws IOException {
        List<List<String>> calls = new ArrayList<>();
        CommandRunner runner = command -> {
            calls.add(command);
            return FinishedProcess.printing(command.contains("--list-ctrls-menus") ? V4l2OutputTest.CONTROLS : "");
        };
        CameraFinder finder = new CameraFinder(sysfs(root), Path.of("/dev"), runner);
        CameraDevice cam = finder.nodes().getFirst();
        assertEquals(9, finder.controls(cam).size());
        finder.set(cam, "brightness", 20);
        assertEquals(List.of("v4l2-ctl", "-d", "/dev/video0", "--set-ctrl=brightness=20"), calls.getLast());
    }

    @Test
    void withoutV4l2CtlThereAreNoCameras(@TempDir Path root) throws IOException {
        CameraFinder finder = new CameraFinder(sysfs(root), Path.of("/dev"), command -> {
            throw new ProgramMissingException(command.getFirst(), new IOException("missing"));
        });
        assertTrue(finder.cameras().isEmpty());
        assertFalse(finder.toolAvailable());
    }

    @Test
    void noSysfsNoCameras() {
        assertTrue(new CameraFinder(Path.of("/nonexistent/x"), Path.of("/dev"), c -> null).nodes().isEmpty());
    }

    @Test
    void failingControlsAreEmpty(@TempDir Path root) throws IOException {
        CameraFinder finder = new CameraFinder(sysfs(root), Path.of("/dev"),
                command -> FinishedProcess.failing(1, "Device or resource busy"));
        assertTrue(finder.controls(finder.nodes().getFirst()).isEmpty());
        assertTrue(finder.toolAvailable());
    }
}
