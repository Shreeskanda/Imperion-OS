package javaos.services;

import javaos.core.JavaOS;
import javaos.util.OSLogger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Procedural Audio Synthesizer for IMPERION OS.
 * Synthesizes crisp cyberpunk UI sounds, clicks, notification chimes,
 * and security tones mathematically with zero external audio assets.
 */
public class SoundService {

    private final JavaOS os;
    private final ExecutorService audioPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ImperionAudioWorker");
        t.setDaemon(true);
        return t;
    });

    public SoundService(JavaOS os) {
        this.os = os;
    }

    public boolean isSoundEnabled() {
        if (os == null || os.getSettingsService() == null) return true;
        return Boolean.parseBoolean(os.getSettingsService().get("sound.enabled", "true"));
    }

    public int getVolume() {
        if (os == null || os.getSettingsService() == null) return 80;
        try {
            return Integer.parseInt(os.getSettingsService().get("sound.volume", "80"));
        } catch (Exception e) {
            return 80;
        }
    }

    public void playClick() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> synthesizeSweep(1200, 1800, 22, 0.4f));
    }

    public void playLaunch() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> {
            synthesizeTone(587.33, 40, 0.4f); // D5
            synthesizeTone(880.00, 60, 0.5f); // A5
        });
    }

    public void playSuccess() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> {
            synthesizeTone(523.25, 45, 0.4f); // C5
            synthesizeTone(659.25, 45, 0.45f); // E5
            synthesizeTone(783.99, 50, 0.5f); // G5
            synthesizeTone(1046.50, 100, 0.6f); // C6
        });
    }

    public void setVolume(int volume) {
        int v = Math.max(0, Math.min(100, volume));
        if (os != null && os.getSettingsService() != null) {
            os.getSettingsService().set("sound.volume", String.valueOf(v));
        }
    }

    public void setSoundEnabled(boolean enabled) {
        if (os != null && os.getSettingsService() != null) {
            os.getSettingsService().set("sound.enabled", String.valueOf(enabled));
        }
    }

    public void playLatch() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> {
            synthesizeSweep(900, 450, 25, 0.45f);
            synthesizeTone(600, 18, 0.35f);
        });
    }

    public void playWhoosh() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> synthesizeSweep(350, 850, 45, 0.4f));
    }

    public void playKeyClick() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> synthesizeSweep(2200, 1400, 8, 0.2f));
    }

    public void playWarning() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> {
            synthesizeTone(440.0, 70, 0.6f);
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            synthesizeTone(370.0, 90, 0.6f);
        });
    }

    public void playSlideSwitch() {
        if (!isSoundEnabled()) return;
        audioPool.submit(() -> synthesizeSweep(700, 1100, 35, 0.35f));
    }

    private void synthesizeTone(double freqHz, int durationMs, float gain) {
        try {
            float sampleRate = 44100f;
            int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
            byte[] buffer = new byte[numSamples];

            float masterVol = (getVolume() / 100.0f) * gain;
            for (int i = 0; i < numSamples; i++) {
                double t = i / sampleRate;
                // Exponential decay envelope for smooth sound
                double envelope = Math.exp(-3.5 * (t / (durationMs / 1000.0)));
                double sin = Math.sin(2.0 * Math.PI * freqHz * t);
                buffer[i] = (byte) (sin * 127.0 * masterVol * envelope);
            }

            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, buffer.length);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }

    private void synthesizeSweep(double startFreq, double endFreq, int durationMs, float gain) {
        try {
            float sampleRate = 44100f;
            int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
            byte[] buffer = new byte[numSamples];

            float masterVol = (getVolume() / 100.0f) * gain;
            for (int i = 0; i < numSamples; i++) {
                double frac = (double) i / numSamples;
                double curFreq = startFreq + (endFreq - startFreq) * frac;
                double t = i / sampleRate;
                double envelope = Math.sin(Math.PI * frac); // Smooth fade in and out
                double sin = Math.sin(2.0 * Math.PI * curFreq * t);
                buffer[i] = (byte) (sin * 127.0 * masterVol * envelope);
            }

            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, buffer.length);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }
}
