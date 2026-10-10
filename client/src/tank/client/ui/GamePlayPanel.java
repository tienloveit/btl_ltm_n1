package tank.client.ui;

import tank.client.model.GameState;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Gameplay renderer. Keyboard input is forwarded to the controller; it never simulates state locally. */
final class GamePlayPanel extends JPanel {
    private final String localPlayer;
    private final Runnable moveLeft;
    private final Runnable moveRight;
    private final Runnable shoot;
    private final Runnable playAgain;
    private final Runnable exit;
    private final JButton playAgainButton = GameTheme.button("CHƠI TIẾP", true);
    private final JLabel timer = new JLabel("03:00", JLabel.CENTER);
    private final JLabel notice = new JLabel("Đang chờ trạng thái trận đấu từ Server...", JLabel.CENTER);
    private final Arena arena = new Arena();
    private String player1Name = "P1";
    private String player2Name = "P2";

    GamePlayPanel(String localPlayer, Runnable moveLeft, Runnable moveRight, Runnable shoot,
                  Runnable playAgain, Runnable exit) {
        super(new BorderLayout(12, 12));
        this.localPlayer = localPlayer;
        this.moveLeft = moveLeft;
        this.moveRight = moveRight;
        this.shoot = shoot;
        this.playAgain = playAgain;
        this.exit = exit;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = GameTheme.card(new GridLayout(1, 1));
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 14, 10, 14));
        timer.setForeground(GameTheme.ACCENT);
        timer.setFont(timer.getFont().deriveFont(Font.BOLD, 26f));
        header.add(timer);
        add(header, BorderLayout.NORTH);

        arena.setPreferredSize(new Dimension(700, 390));
        add(arena, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        JLabel controls = new JLabel("Di chuyển:  ←  →       Bắn:  SPACE");
        controls.setForeground(GameTheme.MUTED);
        footer.add(controls, BorderLayout.WEST);
        notice.setForeground(GameTheme.MUTED);
        footer.add(notice, BorderLayout.CENTER);
        playAgainButton.setVisible(false);
        playAgainButton.addActionListener(event -> {
            playAgainButton.setEnabled(false);
            playAgain.run();
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(playAgainButton);
        JButton exitButton = GameTheme.dangerButton("EXIT");
        exitButton.addActionListener(event -> exit.run());
        actions.add(exitButton);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        bindKeys();
        new javax.swing.Timer(40, event -> {
            if (arena.expireImpacts()) { arena.repaint(); }
        }).start();
    }

    void start(String roomId, String player1, String player2) {
        player1Name = player1;
        player2Name = player2;
        arena.localIsPlayer1 = localPlayer.equals(player1);
        notice.setText("Trận đấu đang diễn ra");
        playAgainButton.setVisible(false);
        playAgainButton.setEnabled(true);
        arena.gameOver = null;
        requestFocusInWindow();
    }

    void setState(GameState state) {
        arena.state = state;
        timer.setText(String.format("%02d:%02d", state.secondsRemaining() / 60, state.secondsRemaining() % 60));
        arena.player1Name = player1Name;
        arena.player2Name = player2Name;
        arena.repaint();
    }

    void gameOver(String winner, String reason) {
        if ("WAITING".equals(winner)) {
            notice.setText(reason);
            return;
        }
        String text = "DRAW".equals(winner) ? "Hòa trận" : (winner.equals(localPlayer) ? "Bạn thắng!" : "Bạn thua.");
        notice.setText(text + " " + reason);
        playAgainButton.setVisible(true);
        arena.gameOver = text;
        arena.repaint();
    }

    void showImpact(int x, int y) {
        arena.addImpact(x, y);
        arena.repaint();
    }

    private void bindKeys() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "move-left");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "move-right");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "shoot");
        getActionMap().put("move-left", action(moveLeft));
        getActionMap().put("move-right", action(moveRight));
        getActionMap().put("shoot", action(shoot));
    }

    private static AbstractAction action(Runnable runnable) {
        return new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent event) { runnable.run(); } };
    }

    private static final class Arena extends JPanel {
        private GameState state;
        private String gameOver;
        private String player1Name = "P1";
        private String player2Name = "P2";
        private boolean localIsPlayer1 = true;
        private final List<Impact> impacts = new ArrayList<>();
        Arena() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Reserve space above/below the grid for player information.
            int left = 18, top = 45, width = getWidth() - 36, height = getHeight() - 90;
            g.setColor(new Color(8, 24, 28));
            g.fillRoundRect(left, top, width, height, 20, 20);
            g.setColor(GameTheme.BORDER);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(left, top, width, height, 20, 20);
            g.setColor(new Color(83, 214, 213, 45));
            for (int x = left + 40; x < left + width; x += 40) { g.drawLine(x, top, x, top + height); }
            for (int y = top + 40; y < top + height; y += 40) { g.drawLine(left, y, left + width, y); }
            if (state != null) {
                GameState.Tank localTank = localIsPlayer1 ? state.player1() : state.player2();
                GameState.Tank opponentTank = localIsPlayer1 ? state.player2() : state.player1();
                String localName = localIsPlayer1 ? player1Name : player2Name;
                String opponentName = localIsPlayer1 ? player2Name : player1Name;
                int opponentY = top + 10;
                int localY = top + height - 65;
                // Every client sees their own tank below and the opponent above.
                drawTank(g, opponentTank.x(), opponentY, false, new Color(255, 153, 84));
                drawTank(g, localTank.x(), localY, true, GameTheme.ACCENT);
                drawPlayerInfo(g, opponentTank.x(), top - 5, opponentName, opponentTank, true, left, width);
                drawPlayerInfo(g, localTank.x(), top + height, localName, localTank, false, left, width);
                g.setColor(GameTheme.CYAN);
                for (GameState.Bullet bullet : state.bullets()) {
                    int x = mapX(bullet.x(), left, width);
                    int modelY = localIsPlayer1 ? bullet.y() : 420 - bullet.y();
                    int y = top + modelY * height / 420;
                    g.fillOval(x - 4, y - 4, 8, 8);
                }
                for (Impact impact : impacts) {
                    int modelY = localIsPlayer1 ? impact.y : 420 - impact.y;
                    drawImpact(g, mapX(impact.x, left, width), top + modelY * height / 420, impact);
                }
            }
            if (gameOver != null) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRoundRect(left + width / 4, top + height / 2 - 40, width / 2, 80, 16, 16);
                g.setColor(GameTheme.TEXT);
                g.setFont(g.getFont().deriveFont(Font.BOLD, 24f));
                int textWidth = g.getFontMetrics().stringWidth(gameOver);
                g.drawString(gameOver, left + (width - textWidth) / 2, top + height / 2 + 8);
            }
            g.dispose();
        }
        private static int mapX(int x, int left, int width) { return left + x * width / 800; }
        void addImpact(int x, int y) { impacts.add(new Impact(x, y, System.nanoTime())); }
        boolean expireImpacts() {
            boolean changed = false;
            for (Iterator<Impact> iterator = impacts.iterator(); iterator.hasNext();) {
                if (iterator.next().ageMillis() > 420) { iterator.remove(); changed = true; }
            }
            return changed || !impacts.isEmpty();
        }
        private static void drawImpact(Graphics2D g, int x, int y, Impact impact) {
            float progress = Math.min(1f, impact.ageMillis() / 420f);
            int alpha = Math.max(0, (int) (255 * (1f - progress)));
            int waveRadius = 12 + (int) (34 * progress);
            g.setStroke(new BasicStroke(2.4f));
            g.setColor(new Color(255, 112, 42, Math.max(0, alpha / 2)));
            g.drawOval(x - waveRadius, y - waveRadius, waveRadius * 2, waveRadius * 2);

            // Deterministic sparks keep the same explosion visible to every client.
            for (int spark = 0; spark < 18; spark++) {
                double angle = (Math.PI * 2 * spark / 18.0) + impact.phase();
                int start = 5 + (spark % 3) * 2;
                int length = 18 + (spark % 5) * 5 + (int) (30 * progress);
                int x1 = x + (int) (Math.cos(angle) * start);
                int y1 = y + (int) (Math.sin(angle) * start);
                int x2 = x + (int) (Math.cos(angle) * length);
                int y2 = y + (int) (Math.sin(angle) * length);
                Color color = spark % 3 == 0 ? new Color(255, 83, 55, alpha)
                        : spark % 3 == 1 ? new Color(255, 190, 44, alpha)
                        : new Color(255, 242, 161, alpha);
                g.setColor(color);
                g.drawLine(x1, y1, x2, y2);
                int dot = Math.max(2, 5 - (int) (progress * 3));
                g.fillOval(x2 - dot / 2, y2 - dot / 2, dot, dot);
            }
            int core = Math.max(7, 19 - (int) (progress * 9));
            g.setColor(new Color(255, 126, 35, alpha));
            g.fillOval(x - core, y - core, core * 2, core * 2);
            g.setColor(new Color(255, 244, 183, alpha));
            g.fillOval(x - core / 2, y - core / 2, core, core);
        }
        private static void drawPlayerInfo(Graphics2D g, int tankX, int y, String name, GameState.Tank tank,
                                           boolean aboveTank, int left, int width) {
            int center = mapX(tankX, left, width);
            String line1 = name;
            String line2 = "HP: " + tank.hp() + "   Ammo: " + tank.ammo() + "   Score: " + tank.score();
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
            int infoWidth = Math.max(g.getFontMetrics().stringWidth(line1), g.getFontMetrics().stringWidth(line2));
            int textLeft = Math.max(left + 8, Math.min(center - infoWidth / 2, left + width - infoWidth - 8));
            int firstBaseline = aboveTank ? y - 18 : y + 16;
            int secondBaseline = aboveTank ? y : y + 35;
            g.setColor(new Color(0, 0, 0, 130));
            int cardTop = aboveTank ? firstBaseline - 16 : firstBaseline - 16;
            g.fillRoundRect(textLeft - 8, cardTop, infoWidth + 16, 39, 9, 9);
            g.setColor(GameTheme.TEXT);
            g.drawString(line1, textLeft, firstBaseline);
            g.setColor(GameTheme.MUTED);
            g.drawString(line2, textLeft, secondBaseline);
        }
        private static void drawTank(Graphics2D g, int x, int y, boolean up, Color color) {
            int center = mapX(x, 18, Math.max(1, g.getClipBounds().width - 36));
            g.setColor(color);
            g.fillRoundRect(center - 37, y + 14, 74, 42, 12, 12);
            g.fillRoundRect(center - 18, y + 3, 36, 34, 9, 9);
            g.fillRect(center - 5, up ? y - 22 : y + 34, 10, 33);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(center - 44, y + 51, 88, 12, 7, 7);
        }

        private record Impact(int x, int y, long createdAt) {
            long ageMillis() { return (System.nanoTime() - createdAt) / 1_000_000L; }
            double phase() { return ((x * 31L + y * 17L) % 360) * Math.PI / 180.0; }
        }
    }
}
