package tank.client.ui;

import tank.client.model.GameState;
import tank.client.model.MatchResultStats;
import tank.client.audio.SoundEffect;
import tank.client.audio.SoundPlayer;
import tank.client.audio.MusicScene;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.JLayeredPane;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/** Gameplay renderer. Keyboard input is forwarded to the controller; it never simulates state locally. */
final class GamePlayPanel extends JPanel {
    static final double TANK_SCALE = 0.765;
    private static final long SHOT_INPUT_INTERVAL_NANOS = 350_000_000L;
    private final String localPlayer;
    private final Runnable moveLeft;
    private final Runnable moveRight;
    private final Runnable shoot;
    private final JButton fullScreenButton = GameTheme.button("Toàn màn hình", false);
    private final JLabel timer = new JLabel("03:00", JLabel.CENTER);
    private final JLabel notice = new JLabel("Đang chờ trạng thái trận đấu từ Server...", JLabel.CENTER);
    private final JLabel controls = new JLabel("Di chuyển:  ←  → / A  D       Bắn:  SPACE");
    private final JButton exitButton = GameTheme.dangerButton("EXIT");
    private final Arena arena = new Arena();
    private final MatchHud hud = new MatchHud();
    private final MatchResultOverlay result;
    private final Timer impactTimer;
    private final LongSupplier clock;
    private final SoundPlayer audio;
    private final JButton soundButton;
    private boolean settingsOpen;
    private boolean finished;
    private boolean effectsStopped;
    private PendingResult pendingResult;
    private MatchResultStats matchResultStats;
    private String player1Name = "P1";
    private String player2Name = "P2";
    private float appliedScale = -1f;
    private long lastShotInputNanos = Long.MIN_VALUE;

    GamePlayPanel(String localPlayer, Runnable moveLeft, Runnable moveRight, Runnable shoot,
                  Runnable playAgain, Runnable exit, Runnable toggleFullScreen, Runnable exitFullScreen) {
        this(localPlayer, moveLeft, moveRight, shoot, playAgain, exit, toggleFullScreen, exitFullScreen,
                System::nanoTime);
    }

    GamePlayPanel(String localPlayer, Runnable moveLeft, Runnable moveRight, Runnable shoot,
                  Runnable playAgain, Runnable exit, Runnable toggleFullScreen, Runnable exitFullScreen,
                  LongSupplier clock) {
        this(localPlayer, moveLeft, moveRight, shoot, playAgain, exit, toggleFullScreen, exitFullScreen,
                clock, SoundPlayer.SILENT);
    }

    GamePlayPanel(String localPlayer, Runnable moveLeft, Runnable moveRight, Runnable shoot,
                  Runnable playAgain, Runnable exit, Runnable toggleFullScreen, Runnable exitFullScreen,
                  SoundPlayer audio) {
        this(localPlayer, moveLeft, moveRight, shoot, playAgain, exit, toggleFullScreen, exitFullScreen,
                System::nanoTime, audio);
    }

