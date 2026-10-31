package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** The microphone level as 16 green, yellow and red segments. */
public final class LevelMeter extends JComponent {
    private static final int SEGMENTS = 16;
    private double level;

    public LevelMeter() {
        setPreferredSize(new Dimension(110, 14));
    }

    public void setLevel(double value) {
        level = Math.max(0, Math.min(1, value));
        repaint();
    }

    public double level() {
        return level;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        int y = (getHeight() - 14) / 2;
        AmadeUI.paintBevel(g, 0, y, getWidth(), 14, false, 1);
        int seg = (getWidth() - 4) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            boolean on = i < SEGMENTS * level;
            Color c = i < 11 ? new Color(0x43a047) : i < 14 ? new Color(0xf9a825) : new Color(0xe53935);
            g.setColor(on ? c : c.darker().darker());
            g.fillRect(2 + i * seg, y + 2, seg - 2, 10);
        }
        g.dispose();
    }
}
