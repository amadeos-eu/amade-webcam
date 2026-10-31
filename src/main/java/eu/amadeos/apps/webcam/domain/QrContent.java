package eu.amadeos.apps.webcam.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a QR code holds: a web link, a WLAN login, or plain text.
 *
 * @param fields for {@link Kind#WIFI}: {@code S} network name, {@code T} security, {@code P} password,
 *               {@code H} hidden; empty for the other kinds
 */
public record QrContent(Kind kind, String text, Map<String, String> fields) {
    public enum Kind { LINK, WIFI, MAIL, PHONE, TEXT }

    public QrContent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        fields = Map.copyOf(fields);
    }

    public static QrContent of(String text) {
        String t = text.strip();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return new QrContent(Kind.LINK, t, Map.of());
        if (lower.startsWith("wifi:")) return new QrContent(Kind.WIFI, t, wifi(t.substring(5)));
        if (lower.startsWith("mailto:")) return new QrContent(Kind.MAIL, t, Map.of());
        if (lower.startsWith("tel:")) return new QrContent(Kind.PHONE, t, Map.of());
        return new QrContent(Kind.TEXT, t, Map.of());
    }

    /** The network name of a WLAN code. */
    public String wifiName() {
        return fields.getOrDefault("S", "");
    }

    public String wifiPassword() {
        return fields.getOrDefault("P", "");
    }

    /**
     * {@code S:Home;T:WPA;P:se\;cret;;} to its fields. Backslash escapes {@code ; , : \ "}, as the format
     * (from the ZXing project, used by all phones) says.
     */
    static Map<String, String> wifi(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        StringBuilder part = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                part.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ';') {
                field(fields, part.toString());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        field(fields, part.toString());
        return fields;
    }

    private static void field(Map<String, String> fields, String part) {
        int colon = part.indexOf(':');
        if (colon <= 0) return;
        String value = part.substring(colon + 1);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        fields.putIfAbsent(part.substring(0, colon).toUpperCase(Locale.ROOT), value);
    }
}
