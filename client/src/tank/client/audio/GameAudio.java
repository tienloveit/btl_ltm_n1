package tank.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.prefs.Preferences;

/** WAV decoding and mixer access run off UI/network threads, with independent music output. */
public final class GameAudio implements SoundPlayer {
    @FunctionalInterface
    interface ClipFactory { Clip create() throws LineUnavailableException; }

    private record Sample(AudioFormat format, byte[] bytes) { }
    private static final class Voice {
        Clip clip;
        final boolean hardwareGain;
        int volume;
        Voice(Clip clip, boolean hardwareGain, int volume) {
            this.clip = clip; this.hardwareGain = hardwareGain; this.volume = volume;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "game-audio");
        thread.setDaemon(true);
        return thread;
    });
    private final Function<String, URL> resources;
    private final ClipFactory clipFactory;
    private final MusicPlayer music;
    private final Preferences preferences;
    private final EnumMap<SoundEffect, Sample> samples = new EnumMap<>(SoundEffect.class);
    private final EnumMap<SoundEffect, List<Voice>> voices = new EnumMap<>(SoundEffect.class);
    private final EnumSet<SoundEffect> unavailable = EnumSet.noneOf(SoundEffect.class);
    private final List<Runnable> stateListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean volumeRefreshQueued = new AtomicBoolean();
    private final AtomicBoolean silenceRequested = new AtomicBoolean();
    private volatile int effectsVolume;
    private volatile int musicVolume;
    private boolean preferenceErrorReported;
    private volatile boolean closed;

    public GameAudio() { this(GameAudio.class::getResource, AudioSystem::getClip, systemPreferences()); }

    GameAudio(Function<String, URL> resources, ClipFactory clipFactory) {
        this(resources, clipFactory, null);
    }

    GameAudio(Function<String, URL> resources, ClipFactory clipFactory, Preferences preferences) {
        this.resources = resources;
        this.clipFactory = clipFactory;
        this.preferences = preferences;
        effectsVolume = readVolume(preferences, "effectsVolume", 100);
        musicVolume = readVolume(preferences, "musicVolume", 20);
        this.music = new MusicPlayer(resources, () -> AudioSystem.getSourceDataLine(MusicPlayer.FORMAT));
        music.setVolume(musicVolume);
        worker.execute(this::loadSamples);
    }

    private static Preferences systemPreferences() {
        try { return Preferences.userNodeForPackage(GameAudio.class); }
        catch (RuntimeException ignored) { return null; }
    }

    private static int readVolume(Preferences prefs, String key, int fallback) {
        try { return prefs == null ? fallback : Math.max(0, Math.min(100, prefs.getInt(key, fallback))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private void loadSamples() {
        for (SoundEffect effect : SoundEffect.values()) {
            if (closed) { return; }
            try {
                URL resource = resources.apply(effect.resourcePath());
                if (resource == null) { throw new java.io.IOException("missing resource"); }
                try (AudioInputStream input = AudioSystem.getAudioInputStream(resource)) {
                    samples.put(effect, new Sample(input.getFormat(), input.readAllBytes()));
                }
            } catch (Exception exception) {
                disable(effect, exception);
            }
        }
    }

    @Override public void play(SoundEffect effect) {
        if (effectsVolume == 0 || closed) { return; }
        long requestedGeneration = generation.get();
        enqueue(() -> {
            if (effectsVolume == 0 || closed || requestedGeneration != generation.get() || unavailable.contains(effect)) { return; }
            Sample sample = samples.get(effect);
            if (sample == null) { return; }
            List<Voice> pool = voices.computeIfAbsent(effect, ignored -> new ArrayList<>());
            Voice voice = null;
            try {
                for (Voice candidate : pool) {
                    if (!candidate.clip.isRunning()) { voice = candidate; break; }
                }
                if (voice == null && pool.size() < 4) {
                    voice = createVoice(sample, effectsVolume);
                    pool.add(voice);
                }
                if (voice == null) {
                    // Bound mixer resources even if a burst has more than four overlapping voices.
                    voice = pool.remove(0);
                    pool.add(voice);
                }
                if (effectsVolume == 0 || closed || requestedGeneration != generation.get()) { return; }
                voice.clip.stop();
                updateVoice(voice, sample, effectsVolume);
                voice.clip.setFramePosition(0);
                voice.clip.start();
            } catch (Exception exception) {
                if (voice != null && !pool.contains(voice)) { voice.clip.close(); }
                disable(effect, exception);
            }
        });
    }

    @Override public int getEffectsVolume() { return effectsVolume; }

    @Override public void setEffectsVolume(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        if (closed || effectsVolume == percent) { return; }
        effectsVolume = percent;
        if (percent == 0) { generation.incrementAndGet(); silenceRequested.set(true); }
        queueVolumeRefresh();
        for (Runnable listener : stateListeners) { listener.run(); }
    }

    @Override public void addStateListener(Runnable listener) {
        if (!closed) { stateListeners.add(listener); }
    }

    @Override public void removeStateListener(Runnable listener) { stateListeners.remove(listener); }

    @Override public void setMusicScene(MusicScene scene) {
        if (!closed) { music.setScene(scene); }
    }

    @Override public int getMusicVolume() { return musicVolume; }

    @Override public void setMusicVolume(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        if (closed || musicVolume == percent) { return; }
        musicVolume = percent;
        music.setVolume(percent);
        for (Runnable listener : stateListeners) { listener.run(); }
    }

    @Override public void saveSettings() {
        int effects = effectsVolume, background = musicVolume;
        enqueue(() -> persist(effects, background));
    }

    private void persist(int effects, int background) {
        if (preferences == null) { return; }
        try {
            preferences.putInt("effectsVolume", effects);
            preferences.putInt("musicVolume", background);
            preferences.flush();
        } catch (Exception exception) {
            if (!preferenceErrorReported) {
                preferenceErrorReported = true;
                System.err.println("Audio settings could not be saved: " + exception.getClass().getSimpleName());
            }
        }
    }

    private Voice createVoice(Sample sample, int percent) throws Exception {
        Clip clip = clipFactory.create();
        try {
            clip.open(sample.format(), sample.bytes(), 0, sample.bytes().length);
            Voice voice = new Voice(clip, clip.isControlSupported(FloatControl.Type.MASTER_GAIN), 100);
            updateVoice(voice, sample, percent);
            return voice;
        } catch (Exception exception) { clip.close(); throw exception; }
    }

    private void updateVoice(Voice voice, Sample sample, int percent) throws Exception {
        if (voice.volume == percent) { return; }
        if (voice.hardwareGain) {
            FloatControl control = (FloatControl) voice.clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = percent == 0 ? control.getMinimum() : (float) (20 * Math.log10(percent / 100.0));
            control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), db)));
        } else {
            int frame = voice.clip.getFramePosition();
            boolean running = voice.clip.isRunning();
            Clip replacement = clipFactory.create();
            try {
                byte[] scaled = scalePcm(sample.bytes(), percent);
                replacement.open(sample.format(), scaled, 0, scaled.length);
                replacement.setFramePosition(Math.min(frame, Math.max(0, replacement.getFrameLength() - 1)));
            } catch (Exception exception) { replacement.close(); throw exception; }
            voice.clip.stop();
            voice.clip.close();
            voice.clip = replacement;
            if (running && frame < replacement.getFrameLength()) { replacement.start(); }
        }
        voice.volume = percent;
    }

    static byte[] scalePcm(byte[] source, int percent) {
        if (percent == 100) { return source; }
        byte[] result = new byte[source.length];
        for (int i = 0; i < source.length; i += 2) {
            int value = (short) ((source[i] & 255) | (source[i + 1] << 8));
            int scaled = (int) Math.round(value * (percent / 100.0));
            result[i] = (byte) scaled;
            result[i + 1] = (byte) (scaled >> 8);
        }
        return result;
    }

    private void queueVolumeRefresh() {
        if (!volumeRefreshQueued.compareAndSet(false, true)) { return; }
        enqueue(() -> {
            int requested = effectsVolume;
            try {
                if (silenceRequested.getAndSet(false)) { closeClips(); }
                if (requested > 0) {
                    for (var entry : voices.entrySet()) {
                        for (Voice voice : entry.getValue()) {
                            if (!voice.clip.isRunning()) { continue; }
                            try { updateVoice(voice, samples.get(entry.getKey()), requested); }
                            catch (Exception exception) { disable(entry.getKey(), exception); }
                        }
                    }
                }
            } finally {
                volumeRefreshQueued.set(false);
                if (!closed && (effectsVolume != requested || silenceRequested.get())) { queueVolumeRefresh(); }
            }
        });
    }

    @Override public void stopEffects() {
        generation.incrementAndGet();
        enqueue(this::stopClips);
    }

    private void stopClips() {
        for (List<Voice> pool : voices.values()) {
            for (Voice voice : pool) { voice.clip.stop(); voice.clip.setFramePosition(0); }
        }
    }

    private void closeClips() {
        for (List<Voice> pool : voices.values()) {
            for (Voice voice : pool) { voice.clip.stop(); voice.clip.close(); }
        }
        voices.clear();
    }

    private void disable(SoundEffect effect, Exception exception) {
        if (unavailable.add(effect)) {
            System.err.println("Audio unavailable (" + effect + "): " + exception.getClass().getSimpleName());
        }
    }

    private void enqueue(Runnable task) {
        if (closed) { return; }
        try { worker.execute(task); }
        catch (RejectedExecutionException ignored) { /* Closing concurrently is harmless. */ }
    }

    @Override public synchronized void close() {
        if (closed) { return; }
        closed = true;
        music.close();
        generation.incrementAndGet();
        stateListeners.clear();
        worker.execute(() -> {
            closeClips();
            samples.clear();
            persist(effectsVolume, musicVolume);
        });
        worker.shutdown();
    }

    boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        return worker.awaitTermination(timeout, unit)
                && music.awaitClosed(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }
}
