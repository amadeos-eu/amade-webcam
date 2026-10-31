package eu.amadeos.apps.webcam.domain;

import java.util.Locale;

public enum PhotoFormat {
    JPEG("jpg", "jpeg"), PNG("png", "png");

    private final String extension;
    private final String imageIoName;

    PhotoFormat(String extension, String imageIoName) {
        this.extension = extension;
        this.imageIoName = imageIoName;
    }

    public String extension() {
        return extension;
    }

    /** The name ImageIO writes it under. */
    public String imageIoName() {
        return imageIoName;
    }

    public static PhotoFormat parse(String value, PhotoFormat fallback) {
        try {
            return value == null ? fallback : valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
