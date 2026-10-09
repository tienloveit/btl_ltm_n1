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
    private final JTable table = new JTable(model);

    public OnlinePlayersPanel(String ownUsername, Consumer<String> onChallenge) {
        super(new BorderLayout(0, 16));
        this.ownUsername = ownUsername;
        this.onChallenge = onChallenge;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 22, 22));
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        JLabel heading = new JLabel("NGƯỜI CHƠI ONLINE");
        heading.setForeground(GameTheme.TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 17f));
        countLabel.setForeground(GameTheme.ACCENT);
        title.add(heading, BorderLayout.WEST);
        title.add(countLabel, BorderLayout.EAST);
        add(title, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setRowHeight(48);
        table.setFont(table.getFont().deriveFont(14f));
        table.setForeground(GameTheme.TEXT);
        table.setBackground(GameTheme.SURFACE);
        table.setSelectionBackground(GameTheme.SURFACE_LIGHT);
        table.setSelectionForeground(GameTheme.TEXT);
        table.setGridColor(GameTheme.BORDER);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(GameTheme.FIELD);
        table.getTableHeader().setForeground(GameTheme.MUTED);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setPreferredSize(new java.awt.Dimension(0, 42));
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                setBackground(selected ? GameTheme.SURFACE_LIGHT : row % 2 == 0 ? GameTheme.SURFACE : GameTheme.FIELD);
                setForeground(column == 2 ? ("ONLINE".equals(players.get(row).getStatus())
                        ? GameTheme.ACCENT : GameTheme.MUTED) : GameTheme.TEXT);
                setHorizontalAlignment(column == 1 ? SwingConstants.CENTER : SwingConstants.LEFT);
                return this;
            }
        };
        for (int column = 0; column < 3; column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(textRenderer);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(145);
        table.getColumnModel().getColumn(3).setPreferredWidth(145);
        table.getColumnModel().getColumn(3).setCellRenderer(new ChallengeRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new ChallengeEditor());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        scroll.getViewport().setBackground(GameTheme.SURFACE);
        add(scroll, BorderLayout.CENTER);
        countLabel.setText("0 đối thủ");
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

    private final class ChallengeRenderer implements TableCellRenderer {
        private final JButton button = GameTheme.button("Thách đấu", true);
        @Override public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            button.setEnabled(canChallenge(row));
            button.setText("ONLINE".equals(players.get(row).getStatus()) ? "Thách đấu" : "Đang bận");
            return button;
        }
    }

    private final class ChallengeEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = GameTheme.button("Thách đấu", true);
        private String target;
        ChallengeEditor() {
            button.addActionListener(event -> {
                String selected = target;
                fireEditingStopped();
                setRequesting(true);
                onChallenge.accept(selected);
            });
        }
        @Override public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            target = players.get(row).getUsername();
            return button;
        }
        @Override public Object getCellEditorValue() { return ""; }
    }
}
