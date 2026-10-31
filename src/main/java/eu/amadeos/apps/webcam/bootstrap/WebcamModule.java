package eu.amadeos.apps.webcam.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.webcam.repository.WebcamPreferences;
import eu.amadeos.apps.webcam.service.CameraFinder;
import eu.amadeos.apps.webcam.service.DesktopOpener;
import eu.amadeos.apps.webcam.service.FfmpegCamera;
import eu.amadeos.apps.webcam.service.Microphones;
import eu.amadeos.apps.webcam.service.PhotoSaver;
import eu.amadeos.apps.webcam.service.QrReader;
import eu.amadeos.apps.webcam.service.RecentMedia;
import eu.amadeos.apps.webcam.service.Recorder;
import eu.amadeos.apps.webcam.service.ShutterSound;
import eu.amadeos.apps.webcam.service.TestPictureCameras;
import eu.amadeos.apps.webcam.ui.main.MainPresenter;
import eu.amadeos.apps.webcam.ui.main.MainWindow;
import eu.amadeos.apps.webcam.ui.main.Ticker;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * amade-webcam bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class WebcamModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.webcam.messages";
    /** The sub-folder of Pictures and Videos for what the camera takes. */
    public static final String FOLDER = "Camera";

    private final boolean testPicture;
    private final Optional<Path> stillPicture;

    public WebcamModule(boolean testPicture, Optional<Path> stillPicture) {
        this.testPicture = testPicture;
        this.stillPicture = stillPicture;
    }

    @Override
    protected void configure() {
        bind(WebcamPreferences.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(WebcamModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    MainWindow window(Messages msg, LocaleSettings locales) {
        return new MainWindow(msg, locales);
    }

    // --8<-- [start:presenter]
    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, WebcamPreferences preferences,
                            XdgDirectories xdg, DesktopLauncher launcher, ExecutorService background,
                            @Ui Executor ui) {
        CommandRunner runner = CommandRunner.system();
        CameraFinder finder = testPicture ? new TestPictureCameras() : new CameraFinder(runner);
        return new MainPresenter(window, msg, locales, new MainPresenter.Services(finder,
                new FfmpegCamera(runner, testPicture, stillPicture), new Recorder(runner), new PhotoSaver(), new RecentMedia(runner),
                new Microphones(runner), new QrReader(), new ShutterSound(), new DesktopOpener(launcher),
                preferences, xdg.pictures().resolve(FOLDER), xdg.videos().resolve(FOLDER), background, ui,
                Ticker.system(), LocalDateTime::now, testPicture, () -> System.exit(0)));
    }
    // --8<-- [end:presenter]
}
