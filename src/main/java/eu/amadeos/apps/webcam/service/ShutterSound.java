package eu.amadeos.apps.webcam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;

/**
 * The click of a photo, made from two short bursts of noise, so no sound file has to ship. Plays on a
 * background thread; a machine without sound output simply stays silent.
 */
public class ShutterSound {
    private static final Logger LOG = LoggerFactory.getLogger(ShutterSound.class);
    private static final float RATE = 44100f;

    private final byte[] samples = samples();

    public void play() {
        Thread.ofVirtual().name("shutter-sound").start(() -> {
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, samples.length);
                line.start();
                line.write(samples, 0, samples.length);
                line.drain();
            } catch (Exception | LinkageError e) {
                LOG.debug("No shutter sound: {}", e.toString());
            }
        });
    }

    /** 16-bit little-endian mono: a sharp click, a pause, a softer second click. */
    static byte[] samples() {
        int length = (int) (RATE * 0.16);
        byte[] out = new byte[length * 2];
        Random noise = new Random(7);
        for (int i = 0; i < length; i++) {
            double t = i / RATE;
            double a = t < 0.03 ? Math.exp(-t * 160) : t > 0.07 ? 0.6 * Math.exp(-(t - 0.07) * 120) : 0;
            int v = (int) (a * (noise.nextDouble() * 2 - 1) * 14000);
            out[2 * i] = (byte) v;
            out[2 * i + 1] = (byte) (v >> 8);
        }
        return out;
    }
}
