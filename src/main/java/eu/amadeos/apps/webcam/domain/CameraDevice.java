package eu.amadeos.apps.webcam.domain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A camera the system offers as a V4L2 capture node.
 *
 * @param node  the device file, {@code /dev/video0}
 * @param name  what the driver calls it, "Integrated Camera: Integrated C"
 * @param usbId vendor and product of a USB camera, {@code 04f2:b6dd}; empty for others
 */
public record CameraDevice(Path node, String name, String usbId) {
    public CameraDevice {
        Objects.requireNonNull(node, "node");
        name = name == null || name.isBlank() ? node.getFileName().toString() : name.strip();
        usbId = usbId == null ? "" : usbId.strip();
    }

    /**
     * The name shown in menus: drivers often repeat the name before a colon ("Integrated Camera: Integrated C"),
     * so only the part before it is kept.
     */
    public String label() {
        int colon = name.indexOf(':');
        String label = colon > 0 ? name.substring(0, colon).strip() : name;
        return label.isEmpty() ? name : label;
    }

    /**
     * A key under which the settings of this camera are kept: the USB id when there is one, so that the camera
     * keeps its settings in another USB port (where it gets another node), else the name.
     */
    public String key() {
        String base = usbId.isEmpty() ? name : usbId;
        return base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
