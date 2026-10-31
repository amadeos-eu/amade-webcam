package eu.amadeos.apps.webcam.bootstrap;

import eu.amadeos.apps.webcam.ui.main.MainPresenter;
import eu.amadeos.apps.webcam.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public final class AmadeWebcamApplication {
    public static final AppInfo APP = new AppInfo("amade-webcam", "Webcam", "webcam");
    /**
     * Shows ffmpeg's test picture instead of a camera, for screenshots and computers without a camera;
     * {@code --test-picture=FILE} shows that image instead.
     */
    public static final String TEST_PICTURE = "--test-picture";

    private AmadeWebcamApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeWebcamApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-webcam " + version());
            return;
        }
        boolean testPicture = testPicture(args);
        Optional<Path> still = stillPicture(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start();
        }, new WebcamModule(testPicture, still));
    }
    // --8<-- [end:main]

    static boolean testPicture(String[] args) {
        return Arrays.stream(args).anyMatch(a -> a.equals(TEST_PICTURE) || a.startsWith(TEST_PICTURE + "="));
    }

    static Optional<Path> stillPicture(String[] args) {
        return Arrays.stream(args).filter(a -> a.startsWith(TEST_PICTURE + "=")).findFirst()
                .map(a -> Path.of(a.substring(TEST_PICTURE.length() + 1)));
    }
}
