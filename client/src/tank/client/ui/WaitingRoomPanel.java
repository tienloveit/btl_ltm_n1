package tank.client.ui;

import tank.client.model.RoomState;
import tank.client.audio.SoundEffect;
import tank.client.audio.SoundPlayer;
import tank.client.audio.MusicScene;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.awt.Component;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.util.Objects;
import java.util.function.Consumer;

public final class WaitingRoomPanel extends JPanel {
    private final String localPlayer;
    private final Consumer<String> onReady;
    private final SoundPlayer audio;
    private final JLabel player1 = label("", HomeTheme.TEXT, 20);
    private final JLabel player2 = label("", HomeTheme.TEXT, 20);
    private final JLabel player1Ready = statusLabel();
    private final JLabel player2Ready = statusLabel();
    private final JLabel player1Role = label("", HomeTheme.CYAN, 14);
    private final JLabel player2Role = label("", HomeTheme.CYAN, 14);
    private final JLabel roomLabel = label("", HomeTheme.LIME, 14);
    private final JLabel countdown = label("", HomeTheme.TEXT, 42);
    private final JLabel detail = label("", HomeTheme.MUTED, 12);
    private final TimerDial dial = new TimerDial();
    private final Timer pulseTimer;
    private final JButton ready = GameTheme.button("TÔI SẴN SÀNG", true);
    private final JButton exit = GameTheme.button("Thoát", false);
    private String currentRoomId;
    private boolean leaving;
    private boolean readyPending;
    private boolean localReady;

    public WaitingRoomPanel(String localPlayer, Consumer<String> onReady, Runnable onExit) {
        this(localPlayer, onReady, onExit, SoundPlayer.SILENT);
    }

