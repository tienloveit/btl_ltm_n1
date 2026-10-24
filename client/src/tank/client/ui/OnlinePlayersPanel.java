package tank.client.ui;

import tank.client.model.PlayerSummary;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class OnlinePlayersPanel extends JPanel {
    private final List<PlayerSummary> players = new ArrayList<>();
    private final String ownUsername;
    private final Consumer<String> onChallenge;
    private String ownStatus = "ONLINE";
    private boolean requesting;
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Tên người chơi", "Điểm", "Trạng thái", "Thao tác"}, 0) {
        @Override public boolean isCellEditable(int row, int column) {
            return column == 3 && canChallenge(row);
        }
    };
    private final JTable table = new HomeTable(model, "Chưa có đối thủ online",
            "Người chơi mới sẽ xuất hiện tại đây.");
    private final ChallengeRenderer challengeRenderer = new ChallengeRenderer();
    private final ChallengeEditor challengeEditor = new ChallengeEditor();

    public OnlinePlayersPanel(String ownUsername, Consumer<String> onChallenge) {
        this(ownUsername, onChallenge, username -> { });
    }

    public OnlinePlayersPanel(String ownUsername, Consumer<String> onChallenge, Consumer<String> onDetails) {
        super(new BorderLayout(0, 8));
        this.ownUsername = ownUsername;
        this.onChallenge = onChallenge;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        countLabel.setForeground(HomeTheme.MUTED);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 14f));
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(countLabel, BorderLayout.SOUTH);
        HomeTheme.table(table);
        table.setRowHeight(54);
        table.putClientProperty("home-name-column", 0);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.getColumnModel().getColumn(2).setCellRenderer(new HomeTheme.BadgeCell());
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(145);
        table.getColumnModel().getColumn(3).setPreferredWidth(145);
        table.getColumnModel().getColumn(3).setCellRenderer(challengeRenderer);
        table.getColumnModel().getColumn(3).setCellEditor(challengeEditor);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        HomeTheme.scroll(scroll);
        add(scroll, BorderLayout.CENTER);
        countLabel.setText("0 đối thủ");
        PlayerTableActions.install(table, 0, 3, onDetails);
        ((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(SwingConstants.CENTER);
    }

    void applyUiScale(float scale) {
        table.setRowHeight(Math.max(44, Math.round(54 * scale)));
        table.getColumnModel().getColumn(0).setPreferredWidth(Math.round(190 * scale));
        table.getColumnModel().getColumn(1).setPreferredWidth(Math.round(70 * scale));
        table.getColumnModel().getColumn(2).setPreferredWidth(Math.round(145 * scale));
        table.getColumnModel().getColumn(3).setPreferredWidth(Math.round(145 * scale));
    }

    public void setPlayers(List<PlayerSummary> roster) {
        if (table.isEditing()) { table.getCellEditor().cancelCellEditing(); }
        players.clear();
        ownStatus = "OFFLINE";
        for (PlayerSummary player : roster) {
            if (ownUsername.equalsIgnoreCase(player.getUsername())) { ownStatus = player.getStatus(); }
            else { players.add(player); }
        }
        model.setRowCount(0);
        for (PlayerSummary player : players) {
            model.addRow(new Object[]{player.getUsername(), player.getScore(), statusText(player.getStatus()), ""});
        }
        countLabel.setText(players.size() + " đối thủ");
    }

    public void setRequesting(boolean requesting) {
        this.requesting = requesting;
        table.repaint();
    }

    public static String statusText(String status) {
        return switch (status) {
            case "ONLINE" -> "Đang chờ";
            case "WAITING" -> "Đang chuẩn bị";
            case "PLAYING" -> "Đang chơi";
            default -> "Offline";
        };
    }

    private boolean canChallenge(int row) {
        return row >= 0 && row < players.size() && !requesting && "ONLINE".equals(ownStatus)
                && "ONLINE".equals(players.get(row).getStatus());
    }

    private JPanel challengeCell(JButton button) {
        HomeTheme.button(button, null);
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        JPanel cell = new JPanel(null) {
            @Override public void doLayout() {
                float scale = table.getFont().getSize2D() / 15f;
                int w = Math.min(getWidth() - 16, Math.round(116 * scale));
                int h = Math.min(getHeight() - 12, Math.round(34 * scale));
                button.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2,
                        Math.max(0, w), Math.max(0, h));
            }
        };
        cell.add(button);
        return cell;
    }

    private void configureChallengeCell(JPanel cell, JButton button, JTable source, int row, boolean selected) {
        cell.setBackground(HomeTheme.rowColor(source, row, selected));
        button.setFont(source.getFont().deriveFont(Font.BOLD, source.getFont().getSize2D() * .87f));
        cell.setSize(source.getColumnModel().getColumn(3).getWidth(), source.getRowHeight());
        cell.doLayout();
    }

    private final class ChallengeRenderer implements TableCellRenderer {
        private final JButton button = GameTheme.button("Thách đấu", true);
        private final JPanel cell = challengeCell(button);
        @Override public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            int modelRow = source.convertRowIndexToModel(row);
            configureChallengeCell(cell, button, source, row, selected);
            button.setEnabled(canChallenge(modelRow));
            button.setText("ONLINE".equals(players.get(modelRow).getStatus()) ? "Thách đấu" : "Đang bận");
            return cell;
        }
    }

    private final class ChallengeEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = GameTheme.button("Thách đấu", true);
        private final JPanel cell = challengeCell(button);
        private String target;
        ChallengeEditor() {
            button.addActionListener(event -> {
                if (requesting || target == null) { return; }
                button.setEnabled(false);
                String selected = target;
                fireEditingStopped();
                setRequesting(true);
                onChallenge.accept(selected);
            });
        }
        @Override public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            target = players.get(source.convertRowIndexToModel(row)).getUsername();
            configureChallengeCell(cell, button, source, row, selected);
            button.setEnabled(canChallenge(source.convertRowIndexToModel(row)));
            return cell;
        }
        @Override public Object getCellEditorValue() { return ""; }
    }
}
