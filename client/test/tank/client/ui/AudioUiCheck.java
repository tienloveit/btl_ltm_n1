package tank.client.ui;

import tank.client.audio.SoundEffect;
import tank.client.audio.SoundPlayer;
import tank.client.audio.MusicScene;
import tank.client.controller.ClientController;
import tank.client.model.GameState;
import tank.client.model.MatchResultStats;
import tank.client.model.PlayerProfile;
import tank.client.model.RoomState;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class AudioUiCheck {
    private static int checks;

    static int run() throws Exception {
        RecordingAudio audio = new RecordingAudio();
        WaitingRoomPanel waiting = new WaitingRoomPanel("alice", ignored -> { }, () -> { }, audio);
        waiting.setRoom(new RoomState("audio-room", "alice", "bob", 10, true, true));
        for (int seconds : new int[]{10, 10, 9, 9, 8, 8, 7, 7, 6, 6, 5, 5,
                4, 4, 3, 3, 2, 2, 1, 1, 0, 0}) {
            waiting.setRoom(new RoomState("audio-room", "alice", "bob", seconds, true, true));
        }
        check(audio.events.equals(java.util.Collections.nCopies(10, SoundEffect.COUNTDOWN_TICK)),
                "countdown ticks from ten through one without spoken numbers");
        check(audio.events.size() == 10, "duplicate countdown snapshots and zero do not repeat sound");
        check(audio.scenes.equals(List.of(MusicScene.LOBBY, MusicScene.COUNTDOWN)),
                "waiting updates keep lobby music running and duck only for the final countdown");

        AtomicLong clock = new AtomicLong();
        GamePlayPanel game = new GamePlayPanel("alice", () -> { }, () -> { }, () -> { },
                () -> { }, () -> { }, () -> { }, () -> { }, clock::get, audio);
        game.start("audio-room", "alice", "bob");
        check(audio.count(SoundEffect.START) == 1, "match start emits one cue");
        check(audio.scene == MusicScene.BATTLE, "match start selects battle music");
        game.setState(state(2, 2, 100));
        Object shoot = game.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("SPACE"));
        game.getActionMap().get(shoot).actionPerformed(new java.awt.event.ActionEvent(game, 0, "shoot"));
        check(audio.count(SoundEffect.SHOT) == 0, "pressing Space and first snapshot do not predict a shot");
        game.setState(state(1, 2, 100));
        game.setState(state(1, 2, 100));
        game.setState(state(1, 1, 100));
        check(audio.count(SoundEffect.SHOT) == 2, "both players' confirmed ammo reductions emit shots once");
        game.setState(state(0, 1, 100));
        game.getActionMap().get(shoot).actionPerformed(new java.awt.event.ActionEvent(game, 0, "shoot"));
        game.setState(state(0, 1, 100));
        check(audio.count(SoundEffect.SHOT) == 3, "empty-ammo input emits no extra shot");
        game.showImpact(400, 30);
        check(audio.count(SoundEffect.HIT) == 1, "server hit effect emits impact sound");
        game.setState(state(0, 1, 0));
        game.setState(state(0, 1, 0));
        check(audio.count(SoundEffect.EXPLOSION) == 1, "repeated HP0 does not replay destruction sound");
        game.gameOver("alice", "HP0");
        check(audio.scene == MusicScene.RESULT, "ending the match lowers music before the result speech");
        check(audio.count(SoundEffect.VICTORY) == 0, "victory waits for destruction to finish");
        clock.set(1_000_000_000L);
        game.refreshEffects();
        game.gameOver("alice", "duplicate");
        game.gameOver("WAITING", "waiting");
        game.setMatchResult(new MatchResultStats(3, 10, 0, 4));
        game.refreshEffects();
        check(audio.count(SoundEffect.VICTORY) == 1, "result, waiting and late scores do not replay victory speech");
        int beforeReady = audio.stops;
        LobbyUiCheck.findButton(game, "CHƠI TIẾP").doClick();
        check(audio.stops == beforeReady + 1, "clicking replay immediately stops result audio");
        int stops = audio.stops;
        game.start("replay", "alice", "bob");
        check(audio.stops == stops + 1 && audio.count(SoundEffect.START) == 2,
                "replay stops old audio before the new start cue");
        game.gameOver("bob", "timeout");
        check(audio.count(SoundEffect.DEFEAT) == 1, "defeat speech follows the local player's perspective");
        game.start("draw", "bob", "alice");
        game.gameOver("DRAW", "timeout");
        check(audio.count(SoundEffect.DRAW) == 1, "draw has its own result cue");
        game.stopEffects();
        int events = audio.events.size();
        game.showImpact(400, 30);
        game.setState(state(0, 0, 0));
        game.gameOver("alice", "stale");
        check(audio.events.size() == events, "leaving ignores stale match audio");

        RecordingAudio session = new RecordingAudio();
        PlayerFrame frame = new PlayerFrame(new ClientController("127.0.0.1", 5000),
                new PlayerProfile("alice", 1, 3, 1, 0, 0, "ONLINE"), session);
        try {
            List<JButton> buttons = toggles(frame.getContentPane());
            check(buttons.size() == 4, "each screen has one settings button");
            frame.setVisible(true);
            JButton lobbySettings = buttons.stream().filter(Component::isShowing).findFirst().orElseThrow();
            lobbySettings.doClick();
            javax.swing.JDialog popup = (javax.swing.JDialog) lobbySettings.getClientProperty("audio-settings-window");
            javax.swing.JSlider music = slider(popup.getContentPane(), "music-volume");
            javax.swing.JSlider effects = slider(popup.getContentPane(), "effects-volume");
            check(music.getValue() == 20 && effects.getValue() == 100, "initial levels are music20 and effects100");
            music.setValue(65);
            effects.setValue(0);
            check(session.musicVolume == 65 && session.effectsVolume == 0, "sliders update channels independently");
            music.setValue(0);
            check(session.musicVolume == 0 && session.effectsVolume == 0,
                    "both zero sliders reach the same shared session audio state");
            music.setValue(65);
            check(session.listeners.size() == 1, "only open settings observes audio");
            LobbyUiCheck.capture((JComponent) popup.getContentPane(), "audio-settings", 340, 230);
            popup.getRootPane().getActionMap().get("close-settings").actionPerformed(new java.awt.event.ActionEvent(music, 0, "escape"));
            check(!popup.isVisible() && session.listeners.isEmpty() && session.saves > 0,
                    "Escape closes, saves, and detaches popup listener");
            frame.setSize(1920, 1000);
            frame.refreshResponsiveUi();
            lobbySettings.doClick();
            java.awt.Dimension popupSize = popup.getContentPane().getPreferredSize();
            java.awt.Point dialogLocation = popup.getLocationOnScreen();
            java.awt.Point ownerLocation = frame.getLocationOnScreen();
            check(Math.abs((dialogLocation.x + popup.getWidth() / 2)
                            - (ownerLocation.x + frame.getWidth() / 2)) <= 2
                            && Math.abs((dialogLocation.y + popup.getHeight() / 2)
                            - (ownerLocation.y + frame.getHeight() / 2)) <= 2,
                    "settings window is centered over its game window");
            LobbyUiCheck.capture((JComponent) popup.getContentPane(), "audio-settings-projection", popupSize.width, popupSize.height);
            check(popupSize.width > 340, "settings grows with projection UI");
            frame.showRoom(new RoomState("audio-room", "alice", "bob", 3));
            check(!popup.isVisible() && session.listeners.isEmpty(), "changing screen closes settings and removes listener");
            check(session.scene == MusicScene.COUNTDOWN, "room countdown ducks lobby music");
            frame.showGame("audio-room", "alice", "bob");
            GamePlayPanel sessionGame = LobbyUiCheck.find(frame.getContentPane(), GamePlayPanel.class);
            JButton gameSettings = toggles(sessionGame).stream().filter(Component::isShowing).findFirst().orElseThrow();
            java.util.concurrent.atomic.AtomicInteger inputs = new java.util.concurrent.atomic.AtomicInteger();
            AtomicLong inputClock = new AtomicLong();
            GamePlayPanel inputGame = new GamePlayPanel("alice", inputs::incrementAndGet, inputs::incrementAndGet,
                    inputs::incrementAndGet, () -> { }, () -> { }, () -> { }, () -> { }, inputClock::get, session);
            javax.swing.JFrame inputWindow = new javax.swing.JFrame();
            inputWindow.setContentPane(inputGame);
            inputWindow.setSize(900, 540);
            inputWindow.setVisible(true);
            inputGame.start("input-room", "alice", "bob");
            for (String action : List.of("move-left", "shoot", "move-right", "move-left", "move-right", "shoot")) {
                inputGame.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(inputGame, 0, action));
            }
            check(inputs.get() == 5, "movement remains available during fire and alternate keys are accepted");
            inputClock.addAndGet(350_000_000L);
            inputGame.getActionMap().get("shoot").actionPerformed(new java.awt.event.ActionEvent(inputGame, 0, "shoot"));
            check(inputs.get() == 6, "holding fire respects the server cadence without queueing excess shots");
            inputs.set(0);
            JButton inputSettings = toggles(inputGame).stream().filter(Component::isShowing).findFirst().orElseThrow();
            inputSettings.doClick();
            javax.swing.JDialog inputPopup = (javax.swing.JDialog) inputSettings.getClientProperty("audio-settings-window");
            for (String action : List.of("move-left", "move-right", "shoot")) {
                inputGame.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(inputGame, 0, action));
            }
            check(inputs.get() == 0, "settings blocks gameplay keyboard actions");
            inputPopup.getRootPane().getActionMap().get("close-settings")
                    .actionPerformed(new java.awt.event.ActionEvent(inputPopup, 0, "escape"));
            inputClock.addAndGet(350_000_000L);
            inputGame.getActionMap().get("shoot").actionPerformed(new java.awt.event.ActionEvent(inputGame, 0, "shoot"));
            check(inputs.get() == 1, "closing settings restores gameplay input");
            inputSettings.doClick();
            inputWindow.dispose();
            check(!inputPopup.isVisible() && session.listeners.isEmpty(), "disposing screen closes popup and detaches listener");
            gameSettings.doClick();
            javax.swing.JDialog gamePopup = (javax.swing.JDialog) gameSettings.getClientProperty("audio-settings-window");
            check(slider(gamePopup.getContentPane(), "music-volume").getValue() == 65
                            && slider(gamePopup.getContentPane(), "effects-volume").getValue() == 0,
                    "another screen reflects shared levels");
            for (java.awt.event.WindowFocusListener listener : gamePopup.getWindowFocusListeners()) {
                listener.windowLostFocus(new java.awt.event.WindowEvent(gamePopup,
                        java.awt.event.WindowEvent.WINDOW_LOST_FOCUS, frame));
            }
            check(!gamePopup.isVisible() && session.listeners.isEmpty(),
                    "losing focus to the game closes and detaches settings");
            int sceneChanges = session.scenes.size();
            LobbyUiCheck.findButton(sessionGame, "To\u00e0n m\u00e0n h\u00ecnh").doClick();
            check(!session.closed && session.effectsVolume == 0, "fullscreen preserves audio settings and lifetime");
            LobbyUiCheck.findButton(sessionGame, "V\u1ec1 c\u1eeda s\u1ed5").doClick();
            check(session.scene == MusicScene.BATTLE && session.scenes.size() == sceneChanges,
                    "fullscreen preserves music playback");
            frame.showGameOver("audio-room", "alice", "timeout");
            check(session.scene == MusicScene.RESULT && session.musicVolume == 65,
                    "result keeps selected music level");
            frame.closeRoom("audio-room", "left");
            check(!session.closed && session.scene == MusicScene.LOBBY,
                    "leaving a room restores lobby music and keeps session audio usable");
        } finally { frame.dispose(); }
        check(session.closed, "logout/dispose releases session audio");
        return checks;
    }

    private static GameState state(int ammo1, int ammo2, int hp2) {
        return new GameState("audio-room", 100, new GameState.Tank(400, 100, ammo1, 0),
                new GameState.Tank(400, hp2, ammo2, 0), List.of());
    }

    private static javax.swing.JSlider slider(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof javax.swing.JSlider slider && name.equals(slider.getName())) { return slider; }
            if (child instanceof Container nested) {
                javax.swing.JSlider found = slider(nested, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static List<JButton> toggles(Container root) {
        return toggles(root, "audio-settings");
    }

    private static List<JButton> toggles(Container root, String name) {
        List<JButton> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) { result.add(button); }
            if (child instanceof Container nested) { result.addAll(toggles(nested, name)); }
        }
        return result;
    }

    private static final class RecordingAudio implements SoundPlayer {
        final List<SoundEffect> events = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();
        final List<MusicScene> scenes = new ArrayList<>();
        MusicScene scene = MusicScene.OFF;
        boolean closed;
        int stops, saves, musicVolume = 20, effectsVolume = 100;
        public void play(SoundEffect effect) { if (effectsVolume > 0 && !closed) { events.add(effect); } }
        public int getMusicVolume() { return musicVolume; }
        public int getEffectsVolume() { return effectsVolume; }
        public void setMusicVolume(int value) { musicVolume = value; List.copyOf(listeners).forEach(Runnable::run); }
        public void setEffectsVolume(int value) { effectsVolume = value; List.copyOf(listeners).forEach(Runnable::run); }
        public void saveSettings() { saves++; }
        public void setMusicScene(MusicScene value) {
            if (scene != value) { scene = value; scenes.add(value); }
        }
        public void removeStateListener(Runnable listener) { listeners.remove(listener); }
        public void stopEffects() { stops++; }
        public void addStateListener(Runnable listener) { listeners.add(listener); }
        public void close() { closed = true; listeners.clear(); }
        int count(SoundEffect effect) { return (int) events.stream().filter(event -> event == effect).count(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
