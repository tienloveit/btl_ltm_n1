package tank.client.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Exercises decoded assets, PCM fades/loops and the output worker without speakers. */
public final class MusicCheck {
    private static int checks;

    public static int run() throws Exception {
        for (MusicScene scene : new MusicScene[]{MusicScene.LOBBY, MusicScene.BATTLE}) {
            check(MusicCheck.class.getResource(scene.resource) != null, "music is bundled on the classpath");
            try (AudioInputStream input = AudioSystem.getAudioInputStream(MusicCheck.class.getResource(scene.resource))) {
                check(MusicPlayer.FORMAT.matches(input.getFormat()) && !input.getFormat().isBigEndian(),
                        "music has portable little-endian mono PCM format");
                byte[] data = input.readAllBytes();
                int peak = 0;
                for (int i = 0; i < data.length; i += 2) { peak = Math.max(peak, Math.abs(sample(data, i / 2))); }
                check(data.length > 44100 * 20 && peak > 1000 && peak < 30000,
                        "music contains full-length audible, unclipped samples");
            }
        }

        MusicPlayer.Mixer mix = new MusicPlayer.Mixer(path -> new short[]{(short) (path.contains("lobby") ? 10000 : 20000)});
        mix.select(MusicScene.LOBBY);
        mix.setGain(MusicScene.LOBBY.gain);
        byte[] fadeIn = mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(fadeIn, 0) < 2 && sample(fadeIn, MusicPlayer.RAMP_FRAMES - 1) == 2000,
                "music fades from silence to 20 percent in 300 ms");
        mix.select(MusicScene.COUNTDOWN);
        mix.setGain(MusicScene.COUNTDOWN.gain);
        byte[] duck = mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(duck, 0) > 1900 && sample(duck, MusicPlayer.RAMP_FRAMES - 1) == 600,
                "countdown smoothly lowers the running music to six percent");
        mix.select(MusicScene.BATTLE);
        mix.setGain(MusicScene.BATTLE.gain);
        byte[] transition = mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(transition, 0) >= 599 && sample(transition, MusicPlayer.RAMP_FRAMES - 1) == 4000,
                "track changes crossfade without a silent gap");
        mix.select(MusicScene.RESULT);
        mix.setGain(MusicScene.RESULT.gain);
        mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(mix.render(1), 0) == 1200, "result ducks battle music for the announcer");
        mix.setGain(0);
        mix.render(MusicPlayer.RAMP_FRAMES);
        check(mix.isSilent() && sample(mix.render(1), 0) == 0, "mute completes its fade to silence");
        mix.select(MusicScene.LOBBY);
        mix.setGain(1);
        mix.render(64);
        mix.setGainImmediately(0);
        check(mix.isSilent() && sample(mix.render(1), 0) == 0,
                "zero volume cuts music immediately without the normal fade");

        mix.select(MusicScene.LOBBY);
        mix.setGain(MusicScene.LOBBY.gain * 50 / 20.0);
        mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(mix.render(1), 0) == 5000, "chosen music volume scales PCM amplitude");
        mix.select(MusicScene.COUNTDOWN);
        mix.setGain(MusicScene.COUNTDOWN.gain * 50 / 20.0);
        mix.render(MusicPlayer.RAMP_FRAMES);
        check(sample(mix.render(1), 0) == 1500, "countdown ducks to thirty percent of chosen volume");

        MusicPlayer.Mixer loop = new MusicPlayer.Mixer(path -> new short[]{100, 200, 300, 400});
        loop.select(MusicScene.LOBBY);
        loop.setGain(1);
        loop.render(MusicPlayer.RAMP_FRAMES);
        check(sample(loop.render(1), 0) == 300, "music cursor wraps across loop boundaries");
        loop.select(MusicScene.LOBBY);
        check(sample(loop.render(1), 0) == 400, "repeated scene snapshots do not restart playback");
        loop.select(MusicScene.COUNTDOWN);
        check(sample(loop.render(1), 0) == 100, "countdown keeps the lobby cursor");
        loop.select(MusicScene.BATTLE);
        loop.setGain(1);
        loop.render(MusicPlayer.RAMP_FRAMES + 1);
        loop.select(MusicScene.RESULT);
        check(sample(loop.render(1), 0) == 400, "results continue the battle cursor");
        loop.select(MusicScene.BATTLE);
        loop.setGain(1);
        loop.render(MusicPlayer.RAMP_FRAMES);
        check(sample(loop.render(1), 0) == 300, "replay starts a fresh battle cursor");

        Output output = new Output();
        MusicPlayer music = new MusicPlayer(MusicCheck.class::getResource, output::create);
        try {
            music.setScene(MusicScene.LOBBY);
            output.await("start");
            output.await("write");
            long mutedAt = System.nanoTime();
            music.setVolume(0);
            output.await("stop");
            check(System.nanoTime() - mutedAt < TimeUnit.MILLISECONDS.toNanos(250)
                            && output.starts.get() == 1,
                    "zero volume immediately stops existing music output");
            output.await("flush");
            output.await("close");
            check(output.closed.get() == 1, "zero releases the output device, not just its gain");
            music.setScene(MusicScene.COUNTDOWN);
            Thread.sleep(80);
            check(output.starts.get() == 1, "scene changes preserve zero volume");
            music.setVolume(50);
            output.await("start");
            output.await("write");
            music.setScene(MusicScene.BATTLE);
            output.await("write");
        } finally {
            music.close();
            check(music.awaitClosed(3, TimeUnit.SECONDS), "music worker terminates promptly");
        }
        check(output.created.get() == 2 && output.closed.get() == 2 && output.onMusicThread,
                "unmute opens a fresh output and every output closes off the UI thread");

        CountDownLatch mutedLoad = new CountDownLatch(1);
        Output mutedOutput = new Output();
        MusicPlayer initiallyMuted = new MusicPlayer(path -> {
            mutedLoad.countDown();
            return MusicCheck.class.getResource(path);
        }, mutedOutput::create);
        try {
            initiallyMuted.setVolume(0);
            initiallyMuted.setScene(MusicScene.LOBBY);
            check(mutedLoad.await(3, TimeUnit.SECONDS), "zero-volume startup handles its scene");
        } finally {
            initiallyMuted.close();
            check(initiallyMuted.awaitClosed(3, TimeUnit.SECONDS) && mutedOutput.created.get() == 0,
                    "starting muted never opens a music device");
        }

        CountDownLatch missing = new CountDownLatch(1);
        AtomicInteger lines = new AtomicInteger();
        MusicPlayer missingTrack = new MusicPlayer(path -> { missing.countDown(); return null; }, () -> {
            lines.incrementAndGet(); return output.create();
        });
        missingTrack.setScene(MusicScene.LOBBY);
        check(missing.await(3, TimeUnit.SECONDS), "missing music resource is exercised");
        missingTrack.close();
        check(missingTrack.awaitClosed(3, TimeUnit.SECONDS) && lines.get() == 0,
                "missing music does not open a device or leave a worker running");

        CountDownLatch noDevice = new CountDownLatch(1);
        MusicPlayer unavailable = new MusicPlayer(MusicCheck.class::getResource, () -> {
            noDevice.countDown(); throw new LineUnavailableException("simulated absent output");
        });
        unavailable.setScene(MusicScene.BATTLE);
        check(noDevice.await(3, TimeUnit.SECONDS), "absent music output is exercised");
        unavailable.close();
        check(unavailable.awaitClosed(3, TimeUnit.SECONDS), "absent output fails gracefully and releases its worker");
        return checks;
    }

    private static int sample(byte[] bytes, int frame) {
        return (short) ((bytes[frame * 2] & 255) | (bytes[frame * 2 + 1] << 8));
    }

    private static final class Output {
        final AtomicInteger created = new AtomicInteger(), starts = new AtomicInteger(), closed = new AtomicInteger();
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        volatile boolean onMusicThread = true;

        SourceDataLine create() {
            created.incrementAndGet();
            boolean[] running = {false};
            return (SourceDataLine) Proxy.newProxyInstance(MusicCheck.class.getClassLoader(),
                    new Class<?>[]{SourceDataLine.class}, (proxy, method, args) -> {
                        onMusicThread &= Thread.currentThread().getName().equals("game-music");
                        return switch (method.getName()) {
                            case "isRunning", "isActive" -> running[0];
                            case "start" -> { running[0] = true; starts.incrementAndGet(); events.add("start"); yield null; }
                            case "stop" -> { if (running[0]) { events.add("stop"); } running[0] = false; yield null; }
                            case "flush" -> { events.add("flush"); yield null; }
                            case "close" -> { closed.incrementAndGet(); events.add("close"); yield null; }
                            case "write" -> {
                                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                                events.add("write"); yield args[2];
                            }
                            default -> null;
                        };
                    });
        }

        void await(String expected) throws InterruptedException {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            String event;
            do {
                event = events.poll(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (event == null) { throw new AssertionError("Missing music event: " + expected); }
            } while (!event.equals(expected) && System.nanoTime() < end);
            check(event.equals(expected), "music command reaches output: " + expected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
