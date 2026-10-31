package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * The last photos and videos as small pictures. A click opens one; the right mouse button offers Open in Image
 * Editor, Show in Folder and Delete.
 */
public final class RecentStrip extends JPanel {
    static final int WIDTH = 48;
    static final int HEIGHT = 27;

    private final Messages msg;
    private final MainPresenter presenter;

    public RecentStrip(Messages msg, MainPresenter presenter) {
        super(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        this.msg = msg;
        this.presenter = presenter;
        setName("recent");
        setPreferredSize(new Dimension(3 * (WIDTH + 7) + 6, HEIGHT + 4));
    }

    public void show(List<MainView.Recent> items) {
        removeAll();
        for (MainView.Recent item : items) add(button(item));
        revalidate();
        repaint();
    }

    private JButton button(MainView.Recent item) {
        JButton b = new JButton(new ImageIcon(thumb(item)));
        b.setName("recent." + item.file().getFileName());
        b.setBorder(eu.amadeos.commons.swing.AmadeUI.raised());
        b.setPreferredSize(new Dimension(WIDTH + 4, HEIGHT + 4));
        b.setToolTipText(item.file().getFileName().toString());
        b.setFocusable(false);
        b.addActionListener(e -> presenter.openRecent(item.file()));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                if (e.isPopupTrigger()) menu(item.file()).show(b, e.getX(), e.getY());
            }
        });
        return b;
    }

    JPopupMenu menu(Path file) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("recent.open", () -> presenter.openRecent(file)));
        if (presenter.canEdit(file)) menu.add(item("recent.edit", () -> presenter.editRecent(file)));
        menu.add(item("recent.folder", () -> presenter.showInFolder(file)));
        menu.addSeparator();
        menu.add(item("recent.delete", () -> presenter.deleteRecent(file)));
        return menu;
    }

    private JMenuItem item(String key, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    /** The thumbnail cut to the strip's size; a video gets a play sign. */
    private static BufferedImage thumb(MainView.Recent item) {
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BufferedImage t = item.thumbnail();
        double scale = Math.max(WIDTH / (double) t.getWidth(), HEIGHT / (double) t.getHeight());
        int w = (int) Math.ceil(t.getWidth() * scale), h = (int) Math.ceil(t.getHeight() * scale);
        g.drawImage(t, (WIDTH - w) / 2, (HEIGHT - h) / 2, w, h, null);
        if (item.video()) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, HEIGHT - 10, WIDTH, 10);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g.fillPolygon(new int[]{3, 3, 9}, new int[]{HEIGHT - 9, HEIGHT - 2, HEIGHT - 5}, 3);
            g.drawString("Video", 12, HEIGHT - 2);
        }
        g.dispose();
        return out;
    }
}
