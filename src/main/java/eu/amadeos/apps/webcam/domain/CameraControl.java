package eu.amadeos.apps.webcam.domain;

import java.util.Map;
import java.util.Objects;

/**
 * One of the camera's own settings, as {@code v4l2-ctl --list-ctrls-menus} reports it.
 *
 * @param name     the V4L2 name, {@code brightness}, {@code white_balance_automatic}
 * @param inactive true while another control makes this one meaningless, such as the colour temperature while
 *                 the white balance is automatic
 * @param menu     the choices of a menu control, value to text ("1" to "50 Hz"); empty for other types
 */
public record CameraControl(String name, Type type, int min, int max, int step, int defaultValue, int value,
                            boolean inactive, Map<Integer, String> menu) {
    public enum Type { INTEGER, BOOLEAN, MENU }

    public CameraControl {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        menu = Map.copyOf(menu);
        if (step <= 0) step = 1;
    }

    public CameraControl withValue(int newValue) {
        return new CameraControl(name, type, min, max, step, defaultValue, newValue, inactive, menu);
    }

    public CameraControl withInactive(boolean newInactive) {
        return new CameraControl(name, type, min, max, step, defaultValue, value, newInactive, menu);
    }

    /** The value moved into the range and onto a step. */
    public int clamp(int wanted) {
        if (type == Type.BOOLEAN) return wanted == 0 ? 0 : 1;
        int v = Math.max(min, Math.min(max, wanted));
        if (type == Type.INTEGER && step > 1) v = min + Math.round((v - min) / (float) step) * step;
        return Math.min(v, max);
    }

    /** True for an automatic switch that turns another control off, such as automatic white balance. */
    public boolean isAutomatic() {
        return name.endsWith("_automatic") || name.equals("auto_exposure") || name.startsWith("focus_auto")
                || name.equals("exposure_auto") || name.equals("white_balance_temperature_auto");
    }

    /**
     * The controls amade-webcam shows, in this order. Others (pan, tilt, privacy, LED) are left out: they are
     * rare, or there are better places for them.
     */
    public static final java.util.List<String> SHOWN = java.util.List.of(
            "brightness", "contrast", "saturation", "hue", "gamma", "sharpness", "backlight_compensation",
            "white_balance_automatic", "white_balance_temperature_auto", "white_balance_temperature",
            "auto_exposure", "exposure_auto", "exposure_time_absolute", "exposure_absolute",
            "exposure_dynamic_framerate", "gain", "focus_automatic_continuous", "focus_auto", "focus_absolute",
            "zoom_absolute", "power_line_frequency");
}
