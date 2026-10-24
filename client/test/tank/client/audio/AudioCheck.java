package tank.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the real decode/worker/pool with a mixer substitute, without requiring speakers. */
public final class AudioCheck {
    private static int checks;

    public static int run() throws Exception {
        for (SoundEffect effect : SoundEffect.values()) {
            check(AudioCheck.class.getResource(effect.resourcePath()) != null, "WAV is on runtime classpath");
            try (AudioInputStream input = AudioSystem.getAudioInputStream(AudioCheck.class.getResource(effect.resourcePath()))) {
                AudioFormat format = input.getFormat();
                byte[] pcm = input.readAllBytes();
                check(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                                && format.getChannels() == 1 && format.getSampleSizeInBits() == 16
                                && format.getSampleRate() == 44100, "portable PCM WAV format");
                int peak = 0;
                for (int i = 0; i < pcm.length; i += 2) {
                    int sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
                    peak = Math.max(peak, Math.abs(sample));
                }
                boolean resultVoice = effect == SoundEffect.VICTORY || effect == SoundEffect.DEFEAT;
                int maxBytes = resultVoice ? 441000 : 176400; // Result stingers may run up to five seconds.
                check(peak > 1000 && peak < 9000 && pcm.length > 7000 && pcm.length < maxBytes,
                        "effects and voice prompts are audible, bounded and conservatively normalized");
                if (resultVoice) {
                    check(pcm[0] == 0 && pcm[1] == 0 && pcm[pcm.length - 2] == 0 && pcm[pcm.length - 1] == 0,
                            "imported results fade to zero at each end to avoid clicks");
                }
            }
        }

        Mixer mixer = new Mixer();
        GameAudio audio = new GameAudio(AudioCheck.class::getResource, mixer::create);
        try {
            audio.play(SoundEffect.SHOT);
            mixer.await("start");
            audio.play(SoundEffect.SHOT);
            mixer.await("start");
            check(mixer.created.get() == 2, "overlapping shots use separate voices");
            audio.setEffectsVolume(0);
            mixer.await("stop"); mixer.await("stop");
            audio.play(SoundEffect.HIT);
            audio.setEffectsVolume(100);
            for (int i = 0; i < 6; i++) { audio.play(SoundEffect.SHOT); }
            for (int i = 0; i < 6; i++) { mixer.await("start"); }
        } finally {
            audio.close();
            check(audio.awaitClosed(3, TimeUnit.SECONDS), "audio worker terminates after close");
        }
        check(mixer.starts.get() == 8, "muted events are discarded, not played after unmute");
        check(mixer.created.get() == 6 && mixer.closed.get() == 6,
                "mute closes old voices and the replacement voice pool stays bounded");
        check(mixer.onAudioThread, "mixer operations stay off the UI thread");

        CountDownLatch opening = new CountDownLatch(1);
        GameAudio noDevice = new GameAudio(AudioCheck.class::getResource, () -> {
            opening.countDown();
            throw new LineUnavailableException("simulated missing audio device");
        });
        noDevice.play(SoundEffect.START);
        check(opening.await(3, TimeUnit.SECONDS), "missing audio device is exercised");
        noDevice.close();
        check(noDevice.awaitClosed(3, TimeUnit.SECONDS), "missing device does not prevent cleanup");

        Mixer missingMixer = new Mixer();
        GameAudio missingFile = new GameAudio(path -> path.equals(SoundEffect.SHOT.resourcePath())
                ? null : AudioCheck.class.getResource(path), missingMixer::create);
        missingFile.play(SoundEffect.SHOT);
        missingFile.play(SoundEffect.HIT);
        missingMixer.await("start");
        missingFile.close();
        check(missingFile.awaitClosed(3, TimeUnit.SECONDS) && missingMixer.starts.get() == 1,
                "one missing file does not disable the other effects");

        CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
        Mixer delayedMixer = new Mixer();
        GameAudio delayed = new GameAudio(path -> {
            loading.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return AudioCheck.class.getResource(path);
        }, delayedMixer::create);
        try {
            check(loading.await(3, TimeUnit.SECONDS), "resource loading can be delayed without blocking caller");
            delayed.play(SoundEffect.START);
            delayed.stopEffects();
            delayed.play(SoundEffect.SHOT);
            release.countDown();
            delayedMixer.await("start");
        } finally {
            release.countDown();
            delayed.close();
            check(delayed.awaitClosed(3, TimeUnit.SECONDS), "delayed worker closes");
        }
        check(delayedMixer.starts.get() == 1, "leaving/restarting cancels queued old effects");
        volumeChecks();
        return checks;
    }

