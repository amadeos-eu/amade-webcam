package eu.amadeos.apps.webcam.ui.settings;

import eu.amadeos.apps.webcam.domain.CameraControl;
import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.PhotoFormat;
import eu.amadeos.apps.webcam.domain.VideoFormat;
import eu.amadeos.apps.webcam.domain.VideoMode;
import eu.amadeos.apps.webcam.repository.WebcamPreferences;
import eu.amadeos.apps.webcam.service.Microphones;
import eu.amadeos.apps.webcam.ui.main.MainPresenter;
import eu.amadeos.apps.webcam.ui.main.SettingsModel;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Camera Settings: on the left the camera, its resolution and frame rate, the microphone and how and where
 * photos and videos are saved; on the right the camera's own controls, built from what the camera reports.
 * Every change applies at once, so the dialog has only Close.
 */
public final class SettingsDialog extends JDialog {
    private static final int LABEL_WIDTH = 104;

    /** A value with its text in a combo box. */
    record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Messages msg;
    private final MainPresenter presenter;
    private final JPanel left = column();
    private final JPanel right = column();
    private final Map<String, JComponent> controlWidgets = new HashMap<>();
    /** Sliders send their value a moment after the last move, not for every pixel. */
    private final Map<String, Timer> pending = new HashMap<>();
    private boolean updating;
    private SettingsModel shown;

