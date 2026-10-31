package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.VideoMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads what {@code v4l2-ctl} prints. Pure text work, so that it can be tested with saved output. */
public final class V4l2Output {
    private static final Pattern FORMAT = Pattern.compile("^\\s*\\[\\d+]:\\s*'([^']+)'");
    private static final Pattern SIZE = Pattern.compile("^\\s*Size:\\s*Discrete\\s+(\\d+)x(\\d+)");
    private static final Pattern STEPWISE = Pattern.compile(
            "^\\s*Size:\\s*(?:Stepwise|Continuous)\\s+(\\d+)x(\\d+)\\s*-\\s*(\\d+)x(\\d+)");
    private static final Pattern INTERVAL = Pattern.compile("^\\s*Interval:\\s*Discrete\\s+[\\d.]+s\\s*\\(([\\d.]+) fps\\)");
    private static final Pattern CONTROL = Pattern.compile(
            "^\\s*([a-z0-9_]+)\\s+0x[0-9a-f]+\\s+\\((int|bool|menu|intmenu)\\)\\s*:(.*)$");
    private static final Pattern MENU_ITEM = Pattern.compile("^\\s*(-?\\d+):\\s*(.+?)\\s*$");
    private static final Pattern VALUE = Pattern.compile("(min|max|step|default|value)=(-?\\d+)");

    private V4l2Output() {
    }

    /**
     * {@code v4l2-ctl --list-formats-ext}: every format, size and frame rate. Sizes without frame rates count
     * as 30 fps; stepwise sizes give their smallest and largest size.
     */
    public static List<VideoMode> formats(String text) {
        List<VideoMode> modes = new ArrayList<>();
        String fourcc = null;
        int width = 0, height = 0;
        boolean sizeHasRate = true;
        for (String line : text.split("\\R")) {
            Matcher m;
            if ((m = FORMAT.matcher(line)).find()) {
                if (!sizeHasRate) modes.add(new VideoMode(fourcc, width, height, 30));
                fourcc = m.group(1).strip();
                sizeHasRate = true;
            } else if (fourcc != null && (m = SIZE.matcher(line)).find()) {
                if (!sizeHasRate) modes.add(new VideoMode(fourcc, width, height, 30));
                width = Integer.parseInt(m.group(1));
                height = Integer.parseInt(m.group(2));
                sizeHasRate = false;
            } else if (fourcc != null && (m = STEPWISE.matcher(line)).find()) {
                if (!sizeHasRate) modes.add(new VideoMode(fourcc, width, height, 30));
                modes.add(new VideoMode(fourcc, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), 30));
                width = Integer.parseInt(m.group(3));
                height = Integer.parseInt(m.group(4));
                sizeHasRate = false;
            } else if (fourcc != null && width > 0 && (m = INTERVAL.matcher(line)).find()) {
                double fps = Double.parseDouble(m.group(1));
                if (fps > 0) modes.add(new VideoMode(fourcc, width, height, fps));
                sizeHasRate = true;
            }
        }
        if (!sizeHasRate && fourcc != null && width > 0) modes.add(new VideoMode(fourcc, width, height, 30));
        return modes.stream().distinct().toList();
    }

    /** {@code v4l2-ctl --list-ctrls-menus}: the controls with their ranges, values and menu entries. */
    public static List<CameraControl> controls(String text) {
        List<CameraControl> controls = new ArrayList<>();
        String name = null;
        CameraControl.Type type = null;
        Map<String, Integer> values = Map.of();
        boolean inactive = false;
        Map<Integer, String> menu = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            Matcher m = CONTROL.matcher(line);
            if (m.find()) {
                if (name != null) controls.add(control(name, type, values, inactive, menu));
                name = m.group(1);
                type = switch (m.group(2)) {
                    case "bool" -> CameraControl.Type.BOOLEAN;
                    case "menu", "intmenu" -> CameraControl.Type.MENU;
                    default -> CameraControl.Type.INTEGER;
                };
                values = new LinkedHashMap<>();
                Matcher v = VALUE.matcher(m.group(3));
                while (v.find()) values.put(v.group(1), Integer.parseInt(v.group(2)));
                inactive = m.group(3).contains("flags=") && m.group(3).replaceAll(".*flags=", "").contains("inactive");
                menu = new LinkedHashMap<>();
            } else if (name != null && type == CameraControl.Type.MENU && (m = MENU_ITEM.matcher(line)).find()
                    && !line.contains("0x")) {
                menu.put(Integer.parseInt(m.group(1)), m.group(2));
            } else if (!line.isBlank() && !line.startsWith("\t\t") && !Character.isWhitespace(line.charAt(0))) {
                // A heading like "User Controls" ends the menu of the control before it.
                if (name != null) controls.add(control(name, type, values, inactive, menu));
                name = null;
            }
        }
        if (name != null) controls.add(control(name, type, values, inactive, menu));
        return controls;
    }

    private static CameraControl control(String name, CameraControl.Type type, Map<String, Integer> v,
                                         boolean inactive, Map<Integer, String> menu) {
        int min = v.getOrDefault("min", 0);
        int max = v.getOrDefault("max", type == CameraControl.Type.BOOLEAN ? 1 : 0);
        int def = v.getOrDefault("default", min);
        return new CameraControl(name.toLowerCase(Locale.ROOT), type, min, max, v.getOrDefault("step", 1), def,
                v.getOrDefault("value", def), inactive, menu);
    }
}
