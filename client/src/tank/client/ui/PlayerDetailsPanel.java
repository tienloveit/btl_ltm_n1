package tank.client.ui;

import tank.client.model.MatchHistoryEntry;
import tank.client.model.PlayerDetails;
import tank.client.model.PlayerProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

/** One modeless dialog's data; late responses never replace a different target. */
final class PlayerDetailsPanel extends JPanel {
    private final Consumer<String> request;
    private String target;
    private final JLabel name = label("", 26);
    private final JLabel[] values = new JLabel[6];
    private final JLabel message = label("", 14);
    private final JButton retry = GameTheme.button("Tải lại", false);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{
            "ID", "Thời gian", "Đối thủ", "Điểm", "Điểm đối thủ", "Kết quả"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final HomeTable history = new HomeTable(model, "Chưa có trận đấu",
            "Các trận gần nhất sẽ xuất hiện tại đây.");

    PlayerDetailsPanel(Consumer<String> request) {
        super(new BorderLayout(0, 12));
        this.request = request;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel heading = new JPanel(new BorderLayout(0, 12));
        heading.setOpaque(false);
        name.setForeground(HomeTheme.LIME);
        heading.add(name, BorderLayout.NORTH);
        JPanel stats = new JPanel(new GridLayout(2, 3, 10, 8));
        stats.setOpaque(false);
        String[] captions = {"Số trận", "Điểm", "Thắng", "Thua", "Hòa", "Trạng thái"};
        for (int i = 0; i < values.length; i++) {
            JPanel card = HomeTheme.card(new BorderLayout(0, 3));
            card.setBorder(BorderFactory.createEmptyBorder(6, 8, 9, 8));
            JLabel title = label(captions[i], 13);
            title.setForeground(HomeTheme.MUTED);
            String[] icons = {"matches", "score", "wins", "losses", "draws", "shield"};
            title.setIcon(new HomeIcon(icons[i], title, HomeTheme.MUTED, 15));
            title.setIconTextGap(6);
            values[i] = label("—", i == 5 ? 17 : 22);
            java.awt.Color[] tints = {HomeTheme.TEXT, HomeTheme.CYAN, HomeTheme.LIME,
                    HomeTheme.CORAL, HomeTheme.GOLD, HomeTheme.TEXT};
            values[i].setForeground(tints[i]);
            card.add(title, BorderLayout.NORTH);
            card.add(values[i], BorderLayout.CENTER);
            stats.add(card);
        }
        heading.add(stats, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);
        HomeTheme.table(history);
        history.getColumnModel().getColumn(5).setCellRenderer(new HomeTheme.BadgeCell());
        HomeTheme.button(retry, "history");
        history.getTableHeader().setReorderingAllowed(false);
        int[] widths = {50, 170, 210, 80, 110, 90};
        int[] minimumWidths = {40, 145, 120, 60, 80, 60};
        for (int i = 0; i < widths.length; i++) {
            history.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            history.getColumnModel().getColumn(i).setMinWidth(minimumWidths[i]);
        }
        JScrollPane scroll = new JScrollPane(history);
        scroll.setColumnHeaderView(history.getTableHeader());
        HomeTheme.scroll(scroll);
        add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        message.setForeground(HomeTheme.MUTED);
        footer.add(message, BorderLayout.CENTER);
        retry.addActionListener(event -> { if (target != null) { load(target); } });
        footer.add(retry, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    void load(String username) {
        target = username;
        name.setText(username);
        name.setIcon(new HomeIcon("avatar", name, null, Math.round(36 * name.getFont().getSize2D() / 26f), username));
        name.setIconTextGap(12);
        history.emptyMessage("Đang tải hồ sơ…", "Vui lòng đợi trong giây lát.");
        name.setToolTipText(username);
        for (JLabel value : values) { value.setText("—"); }
        model.setRowCount(0);
        message.setText("Đang tải hồ sơ và lịch sử đấu…");
        message.setForeground(HomeTheme.MUTED);
        message.setToolTipText(null);
        retry.setEnabled(false);
        request.accept(username);
    }

    void clearTarget() { target = null; }

    void showDetails(PlayerDetails details) {
        PlayerProfile profile = details.profile();
        if (target == null || !target.equalsIgnoreCase(profile.getUsername())) { return; }
        name.setText(profile.getUsername());
        history.emptyMessage("Chưa có trận đấu", "Lịch sử của người chơi sẽ xuất hiện tại đây.");
        int[] counts = {profile.getMatchesPlayed(), profile.getScore(), profile.getWins(),
                profile.getLosses(), profile.getDraws()};
        for (int i = 0; i < counts.length; i++) { values[i].setText(Integer.toString(counts[i])); }
        values[5].setText(OnlinePlayersPanel.statusText(profile.getStatus()));
        model.setRowCount(0);
        for (MatchHistoryEntry entry : details.history()) {
            model.addRow(new Object[]{entry.matchId(), entry.endedAt(), entry.opponent(),
                    entry.playerScore(), entry.opponentScore(), resultText(entry.result())});
        }
        message.setForeground(HomeTheme.MUTED);
        message.setText(details.history().isEmpty() ? "Chưa có trận đấu nào."
                : details.history().size() + " trận gần nhất • Kết quả của " + profile.getUsername());
        message.setToolTipText(message.getText());
        retry.setText("Tải lại");
        retry.setEnabled(true);
    }

    void showError(String username, String error) {
        if (target == null || !target.equalsIgnoreCase(username)) { return; }
        history.emptyMessage("Không thể tải hồ sơ", "Nhấn Thử lại để kết nối lại.");
        message.setText(error);
        message.setToolTipText(error);
        message.setForeground(new java.awt.Color(255, 150, 135));
        retry.setText("Thử lại");
        retry.setEnabled(true);
    }

    private static String resultText(String result) {
        return switch (result) { case "WIN" -> "Thắng"; case "LOSS" -> "Thua"; case "DRAW" -> "Hòa"; default -> result; };
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setForeground(HomeTheme.TEXT);
        label.setFont(new Font("Segoe UI", Font.BOLD, size));
        return label;
    }
}
