package eu.amadeos.apps.webcam.ui.main;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The camera picture, scaled to fit and centred on dark grey, with what lies on top of it: the rule-of-thirds
 * grid, the countdown, the REC sign of a recording and the white flash of a photo. Without a picture it shows
 * a camera sign and a few lines that say why.
 */
public final class PreviewPanel extends JComponent {
    private static final Color BACKGROUND = new Color(0x1e1e1e);

    private BufferedImage frame;
    private boolean mirror = true;
    private boolean grid;
    private int countdown;
    private int countdownOf;
    private String recordingText;
    private float flash;
    private Timer flashTimer;
    private String heading;
    private List<String> lines = List.of();
    private boolean crossed;

    public PreviewPanel() {
        setOpaque(true);
        setPreferredSize(new Dimension(736, 414));
        setMinimumSize(new Dimension(320, 180));
        setFocusable(true);
    }

    public void setFrame(BufferedImage newFrame) {
        frame = newFrame;
        heading = null;
        repaint();
    }

    /** No picture: a heading and lines below it; {@code crossed} strikes through the camera sign. */
    public void setMessage(String newHeading, List<String> newLines, boolean newCrossed) {
        frame = null;
        heading = newHeading;
        lines = List.copyOf(newLines);
        crossed = newCrossed;
        repaint();
    }

    public void setMirror(boolean on) {
        mirror = on;
        repaint();
    }

    public void setGrid(boolean on) {
        grid = on;
        repaint();
    }

    public void setCountdown(int secondsLeft, int ofSeconds) {
        countdown = secondsLeft;
        countdownOf = ofSeconds;
        repaint();
    }

    /** "REC 00:42"; null hides it. */
    public void setRecording(String text) {
        recordingText = text;
        repaint();
    }

    /** White over the picture that fades in a fifth of a second. */
    public void flash() {
        flash = 0.9f;
        if (flashTimer != null) flashTimer.stop();
        flashTimer = new Timer(20, e -> {
            flash -= 0.09f;
            if (flash <= 0) {
                flash = 0;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        flashTimer.start();
        repaint();
    }

    public boolean hasFrame() {
        return frame != null;
    }

    public String heading() {
        return heading;
    }

    /** Where the picture is drawn: as large as fits, same aspect ratio, centred. */
    static Rectangle fit(int imageWidth, int imageHeight, int width, int height) {
        double scale = Math.min(width / (double) imageWidth, height / (double) imageHeight);
        int w = (int) Math.round(imageWidth * scale), h = (int) Math.round(imageHeight * scale);
        return new Rectangle((width - w) / 2, (height - h) / 2, w, h);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        BufferedImage f = frame;
        if (f == null) {
            paintMessage(g);
            g.dispose();
            return;
        }
        Rectangle r = fit(f.getWidth(), f.getHeight(), getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (mirror) {
            g.drawImage(f, r.x + r.width, r.y, r.x, r.y + r.height, 0, 0, f.getWidth(), f.getHeight(), null);
        } else {
            g.drawImage(f, r.x, r.y, r.width, r.height, null);
        }
        if (grid) paintGrid(g, r);
        if (countdown > 0) paintCountdown(g, r);
        if (recordingText != null) paintRecording(g, r);
        if (flash > 0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, flash)));
            g.setColor(Color.WHITE);
            g.fill(r);
        }
        g.dispose();
    }

    private static void paintGrid(Graphics2D g, Rectangle r) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 110));
        for (int i = 1; i < 3; i++) {
            int x = r.x + r.width * i / 3, y = r.y + r.height * i / 3;
            g.drawLine(x, r.y, x, r.y + r.height);
            g.drawLine(r.x, y, r.x + r.width, y);
        }
    }

    private void paintCountdown(Graphics2D g, Rectangle r) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(r);
        int radius = Math.max(40, Math.min(r.width, r.height) / 3);
        double cx = r.getCenterX(), cy = r.getCenterY();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (radius * 1.4)));
        FontMetrics fm = g.getFontMetrics();
        String n = String.valueOf(countdown);
        int tx = (int) (cx - fm.stringWidth(n) / 2.0), ty = (int) (cy + fm.getAscent() / 2.0 - fm.getDescent() / 2.0);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(n, tx + 4, ty + 4);
        g.setColor(Color.WHITE);
        g.drawString(n, tx, ty);
        g.setStroke(new BasicStroke(Math.max(3, radius / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius));
        g.setColor(Color.WHITE);
        double part = countdownOf <= 0 ? 1 : countdown / (double) countdownOf;
        g.draw(new Arc2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius, 90, -360 * part, Arc2D.OPEN));
    }

    private void paintRecording(Graphics2D g, Rectangle r) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(recordingText) + 36, h = 28, x = r.x + 14, y = r.y + 14;
        g.setColor(new Color(0, 0, 0, 160));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        boolean on = (System.currentTimeMillis() / 600) % 2 == 0;
        g.setColor(on ? new Color(0xe53935) : new Color(0x7a1f1d));
        g.fill(new Ellipse2D.Double(x + 10, y + 8, 12, 12));
        g.setColor(Color.WHITE);
        g.drawString(recordingText, x + 28, y + 19);
    }

    private void paintMessage(Graphics2D g) {
        if (heading == null) return;
        int cx = getWidth() / 2, cy = getHeight() / 2 - 50;
        g.setColor(new Color(0x8a8a8a));
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(cx - 60, cy - 34, 90, 68, 14, 14));
        Path2D lens = new Path2D.Double();
        lens.moveTo(cx + 30, cy - 12);
        lens.lineTo(cx + 62, cy - 30);
        lens.lineTo(cx + 62, cy + 30);
        lens.lineTo(cx + 30, cy + 12);
        g.draw(lens);
        if (crossed) {
            g.setColor(new Color(0xc0504d));
            g.drawLine(cx - 72, cy + 46, cx + 72, cy - 46);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        center(g, heading, cx, cy + 84, Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        int y = cy + 110;
        for (String line : lines) {
            center(g, line, cx, y, new Color(0xc8c8c8));
            y += 20;
        }
    }

    private static void center(Graphics2D g, String s, int cx, int y, Color c) {
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }
}
