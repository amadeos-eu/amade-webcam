package eu.amadeos.apps.webcam.ui.main;

import java.io.IOException;
import java.nio.file.Path;

/** Opens files and folders in other programs. */
public interface Opener {
    /** With the default program for the file type. */
    void open(Path file) throws IOException;

    /** True if the program is installed (on the PATH). */
    boolean has(String program);

    /** With a given program, such as amade-imageeditor. */
    void openWith(String program, Path file) throws IOException;

    /** A web link in the default browser. */
    void browse(String uri) throws IOException;
}
