package eu.amadeos.apps.webcam.domain;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookTest {
    private static BufferedImage frame(Color color) {
        BufferedImage f = new BufferedImage(4, 2, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) f.setRGB(x, y, color.getRGB());
        return f;
    }

    private static Color pixel(Look look, Color color) {
        BufferedImage f = frame(color);
        look.apply(f);
        return new Color(f.getRGB(3, 1));
    }

    @Test
    void normalChangesNothing() {
        assertEquals(new Color(10, 200, 30), pixel(Look.NORMAL, new Color(10, 200, 30)));
    }

    @Test
    void blackAndWhiteIsGrey() {
        Color c = pixel(Look.BLACK_AND_WHITE, new Color(200, 40, 90));
        assertEquals(c.getRed(), c.getGreen());
        assertEquals(c.getGreen(), c.getBlue());
    }

    @Test
    void sepiaIsBrownish() {
        Color c = pixel(Look.SEPIA, new Color(128, 128, 128));
        assertTrue(c.getRed() > c.getGreen() && c.getGreen() > c.getBlue(), c.toString());
    }

    @Test
    void warmAndCool() {
        Color warm = pixel(Look.WARM, new Color(128, 128, 128));
        assertTrue(warm.getRed() > warm.getBlue());
        Color cool = pixel(Look.COOL, new Color(128, 128, 128));
        assertTrue(cool.getBlue() > cool.getRed());
    }

    @Test
    void staysInRange() {
        assertEquals(255, pixel(Look.WARM, Color.WHITE).getRed());
        assertEquals(0, pixel(Look.WARM, Color.BLACK).getBlue());
    }

    @Test
    void onlyCameraFrames() {
        BufferedImage rgb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> Look.SEPIA.apply(rgb));
    }
}
