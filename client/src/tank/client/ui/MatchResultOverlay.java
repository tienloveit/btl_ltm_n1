package tank.client.ui;

import tank.client.model.MatchResultStats;
import tank.client.audio.SoundPlayer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.LongSupplier;

/** Local Swing overlay: the server remains the sole authority for results and scores. */
final class MatchResultOverlay extends JPanel {
    private static final Color WIN = HomeTheme.LIME;
    private static final Color LOSS = HomeTheme.CORAL;
    private static final Color DRAW = HomeTheme.CYAN;
    private static final double DURATION = 3.0;
    private final LongSupplier clock;
    private final Timer animation;
    private final ResultCard primary = new ResultCard(false);
    private final ResultCard opponent = new ResultCard(true);
    private final JLabel reason = new JLabel("", JLabel.CENTER);
    private final JButton again = GameTheme.button("CHƠI TIẾP", true);
    private final JButton fullscreen = GameTheme.button("Toàn màn hình", false);
    private final JButton exit = GameTheme.button("THOÁT", false);
    private final JButton sound;
    private long startedAt;
    private boolean hasResult;
    private boolean draw;
    private boolean localWins;
    private float uiScale = 1f;
    private String localPlayer;
    private String player1Name;
    private String player2Name;

    MatchResultOverlay(Runnable playAgain, Runnable leave, Runnable toggleFullScreen) {
        this(playAgain, leave, toggleFullScreen, System::nanoTime);
    }

    MatchResultOverlay(Runnable playAgain, Runnable leave, Runnable toggleFullScreen, LongSupplier clock) {
        this(playAgain, leave, toggleFullScreen, clock, SoundPlayer.SILENT);
    }

