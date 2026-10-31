package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.bootstrap.AmadeWebcamApplication;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.PhotoTimer;
import eu.amadeos.apps.webcam.domain.QrContent;
import eu.amadeos.apps.webcam.domain.Shots;
import eu.amadeos.apps.webcam.domain.VideoSpeed;
import eu.amadeos.apps.webcam.ui.settings.SettingsDialog;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The window: the picture, the control bar under it (photo or video, timer or speed, the big button, look,
 * mirror, QR codes, the last shots, settings) and the status bar.
 */
public final class MainWindow extends JFrame implements MainView {
    /** A value with the text shown for it in a combo box. */
    record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Messages msg;
    private final LocaleSettings locales;
    private final PreviewPanel preview = new PreviewPanel();
    private final JPanel picturePanel = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel(" ");
    private final ShutterButton shutter = new ShutterButton();
    private final LevelMeter level = new LevelMeter();
    private final JToggleButton photoButton = new JToggleButton();
    private final JToggleButton videoButton = new JToggleButton();
    private final JPanel modeCards = new JPanel(new CardLayout());
    private final JComboBox<Choice<PhotoTimer>> timer = new JComboBox<>();
    private final JComboBox<Choice<Shots>> shots = new JComboBox<>();
    private final JComboBox<Choice<VideoSpeed>> speed = new JComboBox<>();
    private final JComboBox<Choice<Look>> look = new JComboBox<>();
    private final JCheckBox mirror = new JCheckBox();
    private final JToggleButton qr = new JToggleButton();
    private final JButton settingsButton = new JButton();
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private RecentStrip recentStrip;
    private QrBanner qrBanner;
    private SettingsDialog settings;
    private JFrame fullScreenWindow;
    /** True while the window itself changes the controls, so that their listeners stay quiet. */
    private boolean updating;
    private boolean timeLapse;

    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-webcam.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);

        preview.setName("preview");
        qrBanner = new QrBanner(msg, presenter);
        picturePanel.setBorder(AmadeUI.sunken());
        picturePanel.add(qrBanner, BorderLayout.NORTH);
        picturePanel.add(preview, BorderLayout.CENTER);
        recentStrip = new RecentStrip(msg, presenter);

        JPanel centre = new JPanel(new BorderLayout(0, 4));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(picturePanel, BorderLayout.CENTER);
        centre.add(controlBar(), BorderLayout.SOUTH);

        status.setName("status");
        status.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        statusBar.add(status);

        add(centre, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        bindKeys(getRootPane());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        setSize(Math.round(820 * AmadeUI.fontScale()), Math.round(640 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- control bar

    /**
     * Two short rows on each side of the big button: on the left Photo/Video and the timer, burst or speed; on
     * the right the last shots with Settings, and the look, mirror and QR switches.
     */
    private JComponent controlBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setName("controls");
        bar.setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        Mnemonics.apply(photoButton, msg.get("control.photo"));
        Mnemonics.apply(videoButton, msg.get("control.video"));
        photoButton.setName("control.photo");
        videoButton.setName("control.video");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(photoButton);
        modeGroup.add(videoButton);
        photoButton.addActionListener(e -> presenter.setCaptureMode(CaptureMode.PHOTO));
        videoButton.addActionListener(e -> presenter.setCaptureMode(CaptureMode.VIDEO));
        fill(timer, PhotoTimer.values(), t -> t == PhotoTimer.OFF ? msg.get("timer.off")
                : msg.format("timer.seconds", t.seconds()), "control.timer", presenter::setTimer);
        fill(shots, Shots.values(), s -> s == Shots.SINGLE ? msg.get("shots.single")
                : msg.format("shots.burst", s.count()), "control.shots", presenter::setShots);
        fill(speed, VideoSpeed.values(), v -> v == VideoSpeed.NORMAL ? msg.get("speed.normal")
                : msg.format("speed.lapse", v.secondsPerPicture()), "control.speed", presenter::setSpeed);

        JPanel modes = row(photoButton, videoButton);
        JPanel photoCard = row(label("control.timer", timer), timer, shots);
        level.setName("level");
        JPanel videoCard = row(label("control.speed", speed), speed, new JLabel(msg.get("control.mic")), level);
        modeCards.add(photoCard, CaptureMode.PHOTO.name());
        modeCards.add(videoCard, CaptureMode.VIDEO.name());
        bar.add(column(modes, modeCards), BorderLayout.WEST);

        JPanel mid = new JPanel(new java.awt.GridBagLayout());
        shutter.setName("shutter");
        shutter.setToolTipText(msg.get("control.shutter"));
        shutter.addActionListener(e -> presenter.shutter());
        mid.add(shutter);
        bar.add(mid, BorderLayout.CENTER);

        fill(look, Look.values(), l -> Mnemonics.strip(msg.get(l.textKey())), "control.look", presenter::setLook);
        look.setToolTipText(msg.get("control.look"));
        Mnemonics.apply(mirror, msg.get("control.mirror"));
        mirror.setName("control.mirror");
        mirror.addActionListener(e -> {
            if (!updating) presenter.setMirror(mirror.isSelected());
        });
        qr.setText(msg.get("control.qr"));
        qr.setName("control.qr");
        qr.setToolTipText(msg.get("control.qr.tip"));
        qr.addActionListener(e -> {
            if (!updating) presenter.setQrCodes(qr.isSelected());
        });
        Mnemonics.apply(settingsButton, msg.get("control.settings"));
        settingsButton.setName("control.settings");
        settingsButton.addActionListener(e -> presenter.openSettings());
        JPanel top = row(recentStrip, settingsButton);
        JPanel bottom = row(look, mirror, qr);
        ((FlowLayout) top.getLayout()).setAlignment(FlowLayout.RIGHT);
        ((FlowLayout) bottom.getLayout()).setAlignment(FlowLayout.RIGHT);
        bar.add(column(top, bottom), BorderLayout.EAST);
        // Space and Enter always take the photo: no button but the shutter keeps the keyboard focus.
        for (JComponent c : List.of(photoButton, videoButton, mirror, qr, settingsButton)) c.setFocusable(false);
        return bar;
    }

    private static JPanel row(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        for (JComponent c : parts) p.add(c);
        return p;
    }

    private static JPanel column(JComponent top, JComponent bottom) {
        JPanel p = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        p.add(top);
        p.add(bottom);
        return p;
    }

    private <T> void fill(JComboBox<Choice<T>> box, T[] values, Function<T, String> label, String name,
                          Consumer<T> chosen) {
        box.setName(name);
        for (T value : values) box.addItem(new Choice<>(value, label.apply(value)));
        box.setFocusable(false);
        box.addActionListener(e -> {
            if (updating || box.getSelectedItem() == null) return;
            @SuppressWarnings("unchecked") Choice<T> c = (Choice<T>) box.getSelectedItem();
            chosen.accept(c.value());
        });
    }

    private JLabel label(String key, JComponent target) {
        JLabel l = Mnemonics.apply(new JLabel(), msg.get(key));
        l.setLabelFor(target);
        return l;
    }

    private static <T> void select(JComboBox<Choice<T>> box, T value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).value() == value) box.setSelectedIndex(i);
        }
    }

    /** Keys that are not in the menus. */
    private void bindKeys(JRootPane root) {
        Map<KeyStroke, Runnable> keys = new LinkedHashMap<>();
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), presenter::shutter);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), presenter::shutter);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), presenter::escape);
        bind(root, keys);
    }

    private static void bind(JRootPane root, Map<KeyStroke, Runnable> keys) {
        int i = 0;
        for (Map.Entry<KeyStroke, Runnable> key : keys.entrySet()) {
            String name = "key." + i++;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key.getKey(), name);
            root.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    key.getValue().run();
                }
            });
        }
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showFrame(BufferedImage frame) {
        preview.setFrame(frame);
    }

    @Override
    public void showNoPicture(NoPicture reason, String detail) {
        String key = "nopicture." + reason.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 1; msg.has(key + "." + i); i++) lines.add(msg.get(key + "." + i));
        if (!detail.isBlank() && reason == NoPicture.FAILED) lines.add(detail);
        preview.setMessage(msg.get(key), lines, reason != NoPicture.STARTING);
    }

    @Override
    public void showCameras(List<CameraDevice> cameras, Optional<CameraDevice> current) {
        menuBar.showCameras(cameras, current);
    }

    @Override
    public void showStatus(String text) {
        status.setText(text == null || text.isEmpty() ? " " : text);
    }

    @Override
    public void showTitle(String title) {
        setTitle(title);
    }

    @Override
    public void showMode(CaptureMode mode, boolean recording, boolean camera) {
        updating = true;
        try {
            photoButton.setSelected(mode == CaptureMode.PHOTO);
            videoButton.setSelected(mode == CaptureMode.VIDEO);
            photoButton.setEnabled(!recording);
            videoButton.setEnabled(!recording);
            ((CardLayout) modeCards.getLayout()).show(modeCards, mode.name());
            speed.setEnabled(!recording);
            shutter.setFace(mode == CaptureMode.PHOTO ? ShutterButton.Face.PHOTO
                    : recording ? ShutterButton.Face.STOP : ShutterButton.Face.RECORD);
            shutter.setEnabled(camera);
            shutter.setToolTipText(msg.get(mode == CaptureMode.PHOTO ? "control.shutter"
                    : recording ? "control.stop" : "control.record"));
            menuBar.showMode(mode, recording, camera);
        } finally {
            updating = false;
        }
    }

    @Override
    public void showOptions(Options options) {
        updating = true;
        try {
            preview.setMirror(options.mirror());
            preview.setGrid(options.grid());
            mirror.setSelected(options.mirror());
            qr.setSelected(options.qrCodes());
            select(look, options.look());
            select(timer, options.timer());
            select(shots, options.shots());
            select(speed, options.speed());
            menuBar.showOptions(options);
        } finally {
            updating = false;
        }
    }

    @Override
    public void showCountdown(int secondsLeft, int ofSeconds) {
        preview.setCountdown(secondsLeft, ofSeconds);
    }

    @Override
    public void showRecording(Optional<Duration> elapsed, boolean lapse) {
        timeLapse = lapse;
        preview.setRecording(elapsed.map(d -> (lapse ? msg.get("recording.lapse") : msg.get("recording.rec"))
                + "  " + clock(d)).orElse(null));
        setTitle(elapsed.map(d -> msg.format("window.title.recording", clock(d))).orElse(msg.get("window.title")));
    }

    static String clock(Duration d) {
        long s = d.toSeconds();
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%02d:%02d", s / 60, s % 60);
    }

    @Override
    public void showLevel(double value) {
        level.setLevel(value);
    }

    @Override
    public void flash() {
        preview.flash();
    }

    @Override
    public void showQr(Optional<QrContent> content) {
        if (content.isPresent()) {
            qrBanner.show(content.get());
        } else {
            qrBanner.setVisible(false);
        }
        picturePanel.revalidate();
    }

    @Override
    public void showRecent(List<Recent> recent) {
        recentStrip.show(recent);
    }

    // --8<-- [start:fullscreen]
    /**
     * Full screen moves the picture into a window without frame that covers the screen; Escape or F11 brings
     * it back. The window manager is asked through {@link GraphicsDevice#setFullScreenWindow}, which keeps the
     * screen resolution as it is.
     */
    @Override
    public void showFullScreen(boolean on) {
        GraphicsDevice device = getGraphicsConfiguration() == null
                ? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                : getGraphicsConfiguration().getDevice();
        if (on && fullScreenWindow == null) {
            fullScreenWindow = new JFrame(getTitle());
            fullScreenWindow.setUndecorated(true);
            fullScreenWindow.setName("full-screen");
            picturePanel.remove(preview);
            fullScreenWindow.add(preview);
            Map<KeyStroke, Runnable> keys = new LinkedHashMap<>();
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), presenter::shutter);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), presenter::shutter);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), presenter::escape);
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), presenter::toggleFullScreen);
            bind(fullScreenWindow.getRootPane(), keys);
            if (device.isFullScreenSupported()) {
                device.setFullScreenWindow(fullScreenWindow);
            } else {
                fullScreenWindow.setBounds(device.getDefaultConfiguration().getBounds());
                fullScreenWindow.setVisible(true);
            }
            fullScreenWindow.toFront();
            preview.requestFocusInWindow();
        } else if (!on && fullScreenWindow != null) {
            if (device.getFullScreenWindow() == fullScreenWindow) device.setFullScreenWindow(null);
            fullScreenWindow.remove(preview);
            fullScreenWindow.dispose();
            fullScreenWindow = null;
            picturePanel.add(preview, BorderLayout.CENTER);
            picturePanel.revalidate();
            toFront();
        }
    }
    // --8<-- [end:fullscreen]

    @Override
    public void showSettings(SettingsModel model) {
        if (settings == null) {
            settings = new SettingsDialog(this, msg, presenter);
            settings.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    settings = null;
                    presenter.settingsClosed();
                }
            });
            settings.show(model);
            settings.setLocationRelativeTo(this);
            settings.setVisible(true);
        } else {
            settings.show(model);
        }
    }

    @Override
    public void copyText(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException e) {
            showError(e.getMessage());
        }
    }

    @Override
    public boolean confirmDelete(String fileName) {
        Object[] options = {msg.get("dialog.delete.delete"), msg.get("dialog.cancel")};
        return JOptionPane.showOptionDialog(this, msg.format("dialog.delete.text", fileName),
                msg.get("dialog.delete.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[1]) == 0;
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-webcam " + AmadeWebcamApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>" + msg.get("dialog.about.libraries") + "</small><br><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        if (fullScreenWindow != null) showFullScreen(false);
        if (settings != null) settings.dispose();
        dispose();
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(40);
        return text;
    }

    // ---------------------------------------------------------------- for tests and screenshots

    PreviewPanel preview() {
        return preview;
    }

    MainMenuBar menu() {
        return menuBar;
    }

    ShutterButton shutter() {
        return shutter;
    }

    QrBanner qrBanner() {
        return qrBanner;
    }

    RecentStrip recentStrip() {
        return recentStrip;
    }

    String statusText() {
        return status.getText();
    }

    SettingsDialog settingsDialog() {
        return settings;
    }

    boolean timeLapse() {
        return timeLapse;
    }

    JFrame fullScreenWindow() {
        return fullScreenWindow;
    }
}