    public SettingsDialog(JFrame owner, Messages msg, MainPresenter presenter) {
        super(owner, msg.get("settings.title"), false);
        this.msg = msg;
        this.presenter = presenter;
        setName("settings");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel cols = new JPanel(new GridLayout(1, 2, 16, 0));
        cols.add(left);
        cols.add(right);
        content.add(cols, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        JButton close = Mnemonics.apply(new JButton(), msg.get("settings.close"));
        close.setName("settings.close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Shows the model; rebuilds only what changed, so a slider being dragged is not replaced. */
    public void show(SettingsModel model) {
        updating = true;
        try {
            if (shown == null || !sameLeft(shown, model)) buildLeft(model);
            if (shown == null || !Objects.equals(shown.controls(), model.controls())
                    || !Objects.equals(shown.camera(), model.camera())) {
                buildRight(model);
            }
            shown = model;
        } finally {
            updating = false;
        }
        pack();
        setMinimumSize(getPreferredSize());
    }

    private static boolean sameLeft(SettingsModel a, SettingsModel b) {
        return a.cameras().equals(b.cameras()) && a.camera().equals(b.camera()) && a.modes().equals(b.modes())
                && a.mode().equals(b.mode()) && a.microphones().equals(b.microphones())
                && a.photoFolder().equals(b.photoFolder()) && a.videoFolder().equals(b.videoFolder());
    }

    // ---------------------------------------------------------------- left: camera, sound, saving

    private void buildLeft(SettingsModel m) {
        left.removeAll();
        left.add(section("settings.camera"));
        List<Choice<CameraDevice>> cameras = new ArrayList<>();
        for (CameraDevice c : m.cameras()) cameras.add(new Choice<>(c, c.label()));
        left.add(row("settings.camera.device", combo("settings.camera.device", cameras, m.camera().orElse(null),
                presenter::chooseCamera)));

        List<VideoMode> modes = m.modes();
        List<Choice<VideoMode>> sizes = new ArrayList<>();
        VideoMode current = m.mode().orElse(null);
        // One entry per size and format, at the best frame rate; the rate box offers the others.
        for (VideoMode mode : modes) {
            boolean seen = sizes.stream().anyMatch(c -> c.value().width() == mode.width()
                    && c.value().height() == mode.height() && c.value().fourcc().equals(mode.fourcc()));
            if (!seen) {
                VideoMode best = modes.stream().filter(o -> o.width() == mode.width() && o.height() == mode.height()
                        && o.fourcc().equals(mode.fourcc())).max(java.util.Comparator.comparingDouble(VideoMode::fps))
                        .orElse(mode);
                sizes.add(new Choice<>(best, best.sizeLabel() + " (" + best.formatLabel() + ")"));
            }
        }
        sizes.sort(java.util.Comparator.comparingLong((Choice<VideoMode> c) -> -c.value().pixels())
                .thenComparing(c -> c.value().compressed() ? 0 : 1));
        VideoMode sizeOfCurrent = current == null ? null : sizes.stream().map(Choice::value)
                .filter(v -> v.width() == current.width() && v.height() == current.height()
                        && v.fourcc().equals(current.fourcc())).findFirst().orElse(null);
        left.add(row("settings.camera.size", combo("settings.camera.size", sizes, sizeOfCurrent, chosen -> {
            if (current == null || chosen.width() != current.width() || chosen.height() != current.height()
                    || !chosen.fourcc().equals(current.fourcc())) {
                presenter.chooseMode(chosen);
            }
        })));
        List<Choice<VideoMode>> rates = new ArrayList<>();
        if (current != null) {
            modes.stream().filter(o -> o.width() == current.width() && o.height() == current.height()
                            && o.fourcc().equals(current.fourcc()))
                    .sorted(java.util.Comparator.comparingDouble(VideoMode::fps).reversed())
                    .forEach(o -> rates.add(new Choice<>(o, o.fpsLabel())));
        }
        left.add(row("settings.camera.rate", combo("settings.camera.rate", rates, current, presenter::chooseMode)));

        left.add(section("settings.sound"));
        List<Choice<String>> mics = new ArrayList<>();
        mics.add(new Choice<>("default", msg.get("settings.sound.default")));
        for (Microphones.Microphone mic : m.microphones()) mics.add(new Choice<>(mic.name(), mic.description()));
        mics.add(new Choice<>(WebcamPreferences.NO_SOUND, msg.get("settings.sound.none")));
        String selectedMic = mics.stream().map(Choice::value).filter(v -> v.equals(m.microphone())).findFirst()
                .orElse("default");
        left.add(row("settings.sound.microphone", combo("settings.sound.microphone", mics, selectedMic,
                presenter::setMicrophone)));

        left.add(section("settings.saving"));
        List<Choice<PhotoFormat>> photo = List.of(new Choice<>(PhotoFormat.JPEG, "JPEG"),
                new Choice<>(PhotoFormat.PNG, "PNG"));
        left.add(row("settings.saving.photo-format", combo("settings.saving.photo-format", photo, m.photoFormat(),
                presenter::setPhotoFormat)));
        List<Choice<VideoFormat>> video = List.of(
                new Choice<>(VideoFormat.MP4, msg.get("settings.saving.mp4")),
                new Choice<>(VideoFormat.WEBM, msg.get("settings.saving.webm")));
        left.add(row("settings.saving.video-format", combo("settings.saving.video-format", video, m.videoFormat(),
                presenter::setVideoFormat)));
        left.add(row("settings.saving.photo-folder", folder("settings.saving.photo-folder", m.photoFolder(),
                presenter::setPhotoFolder)));
        left.add(row("settings.saving.video-folder", folder("settings.saving.video-folder", m.videoFolder(),
                presenter::setVideoFolder)));
        left.add(check("settings.saving.sound", m.shutterSound(), presenter::setShutterSound));
        left.add(check("settings.saving.flash", m.flash(), presenter::setFlash));
        left.add(check("settings.saving.mirrored", m.saveMirrored(), presenter::setSaveMirrored));
        left.add(Box.createVerticalGlue());
        left.revalidate();
        left.repaint();
    }

    // ---------------------------------------------------------------- right: the camera's controls

    private void buildRight(SettingsModel m) {
        right.removeAll();
        controlWidgets.clear();
        right.add(section("settings.picture"));
        Map<String, CameraControl> byName = new HashMap<>();
        for (CameraControl c : m.controls()) byName.put(c.name(), c);
        int shown = 0;
        for (String name : CameraControl.SHOWN) {
            CameraControl c = byName.get(name);
            if (c == null) continue;
            right.add(control(c));
            shown++;
        }
        if (shown == 0) {
            right.add(left(small(msg.get(m.camera().isEmpty() ? "settings.picture.no-camera"
                    : "settings.picture.none"))));
        } else {
            right.add(left(small(msg.get("settings.picture.hint"))));
            JPanel reset = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
            JButton defaults = Mnemonics.apply(new JButton(), msg.get("settings.picture.defaults"));
            defaults.setName("settings.picture.defaults");
            defaults.addActionListener(e -> presenter.cameraDefaults());
            reset.add(defaults);
            right.add(left(reset));
        }
        right.add(Box.createVerticalGlue());
        right.revalidate();
        right.repaint();
    }

    private JComponent control(CameraControl c) {
        String label = controlLabel(c.name());
        JComponent widget;
        switch (c.type()) {
            case BOOLEAN -> {
                JCheckBox box = new JCheckBox(label, c.value() != 0);
                box.setEnabled(!c.inactive());
                box.addActionListener(e -> {
                    if (!updating) presenter.setControl(c.name(), box.isSelected() ? 1 : 0);
                });
                widget = box;
                box.setName("control." + c.name());
                controlWidgets.put(c.name(), box);
                return left(box);
            }
            case MENU -> {
                List<Choice<Integer>> items = new ArrayList<>();
                c.menu().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(e -> items.add(new Choice<>(e.getKey(), menuLabel(c.name(), e.getKey(), e.getValue()))));
                JComboBox<Choice<Integer>> box = combo("control." + c.name(), items, c.value(),
                        v -> presenter.setControl(c.name(), v));
                box.setEnabled(!c.inactive());
                widget = box;
            }
            default -> {
                JSlider slider = new JSlider(c.min(), Math.max(c.min() + 1, c.max()), c.clamp(c.value()));
                slider.setName("control." + c.name());
                slider.setEnabled(!c.inactive());
                slider.setOpaque(false);
                slider.setPreferredSize(new Dimension(160, slider.getPreferredSize().height));
                slider.setToolTipText(c.min() + " … " + c.max() + ", " + msg.get("settings.picture.default") + " "
                        + c.defaultValue());
                slider.addChangeListener(e -> {
                    if (!updating) later(c.name(), () -> presenter.setControl(c.name(), c.clamp(slider.getValue())));
                });
                widget = slider;
            }
        }
        controlWidgets.put(c.name(), widget);
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JLabel l = new JLabel(label);
        l.setEnabled(!c.inactive());
        l.setPreferredSize(new Dimension(LABEL_WIDTH + 24, l.getPreferredSize().height));
        r.add(l, BorderLayout.WEST);
        r.add(widget, BorderLayout.CENTER);
        return left(r);
    }

    private void later(String name, Runnable action) {
        Timer t = pending.get(name);
        if (t != null) t.stop();
        t = new Timer(120, e -> action.run());
        t.setRepeats(false);
        pending.put(name, t);
        t.start();
    }

    /** "Brightness"; controls without a translation get their V4L2 name with spaces. */
    String controlLabel(String name) {
        String key = "control-name." + name;
        if (msg.has(key)) return msg.get(key);
        String s = name.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private String menuLabel(String control, int value, String fallback) {
        String key = "control-name." + control + "." + value;
        return msg.has(key) ? msg.get(key) : fallback;
    }

    // ---------------------------------------------------------------- small parts

    private static JPanel column() {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        return c;
    }

    private JComponent section(String key) {
        JLabel l = new JLabel(msg.get(key));
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, AmadeUI.theme().shadow())));
        l.setAlignmentX(0);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        return l;
    }

    private JComponent row(String key, JComponent field) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JLabel l = Mnemonics.apply(new JLabel(), msg.get(key));
        l.setLabelFor(field);
        l.setPreferredSize(new Dimension(LABEL_WIDTH, l.getPreferredSize().height));
        r.add(l, BorderLayout.WEST);
        r.add(field, BorderLayout.CENTER);
        return left(r);
    }

