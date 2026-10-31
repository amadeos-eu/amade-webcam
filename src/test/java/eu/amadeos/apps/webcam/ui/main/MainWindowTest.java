package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.OnEdt;
import eu.amadeos.apps.webcam.domain.CaptureMode;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.apps.webcam.domain.QrContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window driven by the presenter with fake cameras. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private MainWindow window;
    private Fixture fixture;

    private void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        OnEdt.run(() -> {
            window = new MainWindow(new Fixture(dir).msg, null);
            fixture = new Fixture(dir, window);
            window.bind(fixture.presenter);
            window.setVisible(true);
            fixture.presenter.start();
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    private void key(int code) throws Exception {
        OnEdt.run(() -> {
            var root = window.getRootPane();
            Object name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0));
            root.getActionMap().get(name).actionPerformed(new ActionEvent(root, 0, ""));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return (T) c;
            if (c instanceof Container ct) {
                T found = find(ct, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void framesAppearInThePreview() throws Exception {
        open();
        OnEdt.run(() -> {
            assertFalse(window.preview().hasFrame());
            fixture.source.send(Fixture.frame());
        });
        OnEdt.run(() -> {
            assertTrue(window.preview().hasFrame());
            assertTrue(window.shutter().isEnabled());
            assertEquals(ShutterButton.Face.PHOTO, window.shutter().face());
            assertTrue(window.statusText().contains("1280 × 720"), window.statusText());
        });
    }

    @Test
    void noCameraPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        OnEdt.run(() -> {
            window = new MainWindow(new Fixture(dir).msg, null);
            fixture = new Fixture(dir, window);
            fixture.finder.cameras.clear();
            window.bind(fixture.presenter);
            fixture.presenter.start();
            assertEquals("No camera found", window.preview().heading());
            assertFalse(window.shutter().isEnabled());
            JMenuItem none = find(window.menu().cameraMenu().getPopupMenu(), "menu.camera.none");
            assertNotNull(none);
            assertFalse(none.isEnabled());
        });
    }

    @Test
    void cameraMenuListsTheCameras() throws Exception {
        open();
        OnEdt.run(() -> {
            JRadioButtonMenuItem a = find(window.menu().cameraMenu().getPopupMenu(), "camera.video0");
            JRadioButtonMenuItem b = find(window.menu().cameraMenu().getPopupMenu(), "camera.video2");
            assertEquals("1 Integrated Camera", a.getText());
            assertTrue(a.isSelected());
            assertFalse(b.isSelected());
            b.doClick();
            assertEquals(Fixture.CAM_B, fixture.source.camera);
        });
    }

    @Test
    void videoModeTurnsTheShutterIntoARecordButton() throws Exception {
        open();
        OnEdt.run(() -> {
            fixture.source.send(Fixture.frame());
            window.menu().item("menu.camera.mode-video").doClick();
        });
        OnEdt.run(() -> {
            assertEquals(ShutterButton.Face.RECORD, window.shutter().face());
            window.shutter().doClick();
            assertEquals(ShutterButton.Face.STOP, window.shutter().face());
            assertEquals("Stop Recording", window.menu().item("menu.camera.record").getText());
            assertFalse(window.menu().item("menu.camera.mode-photo").isEnabled());
        });
        fixture.ticker.advance(Duration.ofSeconds(1));
        OnEdt.run(() -> {
            assertTrue(window.getTitle().startsWith("Recording 00:0"), window.getTitle());
            window.shutter().doClick();
            assertEquals(ShutterButton.Face.RECORD, window.shutter().face());
            assertEquals("Webcam", window.getTitle());
        });
    }

    @Test
    void spaceTakesAPhoto() throws Exception {
        open();
        OnEdt.run(() -> fixture.source.send(Fixture.frame()));
        key(KeyEvent.VK_SPACE);
        OnEdt.run(() -> assertTrue(window.statusText().startsWith("Saved Photo"), window.statusText()));
    }

    @Test
    void countdownAndEscape() throws Exception {
        open();
        OnEdt.run(() -> {
            fixture.source.send(Fixture.frame());
            fixture.presenter.setTimer(eu.amadeos.apps.webcam.domain.PhotoTimer.THREE);
        });
        key(KeyEvent.VK_SPACE);
        key(KeyEvent.VK_ESCAPE);
        OnEdt.run(() -> assertEquals("Countdown cancelled", window.statusText()));
    }

    @Test
    void viewMenuSwitches() throws Exception {
        open();
        OnEdt.run(() -> {
            window.menu().item("menu.view.grid").doClick();
            assertTrue(fixture.preferences.grid());
            window.menu().item("menu.view.mirror").doClick();
            assertFalse(fixture.preferences.mirror());
            JRadioButtonMenuItem sepia = find(window.menu(), "look.sepia");
            if (sepia == null) {
                for (int i = 0; i < window.menu().getMenuCount(); i++) {
                    if (sepia == null) sepia = find(window.menu().getMenu(i).getPopupMenu(), "look.sepia");
                }
            }
            if (sepia == null) sepia = findDeep(window.menu());
            sepia.doClick();
            assertEquals(Look.SEPIA, fixture.preferences.look());
        });
    }

    private static JRadioButtonMenuItem findDeep(javax.swing.JMenuBar bar) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            for (Component c : bar.getMenu(i).getMenuComponents()) {
                if (c instanceof javax.swing.JMenu sub) {
                    for (Component d : sub.getMenuComponents()) {
                        if ("look.sepia".equals(d.getName())) return (JRadioButtonMenuItem) d;
                    }
                }
            }
        }
        return null;
    }

    @Test
    void qrBanner() throws Exception {
        open();
        OnEdt.run(() -> {
            window.showQr(Optional.of(QrContent.of("WIFI:S:Home;T:WPA;P:secret;;")));
            assertTrue(window.qrBanner().isVisible());
            assertEquals("Home, password: secret", window.qrBanner().text());
            window.showQr(Optional.empty());
            assertFalse(window.qrBanner().isVisible());
        });
    }

    @Test
    void settingsDialogBuildsTheCameraControls() throws Exception {
        open();
        OnEdt.run(() -> fixture.presenter.openSettings());
        OnEdt.run(() -> {
            var dialog = window.settingsDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isVisible());
            JSlider brightness = find(dialog.getContentPane(), "control.brightness");
            assertEquals(128, brightness.getValue());
            var auto = find(dialog.getContentPane(), "control.white_balance_automatic");
            assertNotNull(auto);
            javax.swing.JComboBox<?> video = find(dialog.getContentPane(), "settings.saving.video-format");
            assertEquals("MP4 (plays everywhere)", video.getSelectedItem().toString());
            javax.swing.JComboBox<?> size = find(dialog.getContentPane(), "settings.camera.size");
            assertEquals("1280 × 720 (MJPEG)", size.getSelectedItem().toString());
            assertEquals(3, size.getItemCount());
            dialog.dispose();
        });
        OnEdt.run(() -> assertNull(window.settingsDialog()));
    }

    @Test
    void recentStripWithItsMenu() throws Exception {
        open();
        OnEdt.run(() -> {
            BufferedImage t = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
            window.showRecent(List.of(new MainView.Recent(dir.resolve("Photo 1.jpg"), t, false),
                    new MainView.Recent(dir.resolve("Video 1.mp4"), t, true)));
            assertEquals(2, window.recentStrip().getComponentCount());
            JPopupMenu menu = window.recentStrip().menu(dir.resolve("Photo 1.jpg"));
            List<String> names = new ArrayList<>();
            for (Component c : menu.getComponents()) if (c.getName() != null) names.add(c.getName());
            assertEquals(List.of("recent.open", "recent.edit", "recent.folder", "recent.delete"), names);
        });
    }

    @Test
    void fullScreenAndBack() throws Exception {
        open();
        OnEdt.run(() -> fixture.presenter.toggleFullScreen());
        OnEdt.run(() -> {
            assertNotNull(window.fullScreenWindow());
            assertTrue(SwingUtilities.isDescendingFrom(window.preview(), window.fullScreenWindow()));
            fixture.presenter.escape();
            assertNull(window.fullScreenWindow());
            assertTrue(SwingUtilities.isDescendingFrom(window.preview(), window));
        });
    }

    @Test
    void clock() {
        assertEquals("00:42", MainWindow.clock(Duration.ofSeconds(42)));
        assertEquals("1:02:03", MainWindow.clock(Duration.ofSeconds(3723)));
    }

    @Test
    void previewFit() {
        assertEquals(new java.awt.Rectangle(0, 45, 800, 450), PreviewPanel.fit(1280, 720, 800, 540));
        assertEquals(new java.awt.Rectangle(80, 0, 640, 480), PreviewPanel.fit(640, 480, 800, 480));
    }

    @Test
    void modeIsSelected() throws Exception {
        open();
        OnEdt.run(() -> {
            fixture.presenter.setCaptureMode(CaptureMode.VIDEO);
            assertTrue(window.menu().item("menu.camera.mode-video").isSelected());
        });
    }
}
