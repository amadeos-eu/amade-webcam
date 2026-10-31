package eu.amadeos.apps.webcam.service;

import eu.amadeos.apps.webcam.ui.main.Opener;
import eu.amadeos.commons.desktop.DesktopLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opens files with the desktop's default programs ({@link DesktopLauncher}) or a named program. */
public class DesktopOpener implements Opener {
    private final DesktopLauncher launcher;

    public DesktopOpener(DesktopLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void open(Path file) throws IOException {
        launcher.open(file);
    }

    @Override
    public boolean has(String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) return true;
        }
        return false;
    }

    @Override
    public void openWith(String program, Path file) throws IOException {
        new ProcessBuilder(List.of(program, file.toString())).inheritIO().start();
    }

    @Override
    public void browse(String uri) throws IOException {
        launcher.browse(uri);
    }
}
