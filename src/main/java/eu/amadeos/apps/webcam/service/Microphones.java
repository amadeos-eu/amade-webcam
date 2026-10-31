package eu.amadeos.apps.webcam.service;

import eu.amadeos.commons.json.Json;
import eu.amadeos.commons.process.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** The sound inputs of PipeWire (through its PulseAudio interface), for the sound of videos. */
public class Microphones {
    private static final Logger LOG = LoggerFactory.getLogger(Microphones.class);

    /** A sound input; {@code name} is what ffmpeg's {@code -f pulse -i} takes. */
    public record Microphone(String name, String description) {
    }

    /** The default input of the desktop, whatever it is at the moment. */
    public static final Microphone DEFAULT = new Microphone("default", "");

    private final CommandRunner runner;

    public Microphones(CommandRunner runner) {
        this.runner = runner;
    }

    /** The inputs without the "monitor" sources, which record what the speakers play. */
    public List<Microphone> list() {
        try {
            return parse(runner.output(List.of("pactl", "-f", "json", "list", "sources"), Duration.ofSeconds(4)));
        } catch (IOException | RuntimeException e) {
            LOG.debug("No sound inputs: {}", e.getMessage());
            return List.of();
        }
    }

    static List<Microphone> parse(String json) {
        List<Microphone> list = new ArrayList<>();
        for (Object source : Json.parseArray(json)) {
            String name = Json.str(source, "name");
            if (name == null || name.isBlank() || name.endsWith(".monitor")) continue;
            String description = Json.str(source, "description");
            list.add(new Microphone(name, description == null || description.isBlank() ? name : description));
        }
        return list;
    }
}
