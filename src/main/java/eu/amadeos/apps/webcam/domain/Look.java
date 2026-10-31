package eu.amadeos.apps.webcam.domain;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Colour looks for the picture. They change the frames themselves, so photos and videos get them too.
 * Frames are {@link BufferedImage#TYPE_3BYTE_BGR}, as ffmpeg delivers them; a look works on the bytes directly,
 * which keeps it fast enough for 30 frames a second.
 */
public enum Look {
    NORMAL, BLACK_AND_WHITE, SEPIA, WARM, COOL;

    /** Changes the picture in place; {@link #NORMAL} leaves it as it is. */
    public void apply(BufferedImage frame) {
        if (this == NORMAL) return;
        if (frame.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            throw new IllegalArgumentException("Frames must be TYPE_3BYTE_BGR, not type " + frame.getType());
        }
        byte[] px = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
        for (int i = 0; i + 2 < px.length; i += 3) {
            int b = px[i] & 0xff, g = px[i + 1] & 0xff, r = px[i + 2] & 0xff;
            int nr, ng, nb;
            switch (this) {
                case BLACK_AND_WHITE -> {
                    int y = (r * 77 + g * 150 + b * 29) >> 8;
                    nr = ng = nb = y;
                }
                case SEPIA -> {
                    nr = (r * 101 + g * 197 + b * 48) >> 8;
                    ng = (r * 89 + g * 176 + b * 43) >> 8;
                    nb = (r * 70 + g * 137 + b * 34) >> 8;
                }
                case WARM -> {
                    nr = r + 22;
                    ng = g + 6;
                    nb = b - 18;
                }
                default -> { // COOL
                    nr = r - 16;
                    ng = g + 2;
                    nb = b + 22;
                }
            }
            px[i] = (byte) clamp(nb);
            px[i + 1] = (byte) clamp(ng);
            px[i + 2] = (byte) clamp(nr);
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** The key of its name in the texts, {@code look.sepia}. */
    public String textKey() {
        return "look." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
