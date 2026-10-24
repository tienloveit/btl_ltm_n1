package tank.client.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.SourceDataLine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Optional hardware check: plays briefly, then verifies that zero releases both outputs. */
public final class AudioMutePreview {
    public static void main(String[] args) throws Exception {
        List<SourceDataLine> lines = new CopyOnWriteArrayList<>();
        List<Clip> clips = new CopyOnWriteArrayList<>();
        MusicPlayer music = new MusicPlayer(AudioMutePreview.class::getResource, () -> {
            SourceDataLine line = AudioSystem.getSourceDataLine(MusicPlayer.FORMAT);
            lines.add(line);
            return line;
        });
        GameAudio effects = new GameAudio(AudioMutePreview.class::getResource, () -> {
            Clip clip = AudioSystem.getClip();
            clips.add(clip);
            return clip;
        });
        try {
            music.setScene(MusicScene.LOBBY);
            effects.play(SoundEffect.VICTORY);
            await(() -> lines.stream().anyMatch(SourceDataLine::isRunning)
                    && clips.stream().anyMatch(Clip::isRunning), "initial audio starts");
            music.setVolume(0);
            effects.setEffectsVolume(0);
            await(() -> lines.stream().noneMatch(SourceDataLine::isRunning)
                    && clips.stream().noneMatch(Clip::isRunning), "zero stops playback");
            System.out.println("At zero: musicOpen=" + lines.stream().filter(SourceDataLine::isOpen).count()
                    + ", effectsOpen=" + clips.stream().filter(Clip::isOpen).count());
            await(() -> lines.stream().noneMatch(SourceDataLine::isOpen)
                    && clips.stream().noneMatch(Clip::isOpen), "zero closes all audio outputs");
            int lineCount = lines.size(), clipCount = clips.size();
            for (MusicScene scene : MusicScene.values()) { music.setScene(scene); }
            effects.play(SoundEffect.SHOT);
            Thread.sleep(100);
            if (lines.size() != lineCount || clips.size() != clipCount) {
                throw new AssertionError("Scene changes and effects reopened a muted output");
            }
            music.setVolume(50);
            music.setScene(MusicScene.BATTLE);
            effects.setEffectsVolume(50);
            effects.play(SoundEffect.DEFEAT);
            await(() -> lines.stream().anyMatch(SourceDataLine::isRunning)
                    && clips.stream().anyMatch(Clip::isRunning), "positive volume restores playback");
            for (int i = 0; i < 10; i++) {
                music.setVolume(0); music.setVolume(50); music.setVolume(0);
                effects.setEffectsVolume(0); effects.setEffectsVolume(50); effects.setEffectsVolume(0);
            }
            await(() -> lines.stream().noneMatch(SourceDataLine::isOpen)
                    && clips.stream().noneMatch(Clip::isOpen), "rapid slider changes end in silence");
            System.out.println("PASS: real-device mute, scene changes, resume and rapid slider changes.");
        } finally {
            music.close();
            effects.close();
            if (!music.awaitClosed(3, TimeUnit.SECONDS) || !effects.awaitClosed(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Audio workers did not close");
            }
        }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) { throw new AssertionError(message); }
            Thread.sleep(10);
        }
    }
}
