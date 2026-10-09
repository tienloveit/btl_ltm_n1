package tank.client.ui;

import tank.client.model.RoomState;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

public final class WaitingRoomPanel extends JPanel {
    private final JLabel player1 = label("", GameTheme.TEXT, 20);
    private final JLabel player2 = label("", GameTheme.TEXT, 20);
    private final JLabel roomLabel = label("", GameTheme.ACCENT, 14);
    private final JLabel countdown = label("", GameTheme.TEXT, 24);
    private final JLabel detail = label("", GameTheme.MUTED, 12);
    private final JButton exit = GameTheme.button("Thoát", false);
    private String currentRoomId;
    private boolean leaving;

    public WaitingRoomPanel(Runnable onExit) {
        super(new BorderLayout(0, 24));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(40, 24, 40, 24));
        add(label("PHÒNG ĐẤU", GameTheme.ACCENT, 23), BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridLayout(1, 3, 14, 0));
        columns.setOpaque(false);
        columns.add(playerCard("XE TĂNG 1", player1));
        JPanel center = GameTheme.card(new GridLayout(3, 1, 0, 14));
        center.setBorder(BorderFactory.createEmptyBorder(25, 12, 25, 12));
        center.add(roomLabel);
        center.add(countdown);
        center.add(detail);
        columns.add(center);
        columns.add(playerCard("XE TĂNG 2", player2));
        add(columns, BorderLayout.CENTER);
        exit.addActionListener(event -> {
            leaving = true;
            exit.setEnabled(false);
            onExit.run();
        });
        add(exit, BorderLayout.SOUTH);
    }

    public void setRoom(RoomState room) {
        if (!room.roomId().equals(currentRoomId)) {
            currentRoomId = room.roomId();
            leaving = false;
        }
        player1.setText(room.player1());
        player2.setText(room.player2());
        roomLabel.setText("Phòng " + room.roomId().substring(0, Math.min(8, room.roomId().length())));
        roomLabel.setToolTipText(room.roomId());
        countdown.setText(Integer.toString(room.secondsRemaining()));
        detail.setText("giây nữa bắt đầu");
        exit.setEnabled(!leaving);
    }

    public void showStarted(boolean integrated) {
        countdown.setText("Bắt đầu!");
        detail.setText(integrated ? "Đang chuyển vào game…" : "Chờ tích hợp gameplay");
    }

    public void setExitEnabled(boolean enabled) {
        leaving = !enabled;
        exit.setEnabled(enabled);
    }

    private static JPanel playerCard(String title, JLabel name) {
        JPanel card = GameTheme.card(new GridLayout(3, 1, 0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(25, 12, 25, 12));
        card.add(label(title, GameTheme.MUTED, 14));
        card.add(name);
        card.add(label("Sẵn sàng", GameTheme.ACCENT, 16));
        return card;
    }

    private static JLabel label(String text, java.awt.Color color, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(Font.BOLD, (float) size));
        return label;
    }
}
