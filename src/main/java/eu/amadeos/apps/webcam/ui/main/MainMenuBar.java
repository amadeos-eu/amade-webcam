package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.domain.CameraDevice;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** File, Camera, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    private final MainPresenter presenter;
    private final Map<String, JMenuItem> items = new HashMap<>();
    private final Map<Look, JRadioButtonMenuItem> looks = new EnumMap<>(Look.class);
    private final JMenu cameraMenu;
    private final List<JMenuItem> cameraItems = new ArrayList<>();
    private int cameraListStart;

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.presenter = presenter;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item("menu.file.photos", key(KeyEvent.VK_O, CTRL), presenter::openPhotoFolder));
        file.add(item("menu.file.videos", null, presenter::openVideoFolder));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        cameraMenu = menu("menu.camera");
        cameraMenu.add(item("menu.camera.photo", null, presenter::takePhoto));
        cameraMenu.add(item("menu.camera.record", key(KeyEvent.VK_R, CTRL), presenter::toggleRecording));
        cameraMenu.addSeparator();
        ButtonGroup modes = new ButtonGroup();
        cameraMenu.add(radio("menu.camera.mode-photo", key(KeyEvent.VK_1, CTRL), modes,
                () -> presenter.setCaptureMode(CaptureMode.PHOTO)));
        cameraMenu.add(radio("menu.camera.mode-video", key(KeyEvent.VK_2, CTRL), modes,
                () -> presenter.setCaptureMode(CaptureMode.VIDEO)));
        cameraMenu.addSeparator();
        cameraListStart = cameraMenu.getMenuComponentCount();
        cameraMenu.addSeparator();
        cameraMenu.add(item("menu.camera.settings", key(KeyEvent.VK_COMMA, CTRL), presenter::openSettings));

        JMenu view = menu("menu.view");
        view.add(check("menu.view.mirror", key(KeyEvent.VK_M, CTRL), presenter::setMirror));
        view.add(check("menu.view.grid", key(KeyEvent.VK_G, CTRL), presenter::setGrid));
        view.add(check("menu.view.qr", key(KeyEvent.VK_K, CTRL), presenter::setQrCodes));
        JMenu lookMenu = Mnemonics.apply(new JMenu(), msg.get("menu.view.look"));
        lookMenu.setName("menu.view.look");
        ButtonGroup lookGroup = new ButtonGroup();
        for (Look look : Look.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem();
            Mnemonics.apply(item, msg.get(look.textKey()));
            item.setName(look.textKey());
            item.addActionListener(e -> presenter.setLook(look));
            lookGroup.add(item);
            looks.put(look, item);
            lookMenu.add(item);
        }
        view.add(lookMenu);
        view.addSeparator();
        view.add(item("menu.view.full-screen", key(KeyEvent.VK_F11, 0), presenter::toggleFullScreen));

        if (locales != null) {
            JMenu options = menu("menu.options");
            options.add(languageMenu(locales));
        }

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
    }

    /** The cameras between the two separators of the Camera menu. */
    void showCameras(List<CameraDevice> cameras, Optional<CameraDevice> current) {
        for (JMenuItem item : cameraItems) cameraMenu.remove(item);
        cameraItems.clear();
        if (cameras.isEmpty()) {
            JMenuItem none = new JMenuItem(msg.get("menu.camera.none"));
            none.setName("menu.camera.none");
            none.setEnabled(false);
            cameraItems.add(none);
        } else {
            ButtonGroup group = new ButtonGroup();
            int n = 1;
            for (CameraDevice camera : cameras) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(n + " " + camera.label());
                if (n <= 9) item.setMnemonic(KeyEvent.VK_0 + n);
                item.setName("camera." + camera.node().getFileName());
                item.setToolTipText(camera.node() + (camera.usbId().isEmpty() ? "" : "  USB " + camera.usbId()));
                item.setSelected(current.map(c -> c.node().equals(camera.node())).orElse(false));
                item.addActionListener(e -> presenter.chooseCamera(camera));
                group.add(item);
                cameraItems.add(item);
                n++;
            }
        }
        int at = cameraListStart;
        for (JMenuItem item : cameraItems) cameraMenu.insert(item, at++);
    }

    void showMode(CaptureMode mode, boolean recording, boolean camera) {
        items.get("menu.camera.mode-photo").setSelected(mode == CaptureMode.PHOTO);
        items.get("menu.camera.mode-video").setSelected(mode == CaptureMode.VIDEO);
        items.get("menu.camera.mode-photo").setEnabled(!recording);
        items.get("menu.camera.mode-video").setEnabled(!recording);
        items.get("menu.camera.photo").setEnabled(camera && mode == CaptureMode.PHOTO);
        items.get("menu.camera.record").setEnabled(camera && mode == CaptureMode.VIDEO);
        Mnemonics.apply(items.get("menu.camera.record"),
                msg.get(recording ? "menu.camera.stop" : "menu.camera.record"));
        for (JMenuItem item : cameraItems) item.setEnabled(!recording && !item.getName().equals("menu.camera.none"));
    }

    void showOptions(Options options) {
        items.get("menu.view.mirror").setSelected(options.mirror());
        items.get("menu.view.grid").setSelected(options.grid());
        items.get("menu.view.qr").setSelected(options.qrCodes());
        looks.get(options.look()).setSelected(true);
    }

    JMenuItem item(String name) {
        return items.get(name);
    }

    JMenu cameraMenu() {
        return cameraMenu;
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }

    private JRadioButtonMenuItem radio(String key, KeyStroke accelerator, ButtonGroup group, Runnable action) {
        JRadioButtonMenuItem item = Mnemonics.apply(new JRadioButtonMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        group.add(item);
        items.put(key, item);
        return item;
    }

    private JCheckBoxMenuItem check(String key, KeyStroke accelerator, java.util.function.Consumer<Boolean> action) {
        JCheckBoxMenuItem item = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.accept(item.isSelected()));
        items.put(key, item);
        return item;
    }
}