    MatchResultOverlay(Runnable playAgain, Runnable leave, Runnable toggleFullScreen, LongSupplier clock,
                       SoundPlayer audio) {
        super(null);
        this.clock = clock;
        sound = SoundControls.button(audio);
        setOpaque(false);
        setVisible(false);
        // Consume pointer input across the entire overlay, including gaps between cards.
        addMouseListener(new java.awt.event.MouseAdapter() { });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
        reason.setForeground(HomeTheme.MUTED);
        reason.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        HomeTheme.button(again, "tank");
        HomeTheme.button(fullscreen, null);
        HomeTheme.button(sound, "settings");
        HomeTheme.button(exit, "logout");
        exit.setForeground(HomeTheme.CORAL);
        add(primary);
        add(opponent);
        add(reason);
        add(again);
        add(fullscreen);
        add(sound);
        add(exit);
        again.setFocusable(false);
        fullscreen.setFocusable(false);
        exit.setFocusable(false);
        again.addActionListener(event -> {
            again.setEnabled(false);
            again.setText("ĐANG CHỜ ĐỐI THỦ…");
            showWaiting("Đã sẵn sàng. Đang chờ đối thủ chọn chơi tiếp…");
            revalidate();
            doLayout();
            playAgain.run();
        });
        fullscreen.addActionListener(event -> toggleFullScreen.run());
        exit.addActionListener(event -> leave.run());
        animation = new Timer(16, event -> refreshAnimation());
        animation.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing() && hasResult) { refreshAnimation(); }
                else { animation.stop(); }
            }
        });
    }

    void showResult(String local, String player1, String player2, String winner,
                    String explanation, MatchResultStats stats) {
        if (hasResult) { return; }
        localPlayer = local;
        player1Name = player1;
        player2Name = player2;
        draw = "DRAW".equals(winner);
        localWins = local.equalsIgnoreCase(winner);
        configureCards(stats);
        hasResult = true;
        startedAt = clock.getAsLong();
        again.setEnabled(true);
        again.setText("CHƠI TIẾP");
        showWaiting(explanation);
        setVisible(true);
        refreshAnimation();
    }

    void updateScores(MatchResultStats stats) {
        if (!hasResult) { return; }
        configureCards(stats);
        primary.repaint();
        opponent.repaint();
    }

    private void configureCards(MatchResultStats stats) {
        boolean localIsFirst = localPlayer.equalsIgnoreCase(player1Name);
        int points1 = stats == null ? -1 : stats.player1Points();
        int points2 = stats == null ? -1 : stats.player2Points();
        int total1 = stats == null ? -1 : stats.player1TotalScore();
        int total2 = stats == null ? -1 : stats.player2TotalScore();
        primary.configure(localIsFirst ? player1Name : player2Name, localIsFirst ? points1 : points2,
                localIsFirst ? total1 : total2, localPlayer,
                draw ? "HÒA TRẬN" : localWins ? "CHIẾN THẮNG" : "THẤT BẠI",
                draw ? DRAW : localWins ? WIN : LOSS, localWins, draw);
        opponent.configure(localIsFirst ? player2Name : player1Name, localIsFirst ? points2 : points1,
                localIsFirst ? total2 : total1, localPlayer,
                draw ? "HÒA TRẬN" : localWins ? "THẤT BẠI" : "CHIẾN THẮNG",
                draw ? DRAW : localWins ? LOSS : WIN, !draw && !localWins, draw);
    }

    void showWaiting(String text) {
        reason.setText(text);
        reason.setToolTipText(text);
    }

    void setFullScreen(boolean enabled) {
        fullscreen.setText(enabled ? "Về cửa sổ" : "Toàn màn hình");
    }

    void reset() {
        animation.stop();
        hasResult = false;
        localPlayer = null;
        player1Name = null;
        player2Name = null;
        setVisible(false);
    }

    void stopAnimation() { animation.stop(); }

    boolean isAnimating() { return animation.isRunning(); }

    double elapsedSeconds() {
        return hasResult ? Math.max(0, (clock.getAsLong() - startedAt) / 1_000_000_000.0) : 0;
    }

    // Clock-driven so previews and timing checks never have to sleep on the EDT.
    void refreshAnimation() {
        if (!hasResult) { return; }
        doLayout();
        repaint();
        if (elapsedSeconds() >= DURATION || !isShowing()) { animation.stop(); }
        else if (!animation.isRunning()) { animation.start(); }
    }

    @Override public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { return; }
        boolean compact = h < 420;
        uiScale = ResponsiveUi.scaleFor(w, h);
        for (JButton button : new JButton[]{again, fullscreen, sound, exit}) {
            // Buttons keep the same compact metrics when the waiting label grows.
            button.setFont(new Font("Segoe UI", Font.BOLD, Math.round((compact ? 12 : 14) * uiScale)));
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(
                    Math.round(8 * uiScale), Math.round(12 * uiScale),
                    Math.round(8 * uiScale), Math.round(12 * uiScale)));
        }
        reason.setFont(new Font("Segoe UI", Font.PLAIN, Math.round((compact ? 12 : 13) * uiScale)));
        int pad = Math.round((compact ? 12 : 24) * uiScale);
        int gap = Math.round(8 * uiScale);
        int buttonHeight = Math.round((compact ? 34 : 42) * uiScale);
        int controlsWidth = again.getPreferredSize().width + fullscreen.getPreferredSize().width
                + sound.getPreferredSize().width + exit.getPreferredSize().width + gap * 3;
        boolean stacked = controlsWidth > w - pad * 2;
        int controlsHeight = stacked ? buttonHeight * 2 + gap : buttonHeight;
        int reasonHeight = Math.round(22 * uiScale);
        int opponentHeight = Math.round((compact ? 52 : 76) * uiScale);
        int top = Math.round((compact ? 26 : 40) * uiScale);
        int cardW = Math.max(1, Math.min(Math.round(880 * uiScale), w - pad * 2));
        int maxBodyHeight = h - top - pad - controlsHeight - reasonHeight - gap * 3;
        int heroHeight = Math.max(1, Math.min(Math.round(360 * uiScale), maxBodyHeight - opponentHeight - gap));
        int bodyHeight = heroHeight + opponentHeight + gap;
        int blockHeight = bodyHeight + reasonHeight + controlsHeight + gap * 3;
        int x = (w - cardW) / 2;
        int y = top + Math.max(0, (h - top - pad - blockHeight) / 2);
        positionCard(primary, x, y, cardW, heroHeight, 0.08);
        positionCard(opponent, x, y + heroHeight + gap, cardW, opponentHeight, 0.08);
        int controlsY = y + bodyHeight + gap;
        reason.setBounds(pad, controlsY, Math.max(1, w - pad * 2), reasonHeight);
        if (stacked) {
            layoutButtons(controlsY + reasonHeight + gap, buttonHeight, again, fullscreen);
            layoutButtons(controlsY + reasonHeight + buttonHeight + gap * 2, buttonHeight, sound, exit);
        } else {
            layoutButtons(controlsY + reasonHeight + gap, buttonHeight, again, fullscreen, sound, exit);
        }
    }

    private void layoutButtons(int y, int height, JButton... buttons) {
        int gap = Math.round(8 * uiScale);
        int totalWidth = (buttons.length - 1) * gap;
        for (JButton button : buttons) { totalWidth += button.getPreferredSize().width; }
        int x = (getWidth() - totalWidth) / 2;
        for (JButton button : buttons) {
            int width = button.getPreferredSize().width;
            button.setBounds(x, y, width, height);
            x += width + gap;
        }
    }

    private void positionCard(ResultCard card, int x, int y, int w, int h, double delay) {
        double p = clamp((elapsedSeconds() - delay) / 0.65);
        double eased = 1 - Math.pow(1 - p, 3);
        // Both panels translate together, so the opponent strip never covers the hero.
        card.setBounds(x, y + (int) ((1 - eased) * getHeight() * .35), w, h);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = quality(graphics);
        float fade = (float) clamp(elapsedSeconds() / 0.2);
        g.setComposite(AlphaComposite.SrcOver.derive(fade));
        HomeTheme.backdrop(g, getWidth(), getHeight());
        g.setPaint(new RadialGradientPaint(getWidth() * 0.5f, getHeight() * 0.4f,
                Math.max(1, getWidth() * 0.65f), new float[]{0, 1},
                new Color[]{new Color(primary.accent.getRed(), primary.accent.getGreen(), primary.accent.getBlue(), 25),
                        new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.round(11 * uiScale)));
        g.setColor(HomeTheme.MUTED);
        centered(g, "T A N K   A R E N A   /   K Ế T   Q U Ả", getWidth() / 2, Math.round(20 * uiScale));
        g.dispose();
    }

    @Override protected void paintChildren(Graphics graphics) {
        Graphics2D g = quality(graphics);
        g.setComposite(AlphaComposite.SrcOver.derive((float) clamp(elapsedSeconds() / 0.2)));
        super.paintChildren(g);
        double t = elapsedSeconds();
        if (localWins && t > 0.5 && t < DURATION) { paintConfetti(g, t - 0.5); }
        g.dispose();
    }

    private void paintConfetti(Graphics2D g, double t) {
        g.clipRect(0, 0, getWidth(), Math.max(1, getHeight() - 85));
        float alpha = (float) clamp((2.5 - t) / 0.7);
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        for (int i = 0; i < 80; i++) {
            double velocity = 55 + (i * 37 % 170);
            double x = primary.getX() + primary.getWidth() * 0.5 + Math.sin(i * 2.4) * velocity * t * uiScale;
            double y = primary.getY() + primary.getHeight() * 0.3 - (90 + i * 19 % 110) * t * uiScale + 100 * t * t;
            Graphics2D particle = (Graphics2D) g.create();
            particle.translate(x, y);
            particle.rotate(i + t * (i % 2 == 0 ? 3 : -3));
            particle.setColor(i % 3 == 0 ? HomeTheme.TEXT : i % 3 == 1 ? WIN : HomeTheme.GOLD);
            particle.fillRoundRect(-3, -2, 4 + i % 5, 3 + i % 3, 2, 2);
            particle.dispose();
        }
    }

    private final class ResultCard extends JPanel {
        private final boolean compact;
        private String name = "", title = "";
        private int points, totalScore;
        private boolean local, victory, tied;
        private Color accent = WIN;

        ResultCard(boolean compact) { this.compact = compact; setOpaque(false); }

        void configure(String name, int points, int totalScore, String localName, String title, Color accent,
                       boolean victory, boolean tied) {
            this.name = name;
            this.points = points;
            this.totalScore = totalScore;
            this.local = name.equalsIgnoreCase(localName);
            this.title = title;
            this.accent = accent;
            this.victory = victory;
            this.tied = tied;
            setToolTipText(name);
            getAccessibleContext().setAccessibleName(title + ": " + name + (local ? " (BẠN)" : "")
                    + ", Điểm cộng trận này: " + (points < 0 ? "—" : "+" + points)
                    + ", Tổng điểm backend: " + (totalScore < 0 ? "chưa đồng bộ" : totalScore));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = quality(graphics);
            int w = getWidth(), h = getHeight();
            if (w < 8 || h < 8) { g.dispose(); return; }
            RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 18, 18);
            g.setPaint(new GradientPaint(0, 0, HomeTheme.PANEL.brighter(), w, h, HomeTheme.PANEL));
            g.fill(shape);
            g.setColor(compact ? HomeTheme.EDGE : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 130));
            g.draw(shape);
            g.clip(shape);
            if (compact) { paintOpponent(g, w, h); }
            else { paintHero(g, w, h); }
            g.dispose();
        }

        private void paintHero(Graphics2D g, int w, int h) {
            boolean small = h < 230 * uiScale;
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 11));
            g.fillPolygon(new int[]{w / 2, w, w}, new int[]{0, 0, h}, 3);
            g.setColor(accent);
            g.fillRoundRect(w / 2 - Math.round(32 * uiScale), 0, Math.round(64 * uiScale),
                    Math.round(3 * uiScale), 3, 3);
            font(g, Font.BOLD, small ? 27 : 36);
            centered(g, title, w / 2, (int) (h * (small ? .22 : .15)));
            if (!small) {
                Graphics2D emblem = (Graphics2D) g.create();
                emblem.translate(w / 2.0, h * .35);
                double scale = Math.min(1.0 * uiScale, h / 350.0);
                emblem.scale(scale, scale);
                paintEmblem(emblem, accent, victory, tied);
                emblem.dispose();
            }
            font(g, Font.BOLD, small ? 17 : 22);
            g.setColor(HomeTheme.TEXT);
            centered(g, ellipsize(g, "BẠN  ·  " + name, w - Math.round(40 * uiScale)),
                    w / 2, (int) (h * (small ? .43 : .59)));
            int statsY = (int) (h * (small ? .53 : .68));
            int statsH = (int) (h * (small ? .38 : .25));
            int margin = Math.round(18 * uiScale);
            g.setColor(HomeTheme.ALT);
            g.fillRoundRect(margin, statsY, w - margin * 2, statsH, 12, 12);
            g.setColor(HomeTheme.EDGE);
            g.drawLine(w / 2, statsY + 10, w / 2, statsY + statsH - 10);
            metric(g, "ĐIỂM CỘNG TRẬN NÀY", points < 0 ? null : "+" + points,
                    w / 4 + margin / 2, statsY, statsH, w / 2 - margin * 2, accent, false);
            metric(g, "TỔNG ĐIỂM", totalScore < 0 ? null : Integer.toString(totalScore),
                    w * 3 / 4 - margin / 2, statsY, statsH, w / 2 - margin * 2, HomeTheme.TEXT, false);
        }

        private void paintOpponent(Graphics2D g, int w, int h) {
            int pad = Math.round(16 * uiScale);
            int avatarSize = Math.min(Math.round(32 * uiScale), h - 16);
            HomeIcon avatar = new HomeIcon("avatar", this, null, avatarSize, name);
            avatar.paintIcon(this, g, pad, (h - avatarSize) / 2);
            int textX = pad + avatarSize + Math.round(10 * uiScale);
            font(g, Font.BOLD, 14);
            g.setColor(HomeTheme.TEXT);
            g.drawString(ellipsize(g, name, (int) (w * .47) - textX - pad), textX, (int) (h * .43));
            font(g, Font.BOLD, 10);
            g.setColor(accent);
            g.drawString("ĐỐI THỦ  ·  " + title, textX, (int) (h * .75));
            metric(g, "ĐIỂM CỘNG", points < 0 ? null : "+" + points,
                    (int) (w * .60), 0, h, (int) (w * .23), accent, true);
            metric(g, "TỔNG ĐIỂM", totalScore < 0 ? null : Integer.toString(totalScore),
                    (int) (w * .85), 0, h, (int) (w * .25) - pad, HomeTheme.TEXT, true);
        }

        private void metric(Graphics2D g, String caption, String value, int cx, int y,
                            int h, int maxWidth, Color tint, boolean small) {
            font(g, Font.BOLD, small ? 10 : 11);
            g.setColor(HomeTheme.MUTED);
            centered(g, caption, cx, y + (int) (h * .33));
            font(g, Font.BOLD, value == null ? (small ? 11 : 15) : small ? 18 : 28);
            g.setColor(value == null ? HomeTheme.MUTED : tint);
            centered(g, ellipsize(g, value == null ? "Đang đồng bộ…" : value, maxWidth),
                    cx, y + (int) (h * .78));
        }
    }

    private void font(Graphics2D g, int style, float size) {
        g.setFont(new Font("Segoe UI", style, Math.max(9, Math.round(size * uiScale))));
    }

    private static void paintEmblem(Graphics2D g, Color accent, boolean victory, boolean tied) {
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(-30, -40, accent.brighter(), 36, 40, accent.darker()));
        if (victory) {
            g.drawRoundRect(-48, -34, 96, 40, 24, 24);
            Path2D cup = new Path2D.Double();
            cup.moveTo(-32, -42); cup.lineTo(32, -42); cup.lineTo(26, 0);
            cup.quadTo(0, 33, -26, 0); cup.closePath();
            g.fill(cup);
            g.fillRoundRect(-5, 10, 10, 24, 3, 3);
            g.fillRoundRect(-27, 31, 54, 10, 4, 4);
            g.setColor(HomeTheme.PANEL);
            g.fill(star(0, -15, 12, 5));
            g.setColor(accent);
            g.fill(star(-56, -52, 6, 2));
            g.fill(star(55, -23, 8, 3));
        } else {
            Path2D shield = new Path2D.Double();
            shield.moveTo(0, -48); shield.lineTo(38, -32); shield.lineTo(32, 10);
            shield.quadTo(20, 35, 0, 46); shield.quadTo(-20, 35, -32, 10);
            shield.lineTo(-38, -32); shield.closePath();
            g.fill(shield);
            g.setColor(HomeTheme.PANEL);
            if (tied) {
                g.fillRoundRect(-18, -14, 36, 8, 3, 3);
                g.fillRoundRect(-18, 3, 36, 8, 3, 3);
            } else {
                Path2D crack = new Path2D.Double();
                crack.moveTo(7, -48); crack.lineTo(-7, -13); crack.lineTo(11, -5);
                crack.lineTo(-6, 18); crack.lineTo(0, 46);
                g.draw(crack);
            }
        }
    }

    private static Path2D star(double x, double y, double outer, double inner) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5 - Math.PI / 2, r = i % 2 == 0 ? outer : inner;
            double px = x + Math.cos(angle) * r, py = y + Math.sin(angle) * r;
            if (i == 0) { path.moveTo(px, py); } else { path.lineTo(px, py); }
        }
        path.closePath();
        return path;
    }

    private static String ellipsize(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) { return text; }
        int end = text.length();
        while (end > 0 && g.getFontMetrics().stringWidth(text.substring(0, end) + "…") > width) { end--; }
        return text.substring(0, end) + "…";
    }

    private static void centered(Graphics2D g, String text, int x, int baseline) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static Graphics2D quality(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