    private <T> JComboBox<Choice<T>> combo(String name, List<Choice<T>> items, T selected, Consumer<T> chosen) {
        JComboBox<Choice<T>> box = new JComboBox<>();
        box.setName(name);
        for (Choice<T> item : items) box.addItem(item);
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i).value(), selected)) box.setSelectedIndex(i);
        }
        box.setEnabled(!items.isEmpty());
        box.addActionListener(e -> {
            if (updating || box.getSelectedItem() == null) return;
            @SuppressWarnings("unchecked") Choice<T> c = (Choice<T>) box.getSelectedItem();
            chosen.accept(c.value());
        });
        box.setPrototypeDisplayValue(new Choice<>(null, "Integrated Camera (MJPEG)"));
        return box;
    }

    private JComponent folder(String name, Path folder, Consumer<Path> chosen) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        JTextField field = new JTextField(folder.toString(), 14);
        field.setName(name);
        field.setEditable(false);
        JButton pick = new JButton("…");
        pick.setName(name + ".choose");
        pick.setToolTipText(msg.get("settings.saving.choose"));
        pick.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(folder.toFile());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(msg.get("settings.saving.choose"));
            if (chooser.showDialog(this, msg.get("settings.saving.choose-ok")) == JFileChooser.APPROVE_OPTION) {
                chosen.accept(chooser.getSelectedFile().toPath());
            }
        });
        p.add(field, BorderLayout.CENTER);
        p.add(pick, BorderLayout.EAST);
        return p;
    }

    private JComponent check(String key, boolean on, Consumer<Boolean> changed) {
        JCheckBox box = Mnemonics.apply(new JCheckBox(), msg.get(key));
        box.setName(key);
        box.setSelected(on);
        box.addActionListener(e -> {
            if (!updating) changed.accept(box.isSelected());
        });
        return left(box);
    }

    private static JLabel small(String text) {
        JLabel l = new JLabel("<html><div style=\"width:240px\">" + text + "</div></html>");
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 2f));
        l.setForeground(AmadeUI.theme().textInactive());
        return l;
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // for tests
    JComponent controlWidget(String name) {
        return controlWidgets.get(name);
    }
}
