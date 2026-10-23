package tank.client.ui;

import tank.client.model.GameState;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Clock-controlled death ordering and peer recreation, without sleeping on the EDT. */
final class FinisherUiCheck {
    private static int checks;

    static int run() throws Exception {
        for (boolean p1 : new boolean[]{true, false}) {
            AtomicLong clock = new AtomicLong();
            AtomicInteger inputs = new AtomicInteger();
            GamePlayPanel game = new GamePlayPanel(p1 ? "alice" : "bob", inputs::incrementAndGet,
                    inputs::incrementAndGet, inputs::incrementAndGet, () -> { }, () -> { },
                    () -> { }, () -> { }, clock::get);
            JFrame frame = new JFrame("Finisher lifecycle");
            try {
                frame.setContentPane(game);
                frame.setSize(900, 540);
                frame.setVisible(true);
                game.setOpaque(true);
                game.setBackground(GameTheme.BACKGROUND);
                MatchResultOverlay result = find(game, MatchResultOverlay.class);
                game.start("death", "alice", "bob");
                game.setState(state(100, 520));
                key(game, "LEFT"); key(game, "RIGHT"); key(game, "SPACE");
                check(inputs.get() == 3, "live snapshot accepts input");

                // No HIT_EFFECT and no GAME_OVER: the HP0 snapshot alone must start death.
                game.setState(state(0, 520));
                key(game, "LEFT"); key(game, "RIGHT"); key(game, "SPACE");
                check(inputs.get() == 3, "HP0 locks all input before GAME_OVER");
                clock.set(120_000_000L);
                LobbyUiCheck.capture(game, "finisher-burst-p" + (p1 ? 1 : 2), 900, 540);
                clock.set(600_000_000L);
                game.setState(state(0, 600)); // Duplicate HP0 must retain original position/time.
                LobbyUiCheck.capture(game, "finisher-smoke-p" + (p1 ? 1 : 2), 1920, 1080);
                game.gameOver("alice", "HP0");
                check(!result.isVisible(), "early GAME_OVER waits for destruction");
                game.gameOver("bob", "duplicate");

                // Mimic PlayerFrame's fullscreen dispose/recreate cycle on the same panel.
                frame.dispose();
                frame.setUndecorated(true);
                frame.setSize(1920, 1080);
                frame.setVisible(true);
                clock.set(999_000_000L);
                game.refreshEffects();
                check(!result.isVisible(), "fullscreen does not shorten the one-second effect");
                clock.set(1_000_000_000L);
                game.refreshEffects();
                check(result.isVisible(), "duplicate HP0 and fullscreen do not restart the effect");
                check(result.getComponent(0).getAccessibleContext().getAccessibleName()
                                .startsWith(p1 ? "CHIẾN THẮNG: alice (BẠN)" : "THẤT BẠI: bob (BẠN)"),
                        "first GAME_OVER is still handled after HP0 locks input");

                // First snapshot can already be dead. A late GAME_OVER needs no extra delay.
                game.start("late", "alice", "bob");
                clock.set(2_000_000_000L);
                game.setState(state(0, 520));
                clock.set(3_100_000_000L);
                game.setState(state(0, 600));
                check(!result.isVisible(), "finished destruction still waits for GAME_OVER");
                BufferedImage wreck = LobbyUiCheck.capture(game, "finisher-wreck-p" + (p1 ? 1 : 2), 900, 540);
                assertWorldPixel(game, wreck, p1, 540, 30, new Color(64, 59, 52));
                game.gameOver("alice", "late result");
                check(result.isVisible(), "late GAME_OVER reveals immediately after death completes");

                // Leaving cancels pending results even if a peer is later shown again.
                game.start("leave", "alice", "bob");
                game.setState(state(0, 520));
                game.gameOver("alice", "pending");
                game.stopEffects();
                clock.addAndGet(2_000_000_000L);
                frame.setVisible(false);
                frame.setVisible(true);
                game.refreshEffects();
                game.gameOver("alice", "stale result");
                check(!result.isVisible(), "leaving clears pending result and disables old callbacks");
                game.start("replay", "alice", "bob");
                game.setState(state(100, 520));
                key(game, "SPACE");
                check(inputs.get() == 4 && !result.isVisible(), "replay clears deaths and restores input");
                game.gameOver("DRAW", "timeout");
                check(result.isVisible(), "timeout with positive HP has no death delay");

                // A peer can remain hidden beyond the deadline; reattach uses elapsed clock time.
                game.start("hidden", "alice", "bob");
                game.setState(state(0, 520));
                game.gameOver("alice", "hidden pending");
                frame.dispose();
                clock.addAndGet(1_100_000_000L);
                frame.setVisible(true);
                check(result.isVisible(), "reattach after deadline reveals without replaying destruction");

                game.start("both", "alice", "bob");
                long firstDeath = clock.get();
                game.setState(new GameState("both", 100, new GameState.Tank(280, 0, 40, 3),
                        new GameState.Tank(520, 100, 20, 1), List.of()));
                key(game, "SPACE");
                check(inputs.get() == 4, "player1 HP0 also locks input");
                clock.addAndGet(400_000_000L);
                game.setState(new GameState("both", 100, new GameState.Tank(280, 0, 40, 3),
                        new GameState.Tank(520, 0, 20, 1), List.of()));
                game.gameOver("DRAW", "both dead");
                clock.set(firstDeath + 1_000_000_000L);
                game.refreshEffects();
                check(!result.isVisible(), "result waits for both independently timed destructions");
                BufferedImage both = LobbyUiCheck.capture(game, "finisher-both-p" + (p1 ? 1 : 2), 900, 540);
                assertWorldPixel(game, both, p1, 300, 390, new Color(64, 59, 52));
                clock.set(firstDeath + 1_400_000_000L);
                game.refreshEffects();
                check(result.isVisible(), "second destruction finishes without restarting the first");
                game.stopEffects();
            } finally { frame.dispose(); }
        }
        return checks;
    }

    private static GameState state(int opponentHp, int opponentX) {
        return new GameState("death", 120, new GameState.Tank(280, 100, 40, 3),
                new GameState.Tank(opponentX, opponentHp, 20, 1), List.of());
    }

    private static void assertWorldPixel(GamePlayPanel game, BufferedImage image, boolean p1,
                                         int x, int y, Color expected) {
        Component arena = named(game, "arena");
        Point offset = SwingUtilities.convertPoint(arena, 0, 0, game);
        Point2D point = ArenaViewport.fit(arena.getWidth(), arena.getHeight()).transform(p1)
                .transform(new Point2D.Double(x, y), null);
        int actual = image.getRGB(offset.x + (int) Math.round(point.getX()),
                offset.y + (int) Math.round(point.getY())) & 0xffffff;
        check(actual == (expected.getRGB() & 0xffffff), "wreck remains at captured death position in either perspective");
    }

    private static void key(GamePlayPanel game, String stroke) {
        Object action = game.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke));
        game.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(game, 0, stroke));
    }

    private static Component named(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (name.equals(c.getName())) { return c; }
            if (c instanceof Container container) {
                Component found = named(container, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) { return type.cast(c); }
            if (c instanceof Container container) {
                T found = find(container, type);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
