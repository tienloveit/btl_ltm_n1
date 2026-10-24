package tank.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.LineUnavailableException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Separate PCM output keeps music gain/fades independent of mixer gain controls and effects. */
final class MusicPlayer implements AutoCloseable {
    static final AudioFormat FORMAT = new AudioFormat(44100, 16, 1, true, false);
    static final int CHUNK_FRAMES = 882;
    static final int RAMP_FRAMES = 13230; // 300 ms
    @FunctionalInterface interface LineFactory { SourceDataLine create() throws LineUnavailableException; }

    private final Object monitor = new Object();
    private final Function<String, URL> resources;
    private final LineFactory lines;
    private final Map<String, short[]> cache = new HashMap<>();
    private final Set<String> unavailable = new HashSet<>();
    private volatile MusicScene scene = MusicScene.OFF;
    private volatile boolean closed, failed;
    private volatile int volume = 20;
    private volatile long revision;
    private boolean silenceRequested;
    private Thread thread;

    MusicPlayer(Function<String, URL> resources, LineFactory lines) {
        this.resources = resources;
        this.lines = lines;
    }

    void setScene(MusicScene value) {
        Objects.requireNonNull(value);
        synchronized (monitor) {
            if (closed || scene == value) { return; }
            scene = value;
            changed();
        }
    }

    void setVolume(int value) {
        value = Math.max(0, Math.min(100, value));
        synchronized (monitor) {
            if (closed || volume == value) { return; }
            volume = value;
            if (value == 0) { silenceRequested = true; }
            changed();
        }
    }

    private void changed() {
        revision++;
        if (thread == null && scene != MusicScene.OFF && !failed) {
            thread = new Thread(this::run, "game-music");
            thread.setDaemon(true);
            thread.start();
        }
        monitor.notifyAll();
    }

    private short[] load(String path) {
        if (cache.containsKey(path)) { return cache.get(path); }
        if (unavailable.contains(path)) { return new short[0]; }
        try {
            URL url = resources.apply(path);
            if (url == null) { throw new java.io.IOException("missing resource"); }
            try (AudioInputStream input = AudioSystem.getAudioInputStream(url)) {
                if (!FORMAT.matches(input.getFormat()) || input.getFormat().isBigEndian()) {
                    throw new java.io.IOException("expected little-endian mono PCM 44100 Hz, 16-bit");
                }
                byte[] bytes = input.readAllBytes();
                if (bytes.length < 2 || bytes.length % 2 != 0) { throw new java.io.IOException("empty PCM"); }
                short[] samples = new short[bytes.length / 2];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = (short) ((bytes[i * 2] & 255) | (bytes[i * 2 + 1] << 8));
                }
                cache.put(path, samples);
                return samples;
            }
        } catch (Exception exception) {
            unavailable.add(path);
            System.err.println("Music unavailable (" + path + "): " + exception.getClass().getSimpleName());
            return new short[0];
        }
    }

    private void run() {
        SourceDataLine line = null;
        Mixer mixer = new Mixer(this::load);
        try {
            while (!closed) {
                long observed;
                MusicScene requested;
                double target;
                boolean hardMute;
                synchronized (monitor) {
                    observed = revision;
                    requested = scene;
                    target = requested.gain * volume / 20.0;
                    hardMute = silenceRequested;
                    silenceRequested = false;
                }
                if (hardMute || target == 0) {
                    mixer.setGainImmediately(0);
                    if (line != null) {
                        line.stop(); line.flush(); line.close();
                        line = null;
                    }
                }
                mixer.select(requested);
                if (target == 0) { mixer.setGainImmediately(0); }
                else { mixer.setGain(target); }
                if (!mixer.hasAudio() || (target == 0 && mixer.isSilent())) {
                    if (line != null) {
                        line.stop(); line.flush(); line.close();
                        line = null;
                    }
                    synchronized (monitor) {
                        if (!closed && observed == revision) { monitor.wait(); }
                    }
                    continue;
                }
                if (closed) { break; }
                if (volume == 0 || revision != observed) { continue; }
                if (line == null) {
                    line = lines.create();
                    line.open(FORMAT, CHUNK_FRAMES * 2 * 4);
                }
                if (volume == 0 || revision != observed) { continue; }
                if (!line.isRunning()) { line.start(); }
                byte[] output = mixer.render(CHUNK_FRAMES);
                int offset = 0;
                while (offset < output.length && !closed && volume > 0 && revision == observed) {
                    int written = line.write(output, offset, output.length - offset);
                    if (written <= 0) { break; }
                    offset += written;
                }
            }
        } catch (Exception exception) {
            if (!closed) {
                failed = true;
                System.err.println("Music output unavailable: " + exception.getClass().getSimpleName());
            }
        } finally {
            if (line != null) { line.stop(); line.flush(); line.close(); }
            cache.clear();
        }
    }

    @Override public void close() {
        synchronized (monitor) { closed = true; monitor.notifyAll(); }
    }

    boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
        Thread running;
        synchronized (monitor) { running = thread; }
        if (running == null) { return true; }
        running.join(Math.max(1, unit.toMillis(timeout)));
        return !running.isAlive();
    }

    /** Sample renderer is testable without speakers; cursors wrap without a restart/gap. */
    static final class Mixer {
        private final Function<String, short[]> samples;
        private MusicScene selected = MusicScene.OFF;
        private Cursor current, previous;
        private double gain, target = -1, oldGain;
        private int gainFrames, oldFrames;

        Mixer(Function<String, short[]> samples) { this.samples = samples; }

        void select(MusicScene scene) {
            if (selected == scene) { return; }
            boolean restart = scene == MusicScene.BATTLE && selected == MusicScene.RESULT;
            if (!Objects.equals(selected.resource, scene.resource) || restart) {
                previous = current;
                oldGain = gain;
                oldFrames = previous == null ? 0 : RAMP_FRAMES;
                short[] data = scene.resource == null ? new short[0] : samples.apply(scene.resource);
                current = data.length == 0 ? null : new Cursor(data);
                gain = 0;
                target = -1;
            }
            selected = scene;
        }

        void setGain(double value) {
            if (target == value) { return; }
            target = value;
            gainFrames = RAMP_FRAMES;
        }

        void setGainImmediately(double value) {
            target = value;
            gain = value;
            gainFrames = 0;
            if (value == 0) {
                previous = null;
                oldGain = 0;
                oldFrames = 0;
            }
        }

        boolean hasAudio() { return current != null || previous != null; }
        boolean isSilent() { return gain < 0.000001 && previous == null; }

        byte[] render(int frames) {
            byte[] result = new byte[frames * 2];
            for (int i = 0; i < frames; i++) {
                if (gainFrames > 0) { gain += (target - gain) / gainFrames--; }
                double sample = current == null ? 0 : current.next() * gain;
                if (previous != null) {
                    sample += previous.next() * oldGain * oldFrames / RAMP_FRAMES;
                    if (--oldFrames == 0) { previous = null; }
                }
                int value = Math.max(-32768, Math.min(32767, (int) Math.round(sample)));
                result[i * 2] = (byte) value;
                result[i * 2 + 1] = (byte) (value >> 8);
            }
            return result;
        }

        private static final class Cursor {
            private final short[] data;
            private int frame;
            Cursor(short[] data) { this.data = data; }
            short next() {
                short value = data[frame];
                frame = (frame + 1) % data.length;
                return value;
            }
        }
    }
}