    public WaitingRoomPanel(String localPlayer, Consumer<String> onReady, Runnable onExit, SoundPlayer audio) {
        super(new BorderLayout(0, 20));
        this.localPlayer = Objects.requireNonNull(localPlayer);
        this.onReady = Objects.requireNonNull(onReady);
        this.audio = Objects.requireNonNull(audio);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 24));
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = label("PHÒNG ĐẤU", HomeTheme.LIME, 23);
        title.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel hint = label("Hai chiến binh đã vào phòng. Sẵn sàng để bắt đầu sớm.", HomeTheme.MUTED, 12);
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        heading.add(title, BorderLayout.NORTH);
        heading.add(hint, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridLayout(1, 3, 14, 0));
        columns.setOpaque(false);
        columns.add(playerCard(player1Role, player1, player1Ready,
                new TankPortrait(HomeTheme.LIME, true)));
        JPanel center = HomeTheme.card(new BorderLayout(0, 4));
        center.setBorder(BorderFactory.createEmptyBorder(23, 12, 25, 12));
        center.add(roomLabel, BorderLayout.NORTH);
        dial.setLayout(new BorderLayout());
        dial.setOpaque(false);
        dial.add(countdown, BorderLayout.CENTER);
        center.add(dial, BorderLayout.CENTER);
        center.add(detail, BorderLayout.SOUTH);
        columns.add(center);
        columns.add(playerCard(player2Role, player2, player2Ready,
                new TankPortrait(HomeTheme.CORAL, false)));
        add(columns, BorderLayout.CENTER);

        HomeTheme.button(ready, "shield");
        HomeTheme.button(exit, "logout");
        ready.addActionListener(event -> {
            if (currentRoomId == null || localReady || readyPending || leaving) { return; }
            readyPending = true;
            ready.setText("ĐANG GỬI…");
            ready.setEnabled(false);
            statusForLocalPlayer("Đang gửi yêu cầu…");
            onReady.accept(currentRoomId);
        });
        exit.addActionListener(event -> {
            audio.stopEffects();
            leaving = true;
            exit.setEnabled(false);
            ready.setEnabled(false);
            if (currentRoomId != null) { onExit.run(); }
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);
        actions.add(ready);
        JButton settings = SoundControls.button(audio);
        HomeTheme.button(settings, "settings");
        actions.add(settings);
        actions.add(exit);
        add(actions, BorderLayout.SOUTH);
        pulseTimer = new Timer(30, event -> {
            dial.repaint();
            if (System.nanoTime() - dial.pulseStarted > 500_000_000L) {
                ((Timer) event.getSource()).stop();
                dial.repaint();
            }
        });
    }

    public void setRoom(RoomState room) {
        if (!room.roomId().equals(currentRoomId)) {
            audio.stopEffects();
            currentRoomId = room.roomId();
            leaving = false;
            readyPending = false;
            dial.seconds = -1;
            dial.pulseStarted = 0;
            pulseTimer.stop();
        }
        localReady = room.isReady(localPlayer);
        if (localReady) { readyPending = false; }
        player1Role.setText(localPlayer.equals(room.player1()) ? "BẠN" : "ĐỐI THỦ");
        player2Role.setText(localPlayer.equals(room.player2()) ? "BẠN" : "ĐỐI THỦ");
        player1.setToolTipText(room.player1());
        player2.setToolTipText(room.player2());
        player1.setText(room.player1());
        player2.setText(room.player2());
        setPlayerStatus(player1Ready, room.player1Ready());
        setPlayerStatus(player2Ready, room.player2Ready());
        roomLabel.setText("Phòng " + room.roomId().substring(0, Math.min(8, room.roomId().length())));
        roomLabel.setToolTipText(room.roomId());
        int previous = dial.seconds;
        countdown.setText(Integer.toString(room.secondsRemaining()));
        dial.seconds = room.secondsRemaining();
        if (!leaving) { audio.setMusicScene(dial.seconds <= 3 ? MusicScene.COUNTDOWN : MusicScene.LOBBY); }
        if (!leaving && dial.seconds >= 1 && dial.seconds <= 10 && dial.seconds != previous) {
            audio.play(SoundEffect.COUNTDOWN_TICK);
        }
        if (previous > 3 && dial.seconds == 3 && room.player1Ready() && room.player2Ready()) {
            dial.pulseStarted = System.nanoTime();
            pulseTimer.restart();
        }
        dial.repaint();
        detail.setText(room.player1Ready() && room.player2Ready()
                ? "Cả hai đã sẵn sàng!" : "giây nữa bắt đầu");
        ready.setText(localReady ? "ĐÃ SẴN SÀNG" : readyPending ? "ĐANG GỬI…" : "TÔI SẴN SÀNG");
        ready.setEnabled(!leaving && !localReady && !readyPending);
        exit.setEnabled(!leaving);
    }

    public void showStarted(boolean integrated) {
        pulseTimer.stop();
        ready.setEnabled(false);
        exit.setEnabled(false);
        countdown.setText("Bắt đầu!");
        detail.setText(integrated ? "Đang chuyển vào game…" : "Chờ tích hợp gameplay");
    }

    public void setExitEnabled(boolean enabled) {
        leaving = !enabled;
        exit.setEnabled(enabled);
        ready.setEnabled(enabled && !localReady && !readyPending);
    }

    /** Allows a failed READY request to be retried. */
    public void readyRequestFailed() {
        readyPending = false;
        ready.setText(localReady ? "ĐÃ SẴN SÀNG" : "TÔI SẴN SÀNG");
        ready.setEnabled(currentRoomId != null && !leaving && !localReady);
        if (!localReady) { statusForLocalPlayer("Đang chờ sẵn sàng"); }
    }

    private void statusForLocalPlayer(String text) {
        if (currentRoomId == null) { return; }
        // Player order is stable for the lifetime of the room.
        JLabel localStatus = localPlayer.equals(player1.getText()) ? player1Ready : player2Ready;
        localStatus.setText(text);
        localStatus.setForeground(localReady ? HomeTheme.LIME : HomeTheme.MUTED);
    }

    void applyUiScale(float scale) {
        ResponsiveUi.apply(this, scale);
        dial.uiScale = scale;
        dial.repaint();
    }

    private static JPanel playerCard(JLabel cardTitle, JLabel name, JLabel status, TankPortrait portrait) {
        JPanel card = HomeTheme.card(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 12));
        cardTitle.setIcon(new HomeIcon("shield", cardTitle, HomeTheme.CYAN, 17));
        cardTitle.setIconTextGap(8);
        card.add(cardTitle, BorderLayout.NORTH);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 22f));
        portrait.setAlignmentX(Component.CENTER_ALIGNMENT);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        identity.add(Box.createVerticalGlue());
        identity.add(portrait);
        identity.add(Box.createRigidArea(new Dimension(0, 3)));
        identity.add(name);
        identity.add(Box.createVerticalGlue());
        card.add(identity, BorderLayout.CENTER);
        card.add(status, BorderLayout.SOUTH);
        return card;
    }

    @Override public void removeNotify() {
        pulseTimer.stop();
        super.removeNotify();
    }

    private final class TimerDial extends JPanel {
        private int seconds = -1;
        private long pulseStarted;
        private float uiScale = 1f;
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.min(Math.round(156 * uiScale), Math.min(getWidth() - 12, getHeight() - 6));
            if (diameter > 12) {
                int x = (getWidth() - diameter) / 2, y = (getHeight() - diameter) / 2;
                long pulseAge = (System.nanoTime() - pulseStarted) / 1_000_000L;
                boolean pulse = pulseStarted != 0 && pulseAge < 500;
                g.setColor(pulse ? new Color(166, 232, 91, 35) : new Color(83, 214, 213, 18));
                g.fillOval(x + 5, y + 5, diameter - 10, diameter - 10);
                g.setStroke(new BasicStroke(6f * uiScale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(HomeTheme.EDGE);
                g.drawArc(x + 4, y + 4, diameter - 8, diameter - 8, 0, 360);
                g.setColor(pulse ? HomeTheme.LIME : HomeTheme.CYAN);
                g.drawArc(x + 4, y + 4, diameter - 8, diameter - 8, 90,
                        -Math.round(360f * Math.max(0, Math.min(10, seconds)) / 10f));
                g.setStroke(new BasicStroke(Math.max(1, uiScale)));
                g.setColor(HomeTheme.MUTED);
                for (int tick = 0; tick < 20; tick++) {
                    double angle = tick * Math.PI / 10;
                    double radius = diameter / 2.0;
                    int cx = x + diameter / 2, cy = y + diameter / 2;
                    g.drawLine(cx + (int) (Math.cos(angle) * (radius - 13)),
                            cy + (int) (Math.sin(angle) * (radius - 13)),
                            cx + (int) (Math.cos(angle) * (radius - 17)),
                            cy + (int) (Math.sin(angle) * (radius - 17)));
                }
                if (pulse) {
                    int extra = (int) (pulseAge / 20);
                    g.setColor(new Color(166, 232, 91, Math.max(0, 110 - (int) pulseAge / 5)));
                    g.setStroke(new BasicStroke(2f));
                    g.drawOval(x - extra, y - extra, diameter + extra * 2, diameter + extra * 2);
                }
            }
            g.dispose();
        }
    }

    private static void setPlayerStatus(JLabel status, boolean isReady) {
        status.setText(isReady ? "ĐÃ SẴN SÀNG" : "Đang chờ sẵn sàng");
        status.setForeground(isReady ? HomeTheme.LIME : HomeTheme.MUTED);
    }

    private static JLabel statusLabel() { return HomeTheme.badge("Đang chờ sẵn sàng"); }

    private static JLabel label(String text, java.awt.Color color, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        label.setFont(new Font("Segoe UI", Font.BOLD, Math.round(size * 1.1f)));
        return label;
    }
}
