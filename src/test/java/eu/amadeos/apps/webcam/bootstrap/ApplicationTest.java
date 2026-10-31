package eu.amadeos.apps.webcam.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationTest {
    @Test
    void testPictureSwitch() {
        assertTrue(AmadeWebcamApplication.testPicture(new String[]{"--test-picture"}));
        assertFalse(AmadeWebcamApplication.testPicture(new String[]{}));
        assertTrue(AmadeWebcamApplication.testPicture(new String[]{"--test-picture=/tmp/a.png"}));
        assertEquals(java.util.Optional.of(java.nio.file.Path.of("/tmp/a.png")),
                AmadeWebcamApplication.stillPicture(new String[]{"--test-picture=/tmp/a.png"}));
        assertTrue(AmadeWebcamApplication.stillPicture(new String[]{"--test-picture"}).isEmpty());
    }

    @Test
    void versionWithoutAJar() {
        assertEquals("dev", AmadeWebcamApplication.version());
    }

    @Test
    void appInfo() {
        assertEquals("amade-webcam", AmadeWebcamApplication.APP.id());
        assertEquals("webcam", AmadeWebcamApplication.APP.configName());
    }
}
