package tank.client.ui;

import tank.client.controller.ClientController;
import tank.client.model.MatchHistoryEntry;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import tank.client.model.RankingEntry;
import tank.client.model.RoomState;
import tank.client.model.GameState;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public final class PlayerFrame extends JFrame {
    private final ClientController controller;
    private final OnlinePlayersPanel onlinePlayers;
    private final WaitingRoomPanel waitingRoom;
    private final GamePlayPanel gamePlay;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel message = new JLabel("Chọn đối thủ đang chờ để thách đấu.");
    private final JLabel status = new JLabel();
    private final JButton cancel = GameTheme.button("Hủy lời mời", false);
    private final JButton lobby = GameTheme.button("Lobby", false);
    private final JButton ranking = GameTheme.button("Xếp hạng", false);
    private final JButton history = GameTheme.button("Lịch sử đấu", false);
    private final JButton logout = GameTheme.button("Đăng xuất", false);
    private final JPanel rankingView = GameTheme.card(new BorderLayout(0, 12));
    private final JPanel historyView = GameTheme.card(new BorderLayout(0, 12));
    // Track pending requests to show loading state and re-enable on response/error
    private boolean rankingPending = false;
    private boolean historyPending = false;
    private PlayerProfile profile;
    private String challengeId;
    private String roomId;
    private ChallengeDialog challengeDialog;
    // Header stat labels kept for live update after each match
    private final JLabel scoreLabel = new JLabel();
    private final JLabel winsLabel = new JLabel();
    private final JLabel lossesLabel = new JLabel();
    private final JLabel drawsLabel = new JLabel();
    private final JPanel root;
    private final JPanel header;
    private final JPanel sidebar;
    private final JPanel footer;
    private boolean fullScreen;
    private boolean gameVisible;
    private GraphicsDevice fullScreenDevice;
    private Rectangle windowedBounds;
    private int windowedState;
    private Dimension windowedMinimumSize;

    public PlayerFrame(ClientController controller, PlayerProfile profile) {
        super("Tank Arena - Lobby");
        this.controller = controller;
        this.profile = profile;
        onlinePlayers = new OnlinePlayersPanel(profile.getUsername(), controller::challenge);
        waitingRoom = new WaitingRoomPanel(() -> {
            if (roomId != null) {
                controller.leaveRoom(roomId);
            }
        });
        gamePlay = new GamePlayPanel(profile.getUsername(), controller::moveLeft, controller::moveRight,
                controller::shoot, controller::playAgain, controller::exitGame,
                () -> setFullScreen(!fullScreen), () -> setFullScreen(false));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1000, 620);
        setMinimumSize(new Dimension(900, 540));
        setLocationRelativeTo(null);
        windowedBounds = getBounds();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent event) { rememberWindowedBounds(); }
            @Override public void componentResized(ComponentEvent event) { rememberWindowedBounds(); }
        });
        GameTheme.styleWindow(this);

        root = GameTheme.background(new BorderLayout(18, 18));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        header = GameTheme.card(new GridLayout(1, 5, 12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JButton name = GameTheme.button(profile.getUsername(), false);
        name.setToolTipText("Xem thông tin người chơi");
        name.addActionListener(event -> showProfile());
        header.add(name);
        header.add(stat("Điểm", profile.getScore(), scoreLabel));
        header.add(stat("Thắng", profile.getWins(), winsLabel));
        header.add(stat("Thua", profile.getLosses(), lossesLabel));
        header.add(stat("Hòa", profile.getDraws(), drawsLabel));
        root.add(header, BorderLayout.NORTH);

        sidebar = GameTheme.card(new BorderLayout(0, 20));
        sidebar.setPreferredSize(new Dimension(165, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        JPanel menu = new JPanel(new GridLayout(4, 1, 0, 12));
        menu.setOpaque(false);
        lobby.addActionListener(event -> showLobby());
        logout.addActionListener(event -> {
            selectNavigation(logout);
            controller.logout(false);
        });
        menu.add(lobby);
        menu.add(ranking);
        menu.add(history);
        menu.add(logout);
        sidebar.add(menu, BorderLayout.NORTH);
        status.setForeground(GameTheme.ACCENT);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        status.setText(OnlinePlayersPanel.statusText(profile.getStatus()));
        sidebar.add(status, BorderLayout.SOUTH);
        root.add(sidebar, BorderLayout.WEST);

        content.setOpaque(false);
        content.add(onlinePlayers, "lobby");
        content.add(rankingView, "ranking");
        content.add(historyView, "history");
        content.add(waitingRoom, "room");
        content.add(gamePlay, "game");
        root.add(content, BorderLayout.CENTER);
        footer = new JPanel(new BorderLayout(16, 0));
        footer.setOpaque(false);
        message.setForeground(GameTheme.MUTED);
        message.setFont(message.getFont().deriveFont(14f));
        cancel.setVisible(false);
        cancel.addActionListener(event -> {
            if (challengeId != null) {
                cancel.setEnabled(false);
                controller.cancelChallenge(challengeId);
            }
        });
        footer.add(message, BorderLayout.CENTER);
        footer.add(cancel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setNavigationHandlers(null, null);
        showLobby();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                controller.logout(true);
            }
        });
    }

    private void rememberWindowedBounds() {
        if (!fullScreen && getExtendedState() == NORMAL && isShowing()) {
            windowedBounds = getBounds();
        }
    }

    private void setFullScreen(boolean enabled) {
        if (enabled == fullScreen || (enabled && !gameVisible)) {
            return;
        }
        boolean wasVisible = isVisible();
        if (enabled) {
            rememberWindowedBounds();
            windowedState = getExtendedState();
            windowedMinimumSize = getMinimumSize();
            fullScreenDevice = getGraphicsConfiguration().getDevice();
            fullScreen = true;
            // Recreate the native peer without invoking application logout/disposal.
            super.dispose();
            setExtendedState(NORMAL);
            setUndecorated(true);
            setMinimumSize(new Dimension(0, 0));
            setLobbyChromeVisible(false);
            fullScreenDevice.setFullScreenWindow(this);
        } else {
            fullScreenDevice.setFullScreenWindow(null);
            fullScreenDevice = null;
            super.dispose();
            setUndecorated(false);
            setMinimumSize(windowedMinimumSize);
            setLobbyChromeVisible(true);
            setBounds(windowedBounds);
            setExtendedState(windowedState);
            fullScreen = false;
        }
        gamePlay.setFullScreen(enabled);
        setVisible(wasVisible);
        validate();
        gamePlay.requestFocusInWindow();
    }

    private void setLobbyChromeVisible(boolean visible) {
        header.setVisible(visible);
        sidebar.setVisible(visible);
        footer.setVisible(visible);
        int padding = visible ? 20 : 0;
        root.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        BorderLayout layout = (BorderLayout) root.getLayout();
        layout.setHgap(visible ? 18 : 0);
        layout.setVgap(visible ? 18 : 0);
    }

    private static JPanel stat(String caption, int value, JLabel number) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel title = new JLabel(caption);
        title.setForeground(GameTheme.MUTED);
        title.setFont(title.getFont().deriveFont(14f));
        number.setText(Integer.toString(value));
        number.setForeground(GameTheme.TEXT);
        number.setFont(number.getFont().deriveFont(Font.BOLD, 25f));
        panel.add(title, BorderLayout.NORTH);
        panel.add(number, BorderLayout.CENTER);
        return panel;
    }

    private void showProfile() {
        JDialog dialog = new JDialog(this, "Thông tin người chơi", false);
        PlayerInformationPanel information = new PlayerInformationPanel();
        information.setProfile(profile);
        JPanel root = GameTheme.background(new BorderLayout());
        root.add(information);
        dialog.setContentPane(root);
        dialog.setSize(660, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void setNavigationHandlers(Runnable onRanking, Runnable onHistory) {
        configureNavigation(ranking, onRanking == null ? null : () -> {
            if (!rankingPending) {
                rankingPending = true;
                ranking.setEnabled(false);
                ranking.setText("Đang tải…");
                showLoading(rankingView, "Bảng xếp hạng", "Đang tải dữ liệu bảng xếp hạng...");
                selectNavigation(ranking);
                cards.show(content, "ranking");
                onRanking.run();
            }
        });
        configureNavigation(history, onHistory == null ? null : () -> {
            if (!historyPending) {
                historyPending = true;
                history.setEnabled(false);
                history.setText("Đang tải…");
                showLoading(historyView, "Lịch sử đấu", "Đang tải lịch sử trận đấu...");
                selectNavigation(history);
                cards.show(content, "history");
                onHistory.run();
            }
        });
    }

    private void showLobby() {
        selectNavigation(lobby);
        cards.show(content, "lobby");
    }

    private void selectNavigation(JButton selected) {
        GameTheme.setNavigationSelected(lobby, selected == lobby);
        GameTheme.setNavigationSelected(ranking, selected == ranking);
        GameTheme.setNavigationSelected(history, selected == history);
        GameTheme.setNavigationSelected(logout, selected == logout);
    }

    private static void configureNavigation(JButton button, Runnable action) {
        for (java.awt.event.ActionListener listener : button.getActionListeners()) {
            button.removeActionListener(listener);
        }
        button.setEnabled(action != null);
        button.setToolTipText(action == null ? "Chưa tích hợp" : null);
        if (action != null) {
            button.addActionListener(event -> action.run());
        }
    }

    public void updatePlayers(List<PlayerSummary> players) {
        onlinePlayers.setPlayers(players);
        for (PlayerSummary player : players) {
            if (profile.getUsername().equalsIgnoreCase(player.getUsername())) {
                profile = profile.withStatus(player.getStatus());
                status.setText(OnlinePlayersPanel.statusText(player.getStatus()));
                break;
            }
        }
    }

    public void showPending(String id, String opponent, int timeoutSeconds) {
        challengeId = id;
        onlinePlayers.setRequesting(false);
        showMessage("Đang chờ " + opponent + " phản hồi (tối đa " + timeoutSeconds + " giây).", false);
        cancel.setVisible(true);
        cancel.setEnabled(true);
    }

    public void showInvitation(String id, String opponent, int timeoutSeconds) {
        closeInvitation();
        challengeId = id;
        onlinePlayers.setRequesting(false);
        challengeDialog = new ChallengeDialog(this, opponent, timeoutSeconds,
                () -> controller.acceptChallenge(id), () -> controller.rejectChallenge(id));
        challengeDialog.setVisible(true);
        showMessage(opponent + " đã gửi lời mời thách đấu.", false);
    }

    public void closeChallenge(String id, String reason) {
        if (!id.equals(challengeId)) {
            return;
        }
        challengeId = null;
        closeInvitation();
        cancel.setVisible(false);
        onlinePlayers.setRequesting(false);
        showMessage("ACCEPTED".equals(reason) ? "Đã chấp nhận. Đang vào phòng…" : reason, false);
    }

    public void showRoom(RoomState room) {
        setFullScreen(false);
        gameVisible = false;
        closeInvitation();
        challengeId = null;
        roomId = room.roomId();
        cancel.setVisible(false);
        lobby.setEnabled(false);
        waitingRoom.setRoom(room);
        cards.show(content, "room");
        selectNavigation(null);
        showMessage("Hai người đã sẵn sàng. Chờ Server bắt đầu trận.", false);
    }

    public void showMatchStarted(boolean integrated) {
        waitingRoom.showStarted(integrated);
        showMessage(integrated ? "Trận đấu đã bắt đầu." : "Trận đấu đã bắt đầu; gameplay chưa được tích hợp.", false);
    }

    public void showGame(String id, String player1, String player2) {
        gameVisible = true;
        roomId = id;
        gamePlay.start(id, player1, player2);
        cards.show(content, "game");
        selectNavigation(null);
        gamePlay.requestFocusInWindow();
    }

    public void updateGame(GameState state) {
        if (state.roomId().equals(roomId)) {
            gamePlay.setState(state);
        }
    }

    public void showGameOver(String id, String winner, String reason) {
        if (id.equals(roomId)) {
            gamePlay.gameOver(winner, reason);
        }
    }

    public void showHitEffect(String id, int x, int y) {
        if (id.equals(roomId)) {
            gamePlay.showImpact(x, y);
        }
    }

    public void closeRoom(String id, String reason) {
        if (!id.equals(roomId)) {
            return;
        }
        setFullScreen(false);
        gameVisible = false;
        roomId = null;
        lobby.setEnabled(true);
        onlinePlayers.setRequesting(false);
        showLobby();
        showMessage(reason, false);
    }

    public void showLobbyError(String reason) {
        onlinePlayers.setRequesting(false);
        cancel.setEnabled(true);
        waitingRoom.setExitEnabled(true);
        if (challengeDialog != null) {
            challengeDialog.setBusy(false);
        }
        // Re-enable ranking/history buttons if a pending request failed
        if (rankingPending) {
            rankingPending = false;
            ranking.setEnabled(true);
            ranking.setText("Xếp hạng");
        }
        if (historyPending) {
            historyPending = false;
            history.setEnabled(true);
            history.setText("Lịch sử đấu");
        }
        showMessage(reason, true);
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setToolTipText(text);
        message.setForeground(error ? new java.awt.Color(255, 133, 117) : GameTheme.MUTED);
    }

    private void closeInvitation() {
        if (challengeDialog != null) {
            challengeDialog.dispose();
            challengeDialog = null;
        }
    }

    /** Called after each match to refresh the header stat counters. */
    public void updateProfile(PlayerProfile updated) {
        this.profile = updated;
        scoreLabel.setText(Integer.toString(updated.getScore()));
        winsLabel.setText(Integer.toString(updated.getWins()));
        lossesLabel.setText(Integer.toString(updated.getLosses()));
        drawsLabel.setText(Integer.toString(updated.getDraws()));
    }

    /** Displays the ranking table inside the lobby content area. */
    public void showRanking(List<RankingEntry> entries) {
        rankingPending = false;
        ranking.setEnabled(true);
        ranking.setText("Xếp hạng");

        String[] columns = {"#", "Tên", "Trận", "Điểm", "Thắng", "Thua", "Hòa"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        int rank = 1;
        for (RankingEntry e : entries) {
            model.addRow(new Object[]{rank++, e.username(), e.matchesPlayed(),
                    e.score(), e.wins(), e.losses(), e.draws()});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        GameTheme.styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        scroll.getViewport().setBackground(GameTheme.SURFACE);
        scroll.setBackground(GameTheme.SURFACE);
        showData(rankingView, "Bảng xếp hạng (" + entries.size() + " người chơi)", scroll);
        selectNavigation(ranking);
        cards.show(content, "ranking");
    }

    /** Displays the match history table inside the lobby content area. */
    public void showHistory(List<MatchHistoryEntry> entries) {
        historyPending = false;
        history.setEnabled(true);
        history.setText("Lịch sử đấu");

        String[] columns = {"ID", "Thời gian", "Đối thủ", "Điểm tôi", "Điểm đối thủ", "Kết quả"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        for (MatchHistoryEntry e : entries) {
            model.addRow(new Object[]{e.matchId(), e.endedAt(), e.opponent(),
                    e.playerScore(), e.opponentScore(), translateResult(e.result())});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        GameTheme.styleTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        scroll.getViewport().setBackground(GameTheme.SURFACE);
        scroll.setBackground(GameTheme.SURFACE);
        String subtitle = entries.isEmpty() ? "Chưa có trận đấu nào." : entries.size() + " trận gần nhất";
        showData(historyView, "Lịch sử đấu — " + subtitle, scroll);
        selectNavigation(history);
        cards.show(content, "history");
    }

    private static void showLoading(JPanel view, String title, String text) {
        JLabel message = new JLabel(text, JLabel.CENTER);
        message.setForeground(GameTheme.MUTED);
        showData(view, title, message);
    }

    private static void showData(JPanel view, String titleText, java.awt.Component body) {
        view.removeAll();
        view.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JLabel title = new JLabel(titleText);
        title.setForeground(GameTheme.ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        view.add(title, BorderLayout.NORTH);
        view.add(body, BorderLayout.CENTER);
        view.revalidate();
        view.repaint();
    }

    private static String translateResult(String result) {
        return switch (result == null ? "" : result) {
            case "WIN"  -> "Thắng";
            case "LOSS" -> "Thua";
            case "DRAW" -> "Hòa";
            default     -> result;
        };
    }

    @Override
    public void dispose() {
        if (fullScreenDevice != null) {
            fullScreenDevice.setFullScreenWindow(null);
            fullScreenDevice = null;
        }
        fullScreen = false;
        closeInvitation();
        super.dispose();
    }
}
