package tank.client.audio;

/** Shared for one signed-in player's windows; implementations never block the Swing EDT. */
public interface SoundPlayer extends AutoCloseable {
    void play(SoundEffect effect);
    int getEffectsVolume();
    void setEffectsVolume(int percent);
    int getMusicVolume();
    void setMusicVolume(int percent);
    /** Persist the latest values off the UI thread after a slider is released. */
    void saveSettings();
    void setMusicScene(MusicScene scene);
    /** Cancel one-shot effects without interrupting background music. */
    void stopEffects();
    /** Observe changes to either volume. */
    void addStateListener(Runnable listener);
    void removeStateListener(Runnable listener);
    @Override void close();

    /** Existing UI previews and geometry checks do not need an audio device. */
    SoundPlayer SILENT = new SoundPlayer() {
        public void play(SoundEffect effect) { }
        public int getEffectsVolume() { return 0; }
        public void setEffectsVolume(int percent) { }
        public int getMusicVolume() { return 0; }
        public void setMusicVolume(int percent) { }
        public void saveSettings() { }
        public void setMusicScene(MusicScene scene) { }
        public void stopEffects() { }
        public void addStateListener(Runnable listener) { }
        public void removeStateListener(Runnable listener) { }
        public void close() { }
    };
}
