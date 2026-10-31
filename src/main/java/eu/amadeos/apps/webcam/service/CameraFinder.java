package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Finds the cameras and talks to their controls. The list comes from {@code /sys/class/video4linux}; a node
 * counts as a camera when {@code v4l2-ctl} lists picture formats for it, which leaves out the metadata nodes
 * that most USB cameras have next to their capture node.
 */
public class CameraFinder {
    private static final Logger LOG = LoggerFactory.getLogger(CameraFinder.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Path sysfs;
    private final Path dev;
    private final CommandRunner runner;
    /** Formats per node; asking a busy camera is slow, and the formats do not change while it is plugged in. */
    private final Map<Path, List<VideoMode>> formats = new ConcurrentHashMap<>();

    public CameraFinder(CommandRunner runner) {
        this(Path.of("/sys/class/video4linux"), Path.of("/dev"), runner);
    }

    public CameraFinder(Path sysfs, Path dev, CommandRunner runner) {
        this.sysfs = sysfs;
        this.dev = dev;
        this.runner = runner;
    }

    /**
     * The nodes in the system, with name and USB id, sorted by number; cheap, reads only small files. Used to
     * notice cameras that are plugged in or pulled out.
     */
    public List<CameraDevice> nodes() {
        if (!Files.isDirectory(sysfs)) return List.of();
        List<CameraDevice> nodes = new ArrayList<>();
        try (Stream<Path> entries = Files.list(sysfs)) {
            for (Path entry : entries.filter(p -> p.getFileName().toString().matches("video\\d+")).toList()) {
                String node = entry.getFileName().toString();
                nodes.add(new CameraDevice(dev.resolve(node), read(entry.resolve("name")), usbId(entry)));
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}", sysfs, e);
        }
        nodes.sort(Comparator.comparingInt(d -> number(d.node())));
        return nodes;
    }

    /** The nodes that deliver pictures. */
    public List<CameraDevice> cameras() {
        List<CameraDevice> nodes = nodes();
        // Forget the nodes that are gone: a camera plugged in later may get the same node.
        formats.keySet().retainAll(nodes.stream().map(CameraDevice::node).toList());
        List<CameraDevice> cameras = new ArrayList<>();
        for (CameraDevice node : nodes) {
            if (!formats(node).isEmpty()) cameras.add(node);
        }
        return cameras;
    }

    /** What the camera can deliver; empty for a node without pictures or when v4l2-ctl is missing. */
    public List<VideoMode> formats(CameraDevice camera) {
        return formats.computeIfAbsent(camera.node(), node -> {
            try {
                return V4l2Output.formats(runner.output(List.of("v4l2-ctl", "-d", node.toString(),
                        "--list-formats-ext"), TIMEOUT));
            } catch (ProgramMissingException e) {
                LOG.warn("v4l2-ctl is missing (package v4l-utils)");
                return List.of();
            } catch (IOException e) {
                LOG.debug("No formats for {}: {}", node, e.getMessage());
                return List.of();
            }
        });
    }

    /** The camera's own controls; empty if they cannot be read. */
    public List<CameraControl> controls(CameraDevice camera) {
        try {
            return V4l2Output.controls(runner.output(List.of("v4l2-ctl", "-d", camera.node().toString(),
                    "--list-ctrls-menus"), TIMEOUT));
        } catch (IOException e) {
            LOG.debug("No controls for {}: {}", camera.node(), e.getMessage());
            return List.of();
        }
    }

    /** Sets one control; the camera may refuse values while an automatic mode is on. */
    public void set(CameraDevice camera, String control, int value) throws IOException {
        runner.output(List.of("v4l2-ctl", "-d", camera.node().toString(), "--set-ctrl=" + control + "=" + value),
                TIMEOUT);
    }

    /** True if v4l2-ctl can be started. */
    public boolean toolAvailable() {
        try {
            runner.run(List.of("v4l2-ctl", "--version"), TIMEOUT);
            return true;
        } catch (ProgramMissingException e) {
            return false;
        } catch (IOException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file).strip();
        } catch (IOException e) {
            return "";
        }
    }

    /** {@code device} points at the USB interface; vendor and product are in the USB device above it. */
    private static String usbId(Path entry) {
        Path device = entry.resolve("device");
        for (Path dir : new Path[]{device.resolve(".."), device}) {
            String vendor = read(dir.resolve("idVendor"));
            String product = read(dir.resolve("idProduct"));
            if (!vendor.isEmpty() && !product.isEmpty()) return vendor + ":" + product;
        }
        return "";
    }

    private static int number(Path node) {
        try {
            return Integer.parseInt(node.getFileName().toString().replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