    private static void volumeChecks() throws Exception {
        byte[] input = {0, 64, 0, (byte) 192};
        byte[] half = GameAudio.scalePcm(input, 50);
        check(half[1] == 32 && half[3] == (byte) 224 && input[1] == 64,
                "software gain scales signed PCM without changing the source");
        check(java.util.Arrays.equals(GameAudio.scalePcm(input, 0), new byte[4]), "zero gain is silent");
        for (boolean hardware : new boolean[]{false, true}) {
            Mixer mixer = new Mixer();
            mixer.hardware = hardware;
            GameAudio audio = new GameAudio(AudioCheck.class::getResource, mixer::create);
            audio.play(SoundEffect.SHOT);
            mixer.await("start");
            audio.setEffectsVolume(50);
            mixer.await(hardware ? "gain" : "start");
            audio.close();
            check(audio.awaitClosed(3, TimeUnit.SECONDS), "gain worker closes");
            check(hardware ? Math.abs(mixer.gain + 6.0206f) < .01f : mixer.restoredFrame == 123,
                    "live gain uses hardware dB or preserves cursor during software replacement");
            check(mixer.closed.get() == mixer.created.get(), "gain replacement releases every clip");
        }
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot()
                .node("tank-audio-test-" + java.util.UUID.randomUUID());
        try {
            Mixer mixer = new Mixer();
            GameAudio first = new GameAudio(AudioCheck.class::getResource, mixer::create, prefs);
            check(first.getMusicVolume() == 20 && first.getEffectsVolume() == 100, "new preferences use defaults");
            first.setMusicVolume(67);
            first.setEffectsVolume(32);
            first.saveSettings();
            first.close();
            check(first.awaitClosed(3, TimeUnit.SECONDS), "settings flush before shutdown completes");
            GameAudio second = new GameAudio(AudioCheck.class::getResource, mixer::create, prefs);
            check(second.getMusicVolume() == 67 && second.getEffectsVolume() == 32,
                    "a new session restores both persisted volumes");
            second.setMusicVolume(0);
            second.setEffectsVolume(0);
            second.saveSettings();
            second.close();
            check(second.awaitClosed(3, TimeUnit.SECONDS), "restored session closes");
            GameAudio muted = new GameAudio(AudioCheck.class::getResource, mixer::create, prefs);
            check(muted.getMusicVolume() == 0 && muted.getEffectsVolume() == 0,
                    "zero volumes persist without reverting to defaults");
            muted.play(SoundEffect.VICTORY);
            muted.close();
            check(muted.awaitClosed(3, TimeUnit.SECONDS) && mixer.created.get() == 0,
                    "restored zero effects never acquire an output clip");
        } finally { prefs.removeNode(); prefs.flush(); }
    }

    private static final class Mixer {
        final AtomicInteger created = new AtomicInteger(), starts = new AtomicInteger(), closed = new AtomicInteger();
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        volatile boolean onAudioThread = true;
        boolean hardware;
        volatile float gain;
        volatile int restoredFrame;
        final javax.sound.sampled.FloatControl control = new javax.sound.sampled.FloatControl(
                javax.sound.sampled.FloatControl.Type.MASTER_GAIN, -80, 6, .1f, 1, 0, "dB") {
            @Override public void setValue(float value) {
                super.setValue(value); gain = value; events.add("gain");
            }
        };

        Clip create() {
            created.incrementAndGet();
            boolean[] running = {false};
            return (Clip) Proxy.newProxyInstance(AudioCheck.class.getClassLoader(), new Class<?>[]{Clip.class},
                    (proxy, method, args) -> {
                        onAudioThread &= Thread.currentThread().getName().equals("game-audio");
                        return switch (method.getName()) {
                            case "isControlSupported" -> hardware;
                            case "getControl" -> control;
                            case "setFramePosition" -> { restoredFrame = (int) args[0]; yield null; }
                            case "getFramePosition" -> 123;
                            case "getFrameLength" -> 24000;
                            case "isRunning", "isActive" -> running[0];
                            case "start" -> { running[0] = true; starts.incrementAndGet(); events.add("start"); yield null; }
                            case "stop" -> { if (running[0]) { events.add("stop"); } running[0] = false; yield null; }
                            case "close" -> { closed.incrementAndGet(); yield null; }
                            default -> null;
                        };
                    });
        }

        void await(String expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            String event;
            do {
                event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (event == null) { throw new AssertionError("Missing mixer event: " + expected); }
            } while (!event.equals(expected) && System.nanoTime() < deadline);
            check(event.equals(expected), "audio command reaches mixer: " + expected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