    GamePlayPanel(String localPlayer, Runnable moveLeft, Runnable moveRight, Runnable shoot,
                  Runnable playAgain, Runnable exit, Runnable toggleFullScreen, Runnable exitFullScreen,
                  LongSupplier clock, SoundPlayer audio) {
        super(new BorderLayout(6, 6));
        this.localPlayer = localPlayer;
        this.moveLeft = moveLeft;
        this.moveRight = moveRight;
        this.shoot = shoot;
        this.clock = clock;
        this.audio = audio;
        soundButton = SoundControls.button(audio, open -> {
            settingsOpen = open;
            if (!open) { javax.swing.SwingUtilities.invokeLater(this::requestFocusInWindow); }
        });
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        result = new MatchResultOverlay(() -> {
            audio.stopEffects();
            playAgain.run();
        }, () -> {
            audio.stopEffects();
            exit.run();
        }, toggleFullScreen, clock, audio);
        JPanel gameplay = new JPanel(new BorderLayout(0, 4));
        gameplay.setOpaque(false);
        JLayeredPane layers = new JLayeredPane() {
            @Override public void doLayout() {
                gameplay.setBounds(0, 0, getWidth(), getHeight());
                result.setBounds(0, 0, getWidth(), getHeight());
            }
        };
        layers.setPreferredSize(new Dimension(700, 500));
        layers.add(gameplay, JLayeredPane.DEFAULT_LAYER);
        layers.add(result, JLayeredPane.PALETTE_LAYER);
        add(layers, BorderLayout.CENTER);

        gameplay.add(hud, BorderLayout.NORTH);

        arena.setPreferredSize(new Dimension(700, 390));
        gameplay.add(arena, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        controls.setForeground(GameTheme.MUTED);
        controls.setFont(controls.getFont().deriveFont(14f));
        footer.add(controls, BorderLayout.WEST);
        notice.setForeground(GameTheme.MUTED);
        notice.setFont(notice.getFont().deriveFont(14f));
        footer.add(notice, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(soundButton);
        fullScreenButton.setFocusable(false);
        fullScreenButton.addActionListener(event -> toggleFullScreen.run());
        actions.add(fullScreenButton);
        exitButton.setFocusable(false);
        exitButton.addActionListener(event -> {
            audio.stopEffects();
            exit.run();
        });
        actions.add(exitButton);
        footer.add(actions, BorderLayout.EAST);
        gameplay.add(footer, BorderLayout.SOUTH);
        bindKeys();
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "exit-full-screen");
        getActionMap().put("exit-full-screen", action(exitFullScreen));
        impactTimer = new Timer(33, event -> refreshEffects());
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                refreshEffects();
            }
        });
        applyGameplayMetrics();
    }

    private float gameplayScale() {
        return Math.max(1f, Math.min(1.25f, Math.min(getWidth() / 1000f, getHeight() / 620f)));
    }

    private void applyGameplayMetrics() {
        float scale = gameplayScale();
        if (Math.abs(appliedScale - scale) < 0.001f) { return; }
        appliedScale = scale;
        controls.setFont(controls.getFont().deriveFont(14f * scale));
        notice.setFont(notice.getFont().deriveFont(14f * scale));
        for (JButton button : new JButton[]{soundButton, fullScreenButton, exitButton}) {
            button.setFont(button.getFont().deriveFont(Font.BOLD, 14f * scale));
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(8 * scale),
                    Math.round(12 * scale), Math.round(8 * scale), Math.round(12 * scale)));
        }
    }

    @Override public void doLayout() {
        applyGameplayMetrics();
        super.doLayout();
    }

    void setFullScreen(boolean fullScreen) {
        fullScreenButton.setText(fullScreen ? "Về cửa sổ" : "Toàn màn hình");
        result.setFullScreen(fullScreen);
    }

    void start(String roomId, String player1, String player2) {
        audio.stopEffects();
        audio.setMusicScene(MusicScene.BATTLE);
        player1Name = player1;
        player2Name = player2;
        arena.localIsPlayer1 = localPlayer.equals(player1);
        lastShotInputNanos = Long.MIN_VALUE;
        notice.setText("Trận đấu đang diễn ra");
        finished = false;
        effectsStopped = false;
        pendingResult = null;
        matchResultStats = null;
        result.reset();
        impactTimer.stop();
        arena.impacts.clear();
        arena.player1Destruction = null;
        arena.player2Destruction = null;
        arena.state = null;
        timer.setText("03:00");
        arena.repaint();
        hud.repaint();
        audio.play(SoundEffect.START);
        requestFocusInWindow();
    }

    void setState(GameState state) {
        if (effectsStopped) { return; }
        GameState previous = arena.state;
        if (!finished && previous != null) {
            if (state.player1().ammo() < previous.player1().ammo()) { audio.play(SoundEffect.SHOT); }
            if (state.player2().ammo() < previous.player2().ammo()) { audio.play(SoundEffect.SHOT); }
        }
        arena.state = state;
        long now = clock.getAsLong();
        if (state.player1().hp() <= 0 && arena.player1Destruction == null) {
            arena.player1Destruction = new TankDestruction(state.player1().x(), ArenaViewport.PLAYER1_Y, true, now);
            audio.play(SoundEffect.EXPLOSION);
        }
        if (state.player2().hp() <= 0 && arena.player2Destruction == null) {
            arena.player2Destruction = new TankDestruction(state.player2().x(), ArenaViewport.PLAYER2_Y, false, now);
            audio.play(SoundEffect.EXPLOSION);
        }
        timer.setText(String.format("%02d:%02d", state.secondsRemaining() / 60, state.secondsRemaining() % 60));
        refreshEffects();
    }

    void gameOver(String winner, String reason) {
        if (effectsStopped) { return; }
        if ("WAITING".equals(winner)) {
            notice.setText(reason);
            result.showWaiting(reason);
            return;
        }
        if (finished) { return; }
        finished = true;
        audio.setMusicScene(MusicScene.RESULT);
        String text = "DRAW".equals(winner) ? "Hòa trận" : (winner.equals(localPlayer) ? "Bạn thắng!" : "Bạn thua.");
        notice.setText(text + " " + reason);
        pendingResult = new PendingResult(winner, reason);
        refreshEffects();
        requestFocusInWindow();
    }

    void setMatchResult(MatchResultStats stats) {
        if (!effectsStopped) {
            matchResultStats = stats;
            result.updateScores(stats);
        }
    }

    void showImpact(int x, int y) {
        if (effectsStopped) { return; }
        if (!finished) { audio.play(SoundEffect.HIT); }
        arena.addImpact(x, y);
        refreshEffects();
    }

    // Also called when a fullscreen peer is recreated: elapsed time is never reset.
    void refreshEffects() {
        if (effectsStopped) { return; }
        arena.expireImpacts();
        boolean destroying = arena.isDestroying(clock.getAsLong());
        if (pendingResult != null && !destroying) {
            PendingResult ready = pendingResult;
            pendingResult = null;
            result.showResult(localPlayer, player1Name, player2Name, ready.winner(), ready.reason(), matchResultStats);
            audio.play("DRAW".equals(ready.winner()) ? SoundEffect.DRAW
                    : localPlayer.equals(ready.winner()) ? SoundEffect.VICTORY : SoundEffect.DEFEAT);
            requestFocusInWindow();
        }
        if (isShowing() && (destroying || !arena.impacts.isEmpty())) { impactTimer.start(); }
        else { impactTimer.stop(); }
        arena.repaint();
        hud.repaint();
    }

    private boolean acceptsInput() {
        return !settingsOpen && !finished && !effectsStopped && arena.player1Destruction == null && arena.player2Destruction == null;
    }

    private record PendingResult(String winner, String reason) { }

    private void bindKeys() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "move-left");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "move-right");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("A"), "move-left");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("D"), "move-right");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "shoot");
        getActionMap().put("move-left", action(() -> { if (acceptsInput()) { moveLeft.run(); } }));
        getActionMap().put("move-right", action(() -> { if (acceptsInput()) { moveRight.run(); } }));
        getActionMap().put("shoot", action(this::requestShot));
    }

    private void requestShot() {
        if (!acceptsInput()) { return; }
        long now = clock.getAsLong();
        if (lastShotInputNanos != Long.MIN_VALUE && now - lastShotInputNanos < SHOT_INPUT_INTERVAL_NANOS) {
            return;
        }
        lastShotInputNanos = now;
        shoot.run();
    }

    void stopEffects() {
        audio.stopEffects();
        audio.setMusicScene(MusicScene.OFF);
        effectsStopped = true;
        pendingResult = null;
        impactTimer.stop();
        arena.impacts.clear();
        arena.player1Destruction = null;
        arena.player2Destruction = null;
        arena.state = null;
        matchResultStats = null;
        result.reset();
        arena.repaint();
        hud.repaint();
    }

    @Override public void removeNotify() {
        impactTimer.stop();
        result.stopAnimation();
        super.removeNotify();
    }

    private static AbstractAction action(Runnable runnable) {
        return new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { runnable.run(); } };
    }

    static String healthLabel(int hp) {
        int safeHp = Math.max(0, Math.min(100, hp));
        return "HP " + safeHp + "/100 (" + safeHp + "%)";
    }

    static Color healthColorFor(int hp) {
        int safeHp = Math.max(0, Math.min(100, hp));
        if (safeHp > 50) { return new Color(46, 125, 88); }
        if (safeHp > 25) { return new Color(143, 99, 18); }
        return new Color(161, 65, 62);
    }

    private final class MatchHud extends JPanel {
        MatchHud() { setOpaque(false); }
        @Override public Dimension getPreferredSize() {
            return new Dimension(700, Math.round(60 * gameplayScale()));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float scale = gameplayScale();
            int centerWidth = Math.round(96 * scale);
            int gap = Math.round(8 * scale);
            int cardWidth = Math.max(1, (getWidth() - centerWidth - gap * 2) / 2);
            GameState state = arena.state;
            boolean p1 = arena.localIsPlayer1;
            drawPlayerInfo(g, 0, cardWidth, "BẠN • " + (p1 ? player1Name : player2Name),
                    state == null ? null : p1 ? state.player1() : state.player2(),
                    GameTheme.ACCENT, arena.wasHit(true), scale);
            drawPlayerInfo(g, getWidth() - cardWidth, cardWidth,
                    "ĐỐI THỦ • " + (p1 ? player2Name : player1Name),
                    state == null ? null : p1 ? state.player2() : state.player1(),
                    new Color(232, 151, 103), arena.wasHit(false), scale);
            g.setColor(GameTheme.ACCENT);
            g.setFont(getFont().deriveFont(Font.BOLD, 26f * scale));
            int textWidth = g.getFontMetrics().stringWidth(timer.getText());
            int baseline = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.drawString(timer.getText(), (getWidth() - textWidth) / 2, baseline);
            g.dispose();
        }
    }

    private static void drawPlayerInfo(Graphics2D g, int x, int width, String name,
                                       GameState.Tank tank, Color tint, boolean hit, float scale) {
        int height = Math.round(60 * scale);
        int padding = Math.round(12 * scale);
        g.setColor(GameTheme.SURFACE);
        g.fillRoundRect(x, 0, width, height - 1, 12, 12);
        g.setColor(hit ? new Color(255, 135, 91) : GameTheme.BORDER);
        g.drawRoundRect(x, 0, width - 1, height - 1, 12, 12);
        g.setColor(tint);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16f * scale));
        String title = name;
        while (g.getFontMetrics().stringWidth(title) > width - padding * 2 && title.length() > 4) {
            title = title.substring(0, title.length() - 2) + "…";
        }
        g.drawString(title, x + (width - g.getFontMetrics().stringWidth(title)) / 2, Math.round(20 * scale));
        int barX = x + padding, barY = Math.round(28 * scale);
        int barWidth = Math.max(1, width - padding * 2), barHeight = Math.round(8 * scale);
        g.setColor(new Color(48, 59, 64));
        g.fillRoundRect(barX, barY, barWidth, barHeight, barHeight, barHeight);
        if (tank != null && tank.hp() > 0) {
            g.setColor(healthColorFor(tank.hp()));
            g.fillRoundRect(barX, barY, Math.max(1, Math.round(barWidth * Math.min(100, tank.hp()) / 100f)),
                    barHeight, barHeight, barHeight);
        }
        String stats = tank == null ? "Đang chờ dữ liệu…" : healthLabel(tank.hp())
                + " • Đạn " + tank.ammo() + " • Điểm " + tank.score();
        float fontSize = 13f * scale;
        do {
            g.setFont(g.getFont().deriveFont(Font.BOLD, fontSize));
            if (g.getFontMetrics().stringWidth(stats) <= width - padding * 2 || fontSize <= 10f) { break; }
            fontSize -= 0.5f;
        } while (true);
        g.setColor(GameTheme.TEXT);
        g.drawString(stats, x + (width - g.getFontMetrics().stringWidth(stats)) / 2, Math.round(53 * scale));
    }

    private final class Arena extends JPanel {
        private GameState state;
        private boolean localIsPlayer1 = true;
        private final List<Impact> impacts = new ArrayList<>();
        private TankDestruction player1Destruction;
        private TankDestruction player2Destruction;
        boolean isDestroying(long now) {
            return (player1Destruction != null && !player1Destruction.isComplete(now))
                    || (player2Destruction != null && !player2Destruction.isComplete(now));
        }
        Arena() { setOpaque(false); setName("arena"); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D scene = (Graphics2D) graphics.create();
            scene.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ArenaViewport viewport = ArenaViewport.fit(getWidth(), getHeight());
            scene.transform(viewport.transform(localIsPlayer1));
            java.awt.Shape field = new java.awt.geom.RoundRectangle2D.Double(0, 0,
                    ArenaViewport.WIDTH, ArenaViewport.HEIGHT, 14, 14);
            scene.setPaint(new GradientPaint(0, 0, new Color(9, 28, 34),
                    0, ArenaViewport.HEIGHT, new Color(10, 20, 29)));
            scene.fill(field);
            scene.setStroke(new BasicStroke(1.5f));
            scene.setColor(new Color(84, 119, 122));
            scene.draw(field);
            scene.clip(field);
            int shake = localShake();
            scene.translate(shake, shake == 0 ? 0 : shake > 0 ? -2 : 2);
            scene.setStroke(new BasicStroke(0.75f));
            scene.setColor(new Color(83, 214, 213, 25));
            for (int x = 40; x < ArenaViewport.WIDTH; x += 40) {
                scene.drawLine(x, 0, x, ArenaViewport.HEIGHT);
            }
            for (int y = 40; y < ArenaViewport.HEIGHT; y += 40) {
                scene.drawLine(0, y, ArenaViewport.WIDTH, y);
            }
            scene.setColor(new Color(166, 232, 91, 45));
            scene.drawLine(0, 210, ArenaViewport.WIDTH, 210);
            if (state != null) {
                Color opponent = new Color(255, 161, 105);
                if (player1Destruction == null) { drawTank(scene, state.player1().x(), ArenaViewport.PLAYER1_Y, true,
                        localIsPlayer1 ? GameTheme.ACCENT : opponent, wasHit(localIsPlayer1));
                }
                if (player2Destruction == null) { drawTank(scene, state.player2().x(), ArenaViewport.PLAYER2_Y, false,
                        localIsPlayer1 ? opponent : GameTheme.ACCENT, wasHit(!localIsPlayer1));
                }
                for (GameState.Bullet bullet : state.bullets()) {
                    scene.setColor(new Color(83, 214, 213, 65));
                    scene.fillOval(bullet.x() - 10, bullet.y() - 10, 20, 20);
                    scene.setColor(GameTheme.CYAN);
                    scene.fillOval(bullet.x() - 4, bullet.y() - 4, 8, 8);
                }
                for (Impact impact : impacts) { drawImpact(scene, impact.x, impact.y, impact); }
                long now = clock.getAsLong();
                if (player1Destruction != null) { player1Destruction.draw(scene, now); }
                if (player2Destruction != null) { player2Destruction.draw(scene, now); }
            }
            scene.dispose();
        }
        void addImpact(int x, int y) {
            if (impacts.size() == 12) { impacts.remove(0); }
            impacts.add(new Impact(x, y, clock.getAsLong()));
        }
        boolean expireImpacts() {
            boolean changed = false;
            for (Iterator<Impact> iterator = impacts.iterator(); iterator.hasNext();) {
                if (iterator.next().ageMillis(clock.getAsLong()) > 560) { iterator.remove(); changed = true; }
            }
            return changed || !impacts.isEmpty();
        }
        private boolean wasHit(boolean local) {
            for (Impact impact : impacts) {
                boolean hitPlayer1 = impact.y > 210;
                if ((hitPlayer1 == localIsPlayer1) == local
                        && impact.ageMillis(clock.getAsLong()) < 220) { return true; }
            }
            return false;
        }
        private int localShake() {
            for (int index = impacts.size() - 1; index >= 0; index--) {
                Impact impact = impacts.get(index);
                if ((impact.y > 210) == localIsPlayer1) {
                    long age = impact.ageMillis(clock.getAsLong());
                    if (age < 180) { return (age / 30) % 2 == 0 ? 3 : -3; }
                    break;
                }
            }
            return 0;
        }
        private void drawImpact(Graphics2D g, int x, int y, Impact impact) {
            float progress = Math.min(1f, impact.ageMillis(clock.getAsLong()) / 560f);
            int alpha = Math.max(0, (int) (255 * (1f - progress)));
            for (int ring = 0; ring < 2; ring++) {
                int waveRadius = 8 + (int) ((42 + ring * 19) * progress) - ring * 8;
                g.setStroke(new BasicStroke(ring == 0 ? 3.2f : 1.8f));
                g.setColor(ring == 0 ? new Color(255, 184, 94, alpha * 3 / 4)
                        : new Color(255, 103, 61, alpha / 2));
                g.drawOval(x - waveRadius, y - waveRadius, waveRadius * 2, waveRadius * 2);
            }
            for (int spark = 0; spark < 20; spark++) {
                double angle = (Math.PI * 2 * spark / 20.0) + impact.phase();
                int start = 5 + (spark % 3) * 2;
                int length = 14 + (spark % 5) * 5 + (int) (40 * progress);
                int x1 = x + (int) (Math.cos(angle) * start);
                int y1 = y + (int) (Math.sin(angle) * start);
                int x2 = x + (int) (Math.cos(angle) * length);
                int y2 = y + (int) (Math.sin(angle) * length);
                Color color = spark % 3 == 0 ? new Color(255, 83, 55, alpha)
                        : spark % 3 == 1 ? new Color(255, 190, 44, alpha)
                        : new Color(255, 242, 161, alpha);
                g.setColor(color);
                g.setStroke(new BasicStroke(spark % 3 == 0 ? 2.5f : 1.5f));
                g.drawLine(x1, y1, x2, y2);
                int dot = Math.max(2, 5 - (int) (progress * 3));
                g.fillOval(x2 - dot / 2, y2 - dot / 2, dot, dot);
            }
            for (int cloud = 0; cloud < 4; cloud++) {
                int drift = (int) (progress * (16 + cloud * 5));
                int size = 11 + (int) (progress * 18) + cloud * 3;
                g.setColor(new Color(137, 151, 151, alpha / 5));
                g.fillOval(x - 28 + cloud * 14 - size / 2, y - drift - size / 2, size, size);
            }
            int core = Math.max(4, 18 - (int) (progress * 16));
            g.setColor(new Color(255, 126, 35, alpha));
            g.fillOval(x - core, y - core, core * 2, core * 2);
            g.setColor(new Color(255, 250, 213, alpha));
            g.fillOval(x - core / 2, y - core / 2, core, core);
        }
        private static void drawTank(Graphics2D graphics, int center, int y, boolean up, Color color, boolean hit) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(center, y + 20.5);
            g.scale(TANK_SCALE, TANK_SCALE);
            g.translate(-center, -y - 20.5);
            int middle = y + 20;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillOval(center - 46, middle - 29, 92, 58);
            g.setColor(new Color(22, 35, 39));
            g.fillRoundRect(center - 40, middle - 25, 14, 50, 7, 7);
            g.fillRoundRect(center + 26, middle - 25, 14, 50, 7, 7);
            g.setColor(new Color(60, 78, 81));
            for (int offset = -18; offset <= 18; offset += 9) {
                g.fillRect(center - 38, middle + offset, 10, 3);
                g.fillRect(center + 28, middle + offset, 10, 3);
            }
            // Scale the entire sprite about the same hull center as the authoritative server hitbox.
            g.setColor(hit ? Color.WHITE : color);
            g.fillRoundRect(center - 28, y + 5, 56, 31, 8, 8);
            g.setColor(hit ? new Color(255, 238, 190) : color.darker());
            g.fillOval(center - 13, middle - 13, 26, 26);
            g.setColor(hit ? Color.WHITE : color.brighter());
            g.fillRoundRect(center - 5, up ? middle - 35 : middle, 10, 35, 4, 4);
            g.setColor(new Color(10, 25, 29, 180));
            g.drawRoundRect(center - 28, y + 5, 56, 31, 8, 8);
            g.fillOval(center - 4, middle - 4, 8, 8);
            g.dispose();
        }

        private record Impact(int x, int y, long createdAt) {
            long ageMillis(long now) { return Math.max(0, (now - createdAt) / 1_000_000L); }
            double phase() { return ((x * 31L + y * 17L) % 360) * Math.PI / 180.0; }
        }
    }
}
