package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The big round button: a white disc for a photo, a red dot to start a video and a red square to stop it.
 * Painted in the bevel colours of the theme.
 */
public final class ShutterButton extends JButton {
    public enum Face { PHOTO, RECORD, STOP }

    private Face face = Face.PHOTO;

    public ShutterButton() {
        setPreferredSize(new Dimension(58, 58));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
    }

    public void setFace(Face newFace) {
        face = newFace;
        repaint();
    }

    public Face face() {
        return face;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int d = Math.min(getWidth(), getHeight()) - 4;
        double x = (getWidth() - d) / 2.0, y = (getHeight() - d) / 2.0;
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        Color light = AmadeUI.theme().highlight(), dark = AmadeUI.theme().shadow();
        g.setColor(pressed ? light : dark);
        g.fill(new Ellipse2D.Double(x + 2, y + 2, d, d));
        g.setColor(pressed ? dark : light);
        g.fill(new Ellipse2D.Double(x, y, d, d));
        g.setColor(AmadeUI.theme().base());
        g.fill(new Ellipse2D.Double(x + 2, y + 2, d - 3, d - 3));
        double cx = x + d / 2.0 + (pressed ? 1 : 0), cy = y + d / 2.0 + (pressed ? 1 : 0);
        Color red = isEnabled() ? new Color(0xd23a33) : new Color(0xa8a8a8);
        switch (face) {
            case PHOTO -> {
                double r = d * 0.32;
                g.setColor(isEnabled() ? new Color(0xf4f4f4) : new Color(0xa8a8a8));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g.setColor(dark);
                g.setStroke(new BasicStroke(2));
                g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }
            case RECORD -> {
                double r = d * 0.26;
                g.setColor(red);
                g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }
            case STOP -> {
                double r = d * 0.2;
                g.setColor(red);
                g.fill(new RoundRectangle2D.Double(cx - r, cy - r, 2 * r, 2 * r, 6, 6));
            }
        }
        if (hasFocus()) {
            g.setColor(AmadeUI.theme().textActive());
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 2}, 0));
            g.draw(new Ellipse2D.Double(x - 1, y - 1, d + 2, d + 2));
        }
        g.dispose();
    }
}
