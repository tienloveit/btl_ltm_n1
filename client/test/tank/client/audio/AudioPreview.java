package tank.client.audio;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional audible smoke check; run after run-ui-checks.bat with speakers/headphones enabled. */
public final class AudioPreview {
    public static void main(String[] args) throws Exception {
        SoundEffect[] effects = args.length == 0 ? SoundEffect.values()
                : java.util.Arrays.stream(args).map(SoundEffect::valueOf).toArray(SoundEffect[]::new);
        AtomicInteger starts = new AtomicInteger();
        GameAudio audio = new GameAudio(AudioPreview.class::getResource, () -> {
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.START) { starts.incrementAndGet(); }
            });
            return clip;
        });
        try {
            for (SoundEffect effect : effects) {
                System.out.println("Playing " + effect);
                audio.play(effect);
                try (AudioInputStream input = AudioSystem.getAudioInputStream(
                        AudioPreview.class.getResource(effect.resourcePath()))) {
                    Thread.sleep((long) Math.ceil(input.getFrameLength() * 1000.0 / input.getFormat().getFrameRate()) + 150);
                }
            }
        } finally {
            audio.close();
            if (!audio.awaitClosed(3, TimeUnit.SECONDS)) { throw new AssertionError("Audio worker did not close"); }
        }
        if (starts.get() != effects.length) {
            throw new AssertionError("Audio device started " + starts.get() + "/" + effects.length
                    + " effects; see audio errors above");
        }
        System.out.println("PASS: " + starts.get() + " effects started on the audio device; audio resources closed.");
    }
}
