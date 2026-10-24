package tank.client.ui;

import tank.client.model.GameState;
import tank.client.model.MatchResultStats;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Deterministic animation frames and real Swing lifecycle/interaction checks, all on the EDT. */
final class MatchResultUiCheck {
    private static int checks;

    static int verifyLiveAnimation() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<JFrame> window = new AtomicReference<>();
        AtomicReference<Timer> sampler = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Live result animation check");
            window.set(frame);
            MatchResultOverlay overlay = new MatchResultOverlay(() -> { }, () -> { }, () -> { });
            frame.setContentPane(overlay);
            frame.setSize(900, 540);
            frame.setVisible(true);
            overlay.showResult("doanngocquang", "doanngocquang", "player_a", "doanngocquang",
                    "Đối thủ đã hết HP.", new MatchResultStats(3, 13, 0, 4));
            int initialY = overlay.getComponent(0).getY();
            boolean[] moved = {false};
            Timer sample = new Timer(50, event -> {
                try {
                    if (overlay.getComponent(0).getY() < initialY) { moved[0] = true; }
                    if (overlay.elapsedSeconds() >= 3.15) {
                        if (!moved[0] || overlay.isAnimating() || !overlay.isShowing()) {
                            throw new AssertionError("live timer must move cards, stop after 3s and keep result visible");
                        }
                        ((Timer) event.getSource()).stop();
                        frame.dispose();
                        done.countDown();
                    }
                } catch (Throwable error) {
                    failure.set(error);
                    ((Timer) event.getSource()).stop();
                    frame.dispose();
                    done.countDown();
                }
            });
            sampler.set(sample);
            sample.start();
        });
        boolean completed = done.await(8, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {
            sampler.get().stop();
            window.get().dispose();
        });
        if (!completed) { throw new AssertionError("live animation timed out"); }
        if (failure.get() != null) { throw new AssertionError("live animation failed", failure.get()); }
        return 3;
    }

    static int verifyLethalImpact() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<JFrame> window = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                AtomicInteger inputs = new AtomicInteger();
                GamePlayPanel game = new GamePlayPanel("doanngocquang", inputs::incrementAndGet,
                        inputs::incrementAndGet, inputs::incrementAndGet, () -> { }, () -> { },
                        () -> { }, () -> { });
                JFrame frame = new JFrame("Lethal impact check");
                window.set(frame);
                frame.setContentPane(game);
                frame.setSize(900, 540);
                frame.setVisible(true);
                game.start("preview", "doanngocquang", "player_a");
                game.setState(lethalState());
                game.showImpact(400, 360);
                game.showImpact(280, 40);
                game.gameOver("doanngocquang", "Đối thủ đã hết HP.");
                MatchResultOverlay overlay = find(game, MatchResultOverlay.class);
                check(!overlay.isVisible(), "lethal impact remains visible before result");
                key(game, "SPACE");
                check(inputs.get() == 0, "lethal result blocks input immediately");
                Timer reveal = new Timer(1120, event -> {
                    ((Timer) event.getSource()).stop();
                    try {
                        check(overlay.isVisible(), "result appears after lethal impact");
                        game.start("preview", "doanngocquang", "player_a");
                        game.setState(lethalState());
                        game.showImpact(280, 40);
                        game.gameOver("doanngocquang", "Đối thủ đã hết HP.");
                        game.start("next", "doanngocquang", "player_a");
                        Timer cancel = new Timer(1120, second -> {
                            ((Timer) second.getSource()).stop();
                            try {
                                check(!overlay.isVisible(), "new match cancels pending lethal result");
                            } catch (Throwable error) { failure.set(error); }
                            frame.dispose();
                            done.countDown();
                        });
                        cancel.setRepeats(false);
                        cancel.start();
                    } catch (Throwable error) {
                        failure.set(error);
                        frame.dispose();
                        done.countDown();
                    }
                });
                reveal.setRepeats(false);
                reveal.start();
            } catch (Throwable error) { failure.set(error); done.countDown(); }
        });
        boolean completed = done.await(4, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> { if (window.get() != null) { window.get().dispose(); } });
        if (!completed) { throw new AssertionError("lethal impact check timed out"); }
        if (failure.get() != null) { throw new AssertionError("lethal impact check failed", failure.get()); }
        return 4;
    }

    static int run() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger inputs = new AtomicInteger(), replays = new AtomicInteger(), exits = new AtomicInteger();
        AtomicInteger toggles = new AtomicInteger(), escapes = new AtomicInteger();
        GamePlayPanel game = new GamePlayPanel("doanngocquang", inputs::incrementAndGet,
                inputs::incrementAndGet, inputs::incrementAndGet, replays::incrementAndGet,
                exits::incrementAndGet, toggles::incrementAndGet, escapes::incrementAndGet, clock::get);
        JFrame frame = new JFrame("Result check");
        try {
            frame.setContentPane(game);
            frame.setSize(680, 395);
            frame.setVisible(true);
            game.start("preview", "doanngocquang", "player_a");
            game.setState(state());
            game.setMatchResult(new MatchResultStats(3, 13, 0, 4));
            key(game, "LEFT"); key(game, "RIGHT"); key(game, "SPACE");
            check(inputs.get() == 3, "game input active before result");
            MatchResultOverlay overlay = find(game, MatchResultOverlay.class);
            game.gameOver("doanngocquang", "Đối thủ đã hết HP.");
            check(overlay.isShowing() && overlay.isAnimating(), "result starts visible animation");
            check(card(overlay, "CHIẾN THẮNG").contains("doanngocquang (BẠN), Điểm cộng trận này: +3, Tổng điểm backend: 13"),
                    "winner shows server-awarded match points and backend total");
            check(card(overlay, "THẤT BẠI").contains("player_a, Điểm cộng trận này: +0, Tổng điểm backend: 4"),
                    "loser shows zero match points and own backend total");
            clock.set(300_000_000L);
            overlay.refreshAnimation();
            capture(game, "game-over-entering", 680, 365);
            Rectangle entering = overlay.getComponent(0).getBounds();
            check(entering.y > 54, "card is still rising at 300ms");
            clock.set(1_150_000_000L);
            overlay.refreshAnimation();
            capture(game, "game-over-confetti", 900, 540);
            clock.set(3_100_000_000L);
            overlay.refreshAnimation();
            check(!overlay.isAnimating() && overlay.isVisible(), "animation settles without dismissing result");
            capture(game, "game-over", 680, 365);
            capture(game, "game-over-minimum", 639, 326);
            assertFits(overlay);
            capture(game, "game-over-fullscreen", 1920, 1080);
            assertFits(overlay);
            capture(game, "game-over-4k", 3840, 2160);
            assertFits(overlay);
            key(game, "LEFT"); key(game, "RIGHT"); key(game, "SPACE");
            check(inputs.get() == 3, "movement and shooting blocked after result");
            game.gameOver("WAITING", "Đối thủ muốn chơi tiếp. Bạn có muốn không?");
            check(overlay.elapsedSeconds() == 3.1 && !overlay.isAnimating(), "WAITING does not replay animation");
            game.gameOver("player_a", "duplicate result");
            check(card(overlay, "CHIẾN THẮNG").contains("doanngocquang"), "duplicate GAME_OVER keeps result");
            button(overlay, "CHƠI TIẾP").doClick();
            JButton waiting = button(overlay, "ĐANG CHỜ ĐỐI THỦ…");
            waiting.doClick();
            check(replays.get() == 1 && !waiting.isEnabled(), "replay sent once and button disabled");
            capture(game, "game-over-waiting", 639, 326);
            assertFits(overlay);
            button(overlay, "Toàn màn hình").doClick();
            key(game, "ESCAPE");
            button(overlay, "THOÁT").doClick();
            check(toggles.get() == 1 && escapes.get() == 1 && exits.get() == 1, "result controls forward callbacks");

            game.start("preview", "doanngocquang", "player_a");
            check(!overlay.isVisible() && !overlay.isAnimating(), "new match clears result and timer");
            key(game, "SPACE");
            check(inputs.get() == 4, "new match restores gameplay input");
            game.setState(state());
            game.setMatchResult(new MatchResultStats(0, 13, 3, 7));
            game.gameOver("player_a", "Hết thời gian.");
            clock.addAndGet(3_100_000_000L);
            overlay.refreshAnimation();
            capture(game, "game-over-loss", 680, 365);
            check(card(overlay, "CHIẾN THẮNG").contains("player_a"), "opponent winner remains correctly identified");
            check(overlay.getComponent(0).getAccessibleContext().getAccessibleName()
                            .startsWith("THẤT BẠI: doanngocquang (BẠN)"),
                    "local defeat occupies the primary card even when the opponent wins");
            capture(game, "game-over-loss-large", 1920, 1080);
            assertFits(overlay);

            game.start("preview", "player_a", "doanngocquang");
            game.setState(state());
            game.setMatchResult(new MatchResultStats(1, 14, 1, 8));
            game.gameOver("DRAW", "Cả hai người chơi đã hết đạn.");
            clock.addAndGet(3_100_000_000L);
            overlay.refreshAnimation();
            capture(game, "game-over-draw", 680, 365);
            check(overlay.getComponent(0).getAccessibleContext().getAccessibleName().startsWith("HÒA TRẬN")
                    && overlay.getComponent(1).getAccessibleContext().getAccessibleName().startsWith("HÒA TRẬN"),
                    "draw marks both players equally");
            capture(game, "game-over-draw-large", 900, 540);
            assertFits(overlay);

            game.start("preview", "player_a", "doanngocquang");
            game.gameOver("doanngocquang", "Kết thúc khi chưa nhận snapshot.");
            check(card(overlay, "CHIẾN THẮNG").contains("Điểm cộng trận này: —"), "missing match result does not reuse old points");
            check(card(overlay, "CHIẾN THẮNG").contains("Tổng điểm backend: chưa đồng bộ"),
                    "missing backend total is labeled as unsynchronized");
            clock.addAndGet(800_000_000L);
            overlay.refreshAnimation();
            capture(game, "game-over-syncing", 900, 540);
            check(overlay.isAnimating(), "next result starts fresh animation");
            double animationTime = overlay.elapsedSeconds();
            game.setMatchResult(new MatchResultStats(0, 13, 3, 17));
            check(card(overlay, "CHIẾN THẮNG").contains("Điểm cộng trận này: +3, Tổng điểm backend: 17")
                            && card(overlay, "THẤT BẠI").contains("Điểm cộng trận này: +0, Tổng điểm backend: 13"),
                    "late backend scores update both visible result cards");
            check(overlay.elapsedSeconds() == animationTime, "late scores do not restart the result animation");
            frame.setVisible(false);
            check(!overlay.isAnimating(), "hidden window stops animation");
            frame.setVisible(true);
            check(overlay.isAnimating(), "transient fullscreen peer change can resume animation");
            frame.dispose();
            check(!overlay.isAnimating(), "disposed window releases timer");

            AtomicLong longClock = new AtomicLong();
            MatchResultOverlay longNames = new MatchResultOverlay(() -> { }, () -> { }, () -> { }, longClock::get);
            String name = "ten_nguoi_choi_rat_dai_1234567890123456789";
            longNames.showResult(name, name, "doi_thu_rat_dai_1234567890123456789", name,
                    "Lý do dài vẫn có thể đọc đầy đủ qua tooltip khi không đủ diện tích hiển thị.",
                    new MatchResultStats(3, 16, 0, 7));
            longClock.set(3_100_000_000L);
            capture(longNames, "game-over-long-names", 639, 326);
            check(name.equals(((JComponent) longNames.getComponent(0)).getToolTipText()), "full name preserved in tooltip");
            longNames.reset();

            // Score ordering follows the server slots, independent of who owns this client.
            for (boolean localFirst : new boolean[]{true, false}) {
                for (String winner : new String[]{"alice", "bob", "DRAW"}) {
                    String local = localFirst ? "alice" : "bob";
                    longNames.showResult(local, "alice", "bob", winner, "Server result",
                            new MatchResultStats(2, 111, 7, 222));
                    String expected = "DRAW".equals(winner) ? "HÒA TRẬN"
                            : local.equals(winner) ? "CHIẾN THẮNG" : "THẤT BẠI";
                    String primary = longNames.getComponent(0).getAccessibleContext().getAccessibleName();
                    check(primary.startsWith(expected + ": " + local + " (BẠN)")
                                    && primary.contains(localFirst
                                    ? "Điểm cộng trận này: +2, Tổng điểm backend: 111"
                                    : "Điểm cộng trận này: +7, Tổng điểm backend: 222"),
                            "local outcome and backend score mapping for both server slots: " + local + "/" + winner);
                    String other = longNames.getComponent(1).getAccessibleContext().getAccessibleName();
                    check(other.contains(localFirst
                                    ? "Điểm cộng trận này: +7, Tổng điểm backend: 222"
                                    : "Điểm cộng trận này: +2, Tổng điểm backend: 111"),
                            "opponent backend values remain attached to the correct player");
                    longNames.reset();
                }
            }
            return checks;
        } finally {
            frame.dispose();
        }
    }

    private static GameState state() {
        return new GameState("preview", 0, new GameState.Tank(400, 100, 12, 5),
                new GameState.Tank(280, 20, 8, 2), List.of());
    }

    private static GameState lethalState() {
        return new GameState("preview", 0, new GameState.Tank(400, 100, 12, 5),
                new GameState.Tank(280, 0, 8, 2), List.of());
    }

    private static void assertFits(MatchResultOverlay overlay) {
        Rectangle bounds = new Rectangle(0, 0, overlay.getWidth(), overlay.getHeight());
        for (Component child : overlay.getComponents()) {
            check(bounds.contains(child.getBounds()), "result content inside available area");
        }
        check(!overlay.getComponent(0).getBounds().intersects(overlay.getComponent(1).getBounds()), "cards do not overlap");
        check(overlay.getComponent(0).getHeight() > overlay.getComponent(1).getHeight(),
                "local result has priority over the opponent strip");
        check(overlay.getComponent(1).getY() + overlay.getComponent(1).getHeight()
                <= overlay.getComponent(2).getY(), "cards leave room for reason and controls");
    }

    private static String card(MatchResultOverlay overlay, String prefix) {
        for (Component child : overlay.getComponents()) {
            String name = child.getAccessibleContext().getAccessibleName();
            if (name != null && name.startsWith(prefix)) { return name; }
        }
        throw new AssertionError("Missing result card: " + prefix);
    }

    private static void key(GamePlayPanel game, String stroke) {
        Object action = game.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke));
        game.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(game, 0, stroke));
    }

    private static JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton b && b.getText().equals(text)) { return b; }
        }
        throw new AssertionError("Missing button: " + text);
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) { return type.cast(child); }
            if (child instanceof Container c) {
                T found = find(c, type);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static void capture(JComponent component, String name, int width, int height) throws Exception {
        component.setSize(width, height);
        layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        component.printAll(g);
        g.dispose();
        Path directory = Path.of("client", "out", "preview");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container c) { layout(c); }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }
}
