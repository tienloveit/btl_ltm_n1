package tank.client.ui;

import tank.client.model.MatchHistoryEntry;
import tank.client.model.PlayerDetails;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class PlayerDetailsUiCheck {
    private static int checks;

    static int run() throws Exception {
        AtomicReference<String> requested = new AtomicReference<>();
        PlayerDetailsPanel panel = new PlayerDetailsPanel(requested::set);
        JTable history = LobbyUiCheck.find(panel, JTable.class);
        panel.load("alice");
        check("alice".equals(requested.get()), "opening a profile requests the selected username");
        panel.showDetails(details("alice", "ONLINE", List.of(
                new MatchHistoryEntry(7, "23/10/2026 10:30", "bob", 1, 2, "LOSS"))));
        check(history.getRowCount() == 1 && history.getValueAt(0, 3).equals(1)
                && history.getValueAt(0, 5).equals("Thua"), "history is displayed from the selected player's perspective");
        panel.load("bob");
        panel.showDetails(details("alice", "ONLINE", List.of(
                new MatchHistoryEntry(7, "23/10/2026 10:30", "bob", 1, 2, "LOSS"))));
        panel.showError("alice", "late error");
        check(history.getRowCount() == 0 && LobbyUiCheck.findLabel(panel, "late error") == null,
                "late response and error cannot overwrite the next player's profile");
        panel.showDetails(details("bob", "OFFLINE", List.of()));
        check(LobbyUiCheck.findLabel(panel, "Offline") != null
                && LobbyUiCheck.findLabel(panel, "Chưa có trận đấu nào.") != null,
                "offline players and empty history have explicit states");
        panel.showError("bob", "Không thể tải dữ liệu");
        LobbyUiCheck.findButton(panel, "Thử lại").doClick();
        check("bob".equals(requested.get()) && !LobbyUiCheck.findButton(panel, "Thử lại").isEnabled(),
                "retry reloads the current player and disables duplicate requests");
        panel.clearTarget();
        panel.showDetails(details("bob", "ONLINE", List.of()));
        check(LobbyUiCheck.findLabel(panel, "Đang chờ") == null, "closed profiles ignore responses");
        String longName = "abcdefghijklmnopqrstuvwx";
        panel.load(longName);
        panel.showDetails(details(longName, "WAITING", List.of(
                new MatchHistoryEntry(8, "23/10/2026 10:30", "opponent_with_long_name", 2, 1, "WIN"))));
        JLabel cell = (JLabel) history.prepareRenderer(history.getCellRenderer(0, 2), 0, 2);
        check(cell.getHorizontalAlignment() == SwingConstants.CENTER
                && cell.getVerticalAlignment() == SwingConstants.CENTER, "history cell text is centered in both directions");
        JPanel background = GameTheme.background(new BorderLayout());
        background.putClientProperty("home-background", Boolean.TRUE);
        background.add(panel);
        ResponsiveUi.apply(background, 1f);
        LobbyUiCheck.capture(background, "player-details", 860, 620);
        check(history.getTableHeader().getParent() != null && history.getTableHeader().getHeight() > 0,
                "history column headers are visible in the details window");
        LobbyUiCheck.capture(background, "player-details-small", 640, 420);
        ResponsiveUi.apply(background, 1.25f);
        LobbyUiCheck.capture(background, "player-details-large", 1075, 775);

        AtomicReference<String> challenged = new AtomicReference<>();
        OnlinePlayersPanel lobby = new OnlinePlayersPanel("alice", challenged::set, requested::set);
        lobby.setPlayers(List.of(new PlayerSummary("alice", 1, "ONLINE"),
                new PlayerSummary("bob", 2, "ONLINE"), new PlayerSummary("carol", 3, "ONLINE")));
        lobby.setSize(900, 400);
        LobbyUiCheck.layout(lobby);
        JTable table = LobbyUiCheck.find(lobby, JTable.class);
        JLabel nameCell = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        check(nameCell.getHorizontalAlignment() == SwingConstants.CENTER,
                "lobby usernames use the same centered alignment as column headers");
        click(table, 0, 0);
        check("bob".equals(requested.get()) && challenged.get() == null,
                "single row click opens details without issuing a challenge");
        requested.set(null);
        click(table, 0, 3);
        check(requested.get() == null, "challenge column never opens a profile");
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        table.getRowSorter().toggleSortOrder(0);
        click(table, 0, 0);
        check("carol".equals(requested.get()), "profile actions resolve the model row after sorting");
        return checks;
    }

    private static PlayerDetails details(String username, String status, List<MatchHistoryEntry> history) {
        return new PlayerDetails(new PlayerProfile(username, 3, 4, 1, 1, 1, status), history);
    }

    private static void click(JTable table, int row, int column) {
        Rectangle rect = table.getCellRect(row, column, true);
        table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, rect.x + rect.width / 2, rect.y + rect.height / 2, 1, false, MouseEvent.BUTTON1));
    }

    private static void check(boolean valid, String message) {
        if (!valid) { throw new AssertionError(message); }
        checks++;
    }
}
