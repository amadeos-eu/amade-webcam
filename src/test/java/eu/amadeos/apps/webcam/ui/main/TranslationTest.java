package eu.amadeos.apps.webcam.ui.main;

import eu.amadeos.apps.webcam.bootstrap.WebcamModule;
import eu.amadeos.apps.webcam.domain.Look;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(WebcamModule.class, WebcamModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(WebcamModule.class, WebcamModule.MESSAGES));
    }

    /** No two entries share a mnemonic letter in the menu bar or in one menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = Map.of(
                "bar", List.of("menu.file", "menu.camera", "menu.view", "menu.options", "menu.help"),
                "file", List.of("menu.file.photos", "menu.file.videos", "menu.file.exit"),
                "camera", List.of("menu.camera.photo", "menu.camera.record", "menu.camera.mode-photo",
                        "menu.camera.mode-video", "menu.camera.settings"),
                "camera-stop", List.of("menu.camera.photo", "menu.camera.stop", "menu.camera.mode-photo",
                        "menu.camera.mode-video", "menu.camera.settings"),
                "view", List.of("menu.view.mirror", "menu.view.grid", "menu.view.qr", "menu.view.look",
                        "menu.view.full-screen"),
                "look", Arrays.stream(Look.values()).map(Look::textKey).toList(),
                "options", List.of("menu.options.language"),
                "help", List.of("menu.help.about"),
                "recent", List.of("recent.open", "recent.edit", "recent.folder", "recent.delete"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertNull(before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void germanNames() {
        assertEquals("Kameraeinstellungen", texts(Language.GERMAN).get("settings.title"));
    }
}
