package tank.client.ui;

import tank.client.controller.ClientController;
import tank.client.model.MatchHistoryEntry;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerDetails;
import tank.client.model.PlayerSummary;
import tank.client.model.RankingEntry;
import tank.client.model.RoomState;
import tank.client.model.GameState;
import tank.client.model.MatchResultStats;
import tank.client.audio.GameAudio;
import tank.client.audio.SoundPlayer;
import tank.client.audio.MusicScene;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public final class PlayerFrame extends JFrame {
    private final ClientController controller;
    private final SoundPlayer audio;
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
    private final JPanel rankingView = HomeTheme.card(new BorderLayout(0, 12));
    private final JPanel historyView = HomeTheme.card(new BorderLayout(0, 12));
    // Track pending requests to show loading state and re-enable on response/error
    private boolean rankingPending = false;
    private boolean historyPending = false;
    private PlayerProfile profile;
    private String challengeId;
    private String roomId;
    private ChallengeDialog challengeDialog;
    private JDialog detailsDialog;
    private PlayerDetailsPanel detailsPanel;
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
    private String activeCard = "lobby";
    private GraphicsDevice fullScreenDevice;
    private Rectangle windowedBounds;
    private int windowedState;
    private Dimension windowedMinimumSize;
    private float uiScale = 1f;

    public PlayerFrame(ClientController controller, PlayerProfile profile) {
        this(controller, profile, new GameAudio());
    }

    PlayerFrame(ClientController controller, PlayerProfile profile, SoundPlayer audio) {
        super("Tank Arena - Lobby");
        this.controller = controller;
        this.audio = audio;
        this.profile = profile;
        rankingView.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        historyView.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        onlinePlayers = new OnlinePlayersPanel(profile.getUsername(), controller::challenge, this::openPlayerDetails);
        waitingRoom = new WaitingRoomPanel(profile.getUsername(), controller::readyRoom, () -> {
            if (roomId != null) {
                controller.leaveRoom(roomId);
            }
        }, audio);
        gamePlay = new GamePlayPanel(profile.getUsername(), controller::moveLeft, controller::moveRight,
                controller::shoot, controller::playAgain, controller::exitGame,
                () -> setFullScreen(!fullScreen), () -> setFullScreen(false), audio);
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
        header = HomeTheme.card(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel name = new JLabel(profile.getUsername(), javax.swing.SwingConstants.CENTER);
        name.setForeground(HomeTheme.TEXT);
        name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        name.setIcon(new HomeIcon("tank", name, HomeTheme.LIME, 28));
        name.setIconTextGap(10);
        name.setPreferredSize(new Dimension(210, 0));
        header.add(name, BorderLayout.WEST);
        JPanel counters = new JPanel(new GridLayout(1, 4, 12, 0));
        counters.setOpaque(false);
        header.add(counters, BorderLayout.CENTER);
        counters.add(stat("Điểm", profile.getScore(), scoreLabel, HomeTheme.CYAN));
        counters.add(stat("Thắng", profile.getWins(), winsLabel, HomeTheme.LIME));
        counters.add(stat("Thua", profile.getLosses(), lossesLabel, HomeTheme.CORAL));
        counters.add(stat("Hòa", profile.getDraws(), drawsLabel, HomeTheme.GOLD));
        root.add(header, BorderLayout.NORTH);

        sidebar = HomeTheme.card(new BorderLayout(0, 20));
        sidebar.setPreferredSize(new Dimension(165, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        HomeTheme.button(lobby, "lobby");
        HomeTheme.button(ranking, "trophy");
        HomeTheme.button(history, "history");
        HomeTheme.button(logout, "logout");
        HomeTheme.button(cancel, null);
        JPanel menu = new JPanel(new GridLayout(4, 1, 0, 12));
        menu.setOpaque(false);
        lobby.addActionListener(event -> showLobby());
        logout.addActionListener(event -> {
            closePlayerDetails();
            selectNavigation(logout);
            controller.logout(false);
        });
        menu.add(lobby);
        menu.add(ranking);
        menu.add(history);
        menu.add(logout);
        sidebar.add(menu, BorderLayout.NORTH);
        status.setForeground(HomeTheme.LIME);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
        status.setText(OnlinePlayersPanel.statusText(profile.getStatus()));
        JPanel sidebarStatus = new JPanel(new BorderLayout(0, 12));
        sidebarStatus.setOpaque(false);
        sidebarStatus.add(status, BorderLayout.NORTH);
        JButton settings = SoundControls.button(audio);
        HomeTheme.button(settings, "settings");
        sidebarStatus.add(settings, BorderLayout.SOUTH);
        sidebar.add(sidebarStatus, BorderLayout.SOUTH);
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
        message.setForeground(HomeTheme.MUTED);
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
                closePlayerDetails();
                controller.logout(true);
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { refreshResponsiveUi(); }
        });
        refreshResponsiveUi();
    }

    void refreshResponsiveUi() {
        uiScale = ResponsiveUi.scaleFor(this);
        root.putClientProperty("home-background", isHomeView() || "room".equals(activeCard));
        ResponsiveUi.apply(root, uiScale);
        onlinePlayers.applyUiScale(uiScale);
        waitingRoom.applyUiScale(uiScale);
        setLobbyChromeVisible(isHomeView() && !fullScreen);
        root.revalidate();
        root.repaint();
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
            setLobbyChromeVisible(isHomeView());
            setBounds(windowedBounds);
            setExtendedState(windowedState);
            fullScreen = false;
        }
        gamePlay.setFullScreen(enabled);
        setVisible(wasVisible);
        refreshResponsiveUi();
        validate();
        gamePlay.requestFocusInWindow();
    }

    private boolean isHomeView() {
        return "lobby".equals(activeCard) || "ranking".equals(activeCard) || "history".equals(activeCard);
    }

    private void setLobbyChromeVisible(boolean visible) {
        header.setVisible(visible);
        sidebar.setVisible(visible);
        footer.setVisible(visible);
        int padding = visible ? Math.round(20 * uiScale) : 0;
        root.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        BorderLayout layout = (BorderLayout) root.getLayout();
        layout.setHgap(visible ? Math.round(18 * uiScale) : 0);
        layout.setVgap(visible ? Math.round(18 * uiScale) : 0);
        root.revalidate();
    }

    private void showContent(String card) {
        activeCard = card;
        if (!"game".equals(card) && !"room".equals(card)) { audio.setMusicScene(MusicScene.LOBBY); }
        cards.show(content, card);
        refreshResponsiveUi();
    }

    private static JPanel stat(String caption, int value, JLabel number, Color tint) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, HomeTheme.EDGE),
                BorderFactory.createEmptyBorder(1, 8, 1, 8)));
        JLabel title = new JLabel(caption);
        title.setForeground(HomeTheme.MUTED);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        String icon = caption.equals("Điểm") ? "score" : caption.equals("Thắng") ? "wins"
                : caption.equals("Thua") ? "losses" : "draws";
        title.setIcon(new HomeIcon(icon, title, tint, 16));
        title.setIconTextGap(6);
        number.setText(Integer.toString(value));
        number.setForeground(tint);
        number.setFont(number.getFont().deriveFont(Font.BOLD, 25f));
        title.setHorizontalAlignment(JLabel.CENTER);
        number.setHorizontalAlignment(JLabel.CENTER);
        panel.add(title, BorderLayout.NORTH);
        panel.add(number, BorderLayout.CENTER);
        return panel;
    }

    void openPlayerDetails(String username) {
        if (gameVisible) { return; }
        if (detailsDialog == null || !detailsDialog.isDisplayable()) {
            detailsDialog = new JDialog(this, "Hồ sơ và lịch sử đấu", false);
            detailsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            detailsPanel = new PlayerDetailsPanel(controller::requestPlayerDetails);
            JPanel background = GameTheme.background(new BorderLayout());
            background.putClientProperty("home-background", Boolean.TRUE);
            background.add(detailsPanel);
            detailsDialog.setContentPane(background);
            float scale = Math.min(1.25f, uiScale);
            ResponsiveUi.apply(background, scale);
            Rectangle screen = getGraphicsConfiguration().getBounds();
            java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
            Dimension preferred = ResponsiveUi.scaled(new Dimension(860, 620), scale);
            int width = Math.min(preferred.width, (int) ((screen.width - insets.left - insets.right) * 0.9));
            int height = Math.min(preferred.height, (int) ((screen.height - insets.top - insets.bottom) * 0.9));
            detailsDialog.setSize(width, height);
            detailsDialog.setMinimumSize(new Dimension(Math.min(640, width), Math.min(420, height)));
            PlayerDetailsPanel panel = detailsPanel;
            detailsDialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent event) { panel.clearTarget(); }
            });
        }
        detailsPanel.load(username);
        detailsDialog.setLocationRelativeTo(this);
        detailsDialog.setVisible(true);
        detailsDialog.toFront();
    }

    public void showPlayerDetails(PlayerDetails details) {
        if (detailsDialog != null && detailsDialog.isDisplayable()) { detailsPanel.showDetails(details); }
    }

    public void showPlayerDetailsError(String username, String error) {
        if (detailsDialog != null && detailsDialog.isDisplayable()) { detailsPanel.showError(username, error); }
    }

    private void closePlayerDetails() {
        if (detailsPanel != null) { detailsPanel.clearTarget(); }
        if (detailsDialog != null) { detailsDialog.dispose(); }
    }

    public void setNavigationHandlers(Runnable onRanking, Runnable onHistory) {
        configureNavigation(ranking, onRanking == null ? null : () -> {
            if (!rankingPending) {
                rankingPending = true;
                ranking.setEnabled(false);
                ranking.setText("Đang tải…");
                showLoading(rankingView, "Bảng xếp hạng", "Đang tải dữ liệu bảng xếp hạng...");
                selectNavigation(ranking);
                showContent("ranking");
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
                showContent("history");
                onHistory.run();
            }
        });
    }

    private void showLobby() {
        selectNavigation(lobby);
        showContent("lobby");
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
        ResponsiveUi.apply(challengeDialog.getContentPane(), uiScale);
        challengeDialog.setSize(ResponsiveUi.scaled(new Dimension(520, 360), uiScale));
        challengeDialog.setLocationRelativeTo(this);
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
        gameVisible = false;
        setFullScreen(false);
        closeInvitation();
        challengeId = null;
        roomId = room.roomId();
        cancel.setVisible(false);
        lobby.setEnabled(false);
        waitingRoom.setRoom(room);
        showContent("room");
        selectNavigation(null);
        showMessage("Chọn Sẵn sàng để bắt đầu sớm, hoặc đợi đồng hồ đếm hết.", false);
    }

    public void showMatchStarted(boolean integrated) {
        waitingRoom.showStarted(integrated);
        showMessage(integrated ? "Trận đấu đã bắt đầu." : "Trận đấu đã bắt đầu; gameplay chưa được tích hợp.", false);
    }

    public void showGame(String id, String player1, String player2) {
        closePlayerDetails();
        gameVisible = true;
        roomId = id;
        gamePlay.start(id, player1, player2);
        showContent("game");
        root.revalidate();
        root.repaint();
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
        gamePlay.stopEffects();
        gameVisible = false;
        setFullScreen(false);
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
        waitingRoom.readyRequestFailed();
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
        message.setForeground(error ? new java.awt.Color(255, 133, 117) : HomeTheme.MUTED);
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
        boolean wasPending = rankingPending;
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
        JTable table = new HomeTable(model, "Chưa có dữ liệu", "Dữ liệu sẽ xuất hiện tại đây.");
        HomeTheme.table(table);
        table.putClientProperty("home-name-column", 1);
        table.putClientProperty("home-own-username", profile.getUsername());
        table.getColumnModel().getColumn(0).setCellRenderer(new HomeTheme.RankCell());
        ((HomeTable) table).emptyMessage("Chưa có xếp hạng", "Hoàn thành trận đấu để ghi tên trên bảng.");
        setColumnWidths(table, 56, 200, 65, 75, 65, 65, 60);
        PlayerTableActions.install(table, 1, -1, this::openPlayerDetails);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        HomeTheme.scroll(scroll);
        showData(rankingView, "Bảng xếp hạng (" + entries.size() + " người chơi)", scroll);
        if ("ranking".equals(activeCard) || !wasPending) {
            selectNavigation(ranking);
            showContent("ranking");
        }
    }

    /** Displays the match history table inside the lobby content area. */
    public void showHistory(List<MatchHistoryEntry> entries) {
        boolean wasPending = historyPending;
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
        JTable table = new HomeTable(model, "Chưa có dữ liệu", "Dữ liệu sẽ xuất hiện tại đây.");
        HomeTheme.table(table);
        table.getColumnModel().getColumn(5).setCellRenderer(new HomeTheme.BadgeCell());
        ((HomeTable) table).emptyMessage("Chưa có trận đấu", "Thách đấu một người chơi để bắt đầu.");
        setColumnWidths(table, 45, 185, 145, 80, 105, 85);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        HomeTheme.scroll(scroll);
        String subtitle = entries.isEmpty() ? "Chưa có trận đấu nào." : entries.size() + " trận gần nhất";
        showData(historyView, "Lịch sử đấu — " + subtitle, scroll);
        if ("history".equals(activeCard) || !wasPending) {
            selectNavigation(history);
            showContent("history");
        }
    }

    private static void setColumnWidths(JTable table, int... widths) {
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    private void showLoading(JPanel view, String title, String text) {
        JLabel message = new JLabel(text, JLabel.CENTER);
        message.setForeground(HomeTheme.MUTED);
        message.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        message.setIcon(new HomeIcon("history", message, HomeTheme.LIME, 28));
        message.setIconTextGap(12);
        showData(view, title, message);
    }

    public void updateMatchResult(String id, MatchResultStats stats) {
        if (id.equals(roomId)) {
            gamePlay.setMatchResult(stats);
        }
    }

    private void showData(JPanel view, String titleText, java.awt.Component body) {
        view.removeAll();
        view.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel(titleText);
        title.setForeground(HomeTheme.LIME);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        view.add(title, BorderLayout.NORTH);
        view.add(body, BorderLayout.CENTER);
        view.revalidate();
        view.repaint();
        refreshResponsiveUi();
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
        closePlayerDetails();
        gamePlay.stopEffects();
        audio.close();
        if (fullScreenDevice != null) {
            fullScreenDevice.setFullScreenWindow(null);
            fullScreenDevice = null;
        }
        fullScreen = false;
        closeInvitation();
        super.dispose();
    }
}
