package tank.client.ui;

import tank.client.controller.ClientController;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import tank.client.model.RoomState;
import tank.client.model.GameState;
import tank.client.model.RankingEntry;
import tank.client.model.MatchHistoryEntry;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real Swing controls on the EDT and exports offscreen previews. */
public final class LobbyUiCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checks += tank.client.audio.AudioCheck.run();
        checks += tank.client.audio.MusicCheck.run();
        checks += tank.client.network.MatchResultProtocolCheck.run();
        SwingUtilities.invokeAndWait(() -> {
            PlayerFrame frame = null;
            ChallengeDialog dialog = null;
            try {
                checks += AudioUiCheck.run();
                checks += MatchResultUiCheck.run();
                checks += GameplayUiCheck.run();
                checks += FinisherUiCheck.run();
                checks += PlayerDetailsUiCheck.run();
                check("HP 51/100 (51%)".equals(GamePlayPanel.healthLabel(51)),
                        "health HUD exposes the numeric value and percentage");
                check(GamePlayPanel.healthColorFor(51).equals(new java.awt.Color(46, 125, 88))
                                && GamePlayPanel.healthColorFor(50).equals(new java.awt.Color(143, 99, 18))
                                && GamePlayPanel.healthColorFor(25).equals(new java.awt.Color(161, 65, 62)),
                        "health HUD uses distinct green, amber, and red ranges");
                check("HP 0/100 (0%)".equals(GamePlayPanel.healthLabel(-1))
                                && "HP 100/100 (100%)".equals(GamePlayPanel.healthLabel(101)),
                        "health HUD clamps out-of-range values safely");
                AtomicReference<String> readyRoom = new AtomicReference<>();
                WaitingRoomPanel waitingCheck = new WaitingRoomPanel("alice", readyRoom::set, () -> { });
                String readyRoomId = "12345678-0000-0000-0000-000000000000";
                waitingCheck.setRoom(new RoomState(readyRoomId, "alice", "bob", 10));
                check(findLabel(waitingCheck, "alice") != null && findLabel(waitingCheck, "bob") != null,
                        "waiting room displays both player names");
                check(count(waitingCheck, TankPortrait.class) == 2,
                        "waiting room displays one vector tank for each player");
                check("10".equals(findLabel(waitingCheck, "10").getText()),
                        "waiting room shows the server's 10-second snapshot");
                check(findButton(waitingCheck, "TÔI SẴN SÀNG").isEnabled(), "player can signal ready once");
                findButton(waitingCheck, "TÔI SẴN SÀNG").doClick();
                check(readyRoomId.equals(readyRoom.get()) && !findButton(waitingCheck, "ĐANG GỬI…").isEnabled(),
                        "ready request includes the room and cannot be repeated while pending");
                waitingCheck.setRoom(new RoomState(readyRoomId, "alice", "bob", 10, true, false));
                check(findButton(waitingCheck, "ĐÃ SẴN SÀNG") != null
                                && findLabel(waitingCheck, "Đang chờ sẵn sàng") != null,
                        "server snapshot shows each player's independent ready status");
                waitingCheck.setRoom(new RoomState(readyRoomId, "alice", "bob", 3, true, true));
                check(findLabel(waitingCheck, "3") != null && findLabel(waitingCheck, "Cả hai đã sẵn sàng!") != null,
                        "both-ready snapshot updates the room to 3 seconds");
                List<PlayerSummary> roster = List.of(
                        new PlayerSummary("doanngocquang", 100, "ONLINE"),
                        new PlayerSummary("player_a", 422, "ONLINE"),
                        new PlayerSummary("player_b", 421, "PLAYING"),
                        new PlayerSummary("player_c", 321, "WAITING"),
                        new PlayerSummary("player_d", 231, "ONLINE"));
                AtomicReference<String> target = new AtomicReference<>();
                AtomicInteger challengeRequests = new AtomicInteger();
                OnlinePlayersPanel panel = new OnlinePlayersPanel("doanngocquang", username -> {
                    target.set(username); challengeRequests.incrementAndGet();
                });
                panel.setPlayers(roster);
                JTable table = find(panel, JTable.class);
                check(table.getRowCount() == 4, "own account omitted");
                check(table.getColumnCount() == 4 && "Thao tác".equals(table.getColumnName(3)), "four labeled lobby columns");
                check(table.isCellEditable(0, 3), "available opponent can be challenged");
                check(!table.isCellEditable(1, 3) && !table.isCellEditable(2, 3), "busy opponents disabled");
                table.editCellAt(0, 3);
                JButton challengeButton = find((Container) table.getEditorComponent(), JButton.class);
                table.getEditorComponent().setSize(145, 54);
                ((Container) table.getEditorComponent()).doLayout();
                check(challengeButton.getWidth() < 145 && challengeButton.getHeight() < 54
                                && challengeButton.getX() > 0 && challengeButton.getY() > 0,
                        "challenge pill is inset and centered within its table cell");
                challengeButton.doClick();
                challengeButton.doClick();
                check("player_a".equals(target.get()), "button sends the selected username");
                check(challengeRequests.get() == 1, "compact challenge button sends only one request");
                check(!table.isCellEditable(3, 3), "pending send prevents double-click requests");
                panel.setRequesting(false);
                panel.setPlayers(List.of(new PlayerSummary("doanngocquang", 100, "WAITING"), roster.get(1)));
                check(!table.isCellEditable(0, 3), "own busy status disables challenges");

                frame = new PlayerFrame(new ClientController("127.0.0.1", 5000),
                        new PlayerProfile("doanngocquang", 13, 100, 10, 2, 1, "ONLINE"), tank.client.audio.SoundPlayer.SILENT);
                frame.updatePlayers(roster);
                JTable responsiveTable = find(frame.getContentPane(), JTable.class);
                float baseLobbyFont = responsiveTable.getFont().getSize2D();
                int baseLobbyRowHeight = responsiveTable.getRowHeight();
                frame.setSize(1920, 1080);
                frame.refreshResponsiveUi();
                check(responsiveTable.getFont().getSize2D() > baseLobbyFont
                                && responsiveTable.getRowHeight() > baseLobbyRowHeight,
                        "lobby text and rows grow with a projection-sized window");
                capture((JComponent) frame.getContentPane(), "lobby-projection", 1920, 1080);
                frame.setSize(3840, 2160);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "lobby-4k", 3840, 2160);
                frame.setSize(1920, 1080);
                frame.refreshResponsiveUi();
                check(findLabel(frame.getContentPane(), "doanngocquang").getMouseListeners().length == 0,
                        "header username is an inert label");
                frame.openPlayerDetails("doanngocquang");
                javax.swing.JDialog profileDialog = null;
                for (java.awt.Window window : frame.getOwnedWindows()) {
                    if (window instanceof javax.swing.JDialog candidate && candidate.isVisible()) {
                        profileDialog = candidate;
                        break;
                    }
                }
                frame.showPlayerDetails(new tank.client.model.PlayerDetails(
                        new PlayerProfile("doanngocquang", 13, 100, 10, 2, 1, "ONLINE"),
                        List.of(new MatchHistoryEntry(7, "23/10/2026 10:30", "player_a", 3, 1, "WIN"))));
                check(profileDialog != null && profileDialog.isVisible()
                                && find(profileDialog.getContentPane(), JTable.class).getRowCount() == 1
                                && findLabel(profileDialog.getContentPane(), "doanngocquang").getFont().getSize2D() >= 26f,
                        "profile dialog combines readable statistics and match history");
                capture((JComponent) profileDialog.getContentPane(), "profile-projection",
                        profileDialog.getContentPane().getWidth(), profileDialog.getContentPane().getHeight());
                profileDialog.dispose();
                frame.setSize(900, 540);
                frame.refreshResponsiveUi();
                check(responsiveTable.getFont().getSize2D() >= baseLobbyFont
                                && responsiveTable.getRowHeight() >= baseLobbyRowHeight,
                        "minimum window preserves readable baseline text and row height");
                capture((JComponent) frame.getContentPane(), "lobby-minimum", 900, 540);
                frame.setSize(960, 560);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "lobby", 960, 560);
                check(findLabel(frame.getContentPane(), "NGƯỜI CHƠI ONLINE") == null
                                && findLabel(frame.getContentPane(), "Chọn người đang chờ để gửi lời mời thách đấu") == null,
                        "lobby gives the title and subtitle area back to the player list");
                java.awt.Rectangle sharedContent = responsiveTable.getParent().getParent().getParent().getBounds();
                frame.showRanking(List.of(
                        new RankingEntry("player_a", 12, 422, 8, 3, 1),
                        new RankingEntry("player_b", 10, 421, 7, 3, 0),
                        new RankingEntry("doanngocquang", 13, 100, 10, 2, 1),
                        new RankingEntry("opponent_with_a_long_name", 2, 80, 1, 1, 0)));
                check(findButton(frame.getContentPane(), "Xếp hạng").isSelected(), "ranking menu is selected");
                check(findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible()
                                && findButton(frame.getContentPane(), "Lobby").getParent().getParent().isVisible(),
                        "ranking preserves the account header and sidebar");
                capture((JComponent) frame.getContentPane(), "ranking", 960, 560);
                JTable rankingTable = findTable(frame.getContentPane(), "#");
                JLabel ownRank = (JLabel) rankingTable.prepareRenderer(rankingTable.getCellRenderer(2, 1), 2, 1);
                check(ownRank.getBackground().equals(new java.awt.Color(37, 52, 41))
                                && ownRank.getIcon() != null,
                        "ranking marks the current account without changing its backend values");
                check(sharedContent.equals(rankingTable.getParent().getParent().getParent().getBounds()),
                        "ranking occupies the same content frame as Lobby");
                java.awt.Rectangle nameCell = rankingTable.getCellRect(0, 1, true);
                rankingTable.dispatchEvent(new java.awt.event.MouseEvent(rankingTable,
                        java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                        nameCell.x + nameCell.width / 2, nameCell.y + nameCell.height / 2,
                        1, false, java.awt.event.MouseEvent.BUTTON1));
                javax.swing.JDialog rankedProfile = null;
                for (java.awt.Window window : frame.getOwnedWindows()) {
                    if (window instanceof javax.swing.JDialog candidate && candidate.isVisible()) { rankedProfile = candidate; }
                }
                check(rankedProfile != null && findLabel(rankedProfile.getContentPane(), "player_a") != null,
                        "single click on ranking opens the selected player's details");
                rankedProfile.dispose();
                float rankingFont = rankingTable.getFont().getSize2D();
                frame.setSize(1920, 1080);
                frame.refreshResponsiveUi();
                check(rankingTable.getFont().getSize2D() > rankingFont,
                        "ranking table text scales with a projection-sized window");
                capture((JComponent) frame.getContentPane(), "ranking-projection", 1920, 1080);
                frame.setSize(3840, 2160);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "ranking-4k", 3840, 2160);
                frame.setSize(900, 540);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "ranking-minimum", 900, 540);
                frame.setSize(960, 560);
                frame.refreshResponsiveUi();
                frame.showHistory(List.of(new MatchHistoryEntry(7, "2026-10-12 10:30", "player_a", 3, 1, "WIN")));
                check(findButton(frame.getContentPane(), "Lịch sử đấu").isSelected(), "history menu is selected");
                check(findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible()
                                && findButton(frame.getContentPane(), "Lobby").getParent().getParent().isVisible(),
                        "history preserves the account header and sidebar");
                capture((JComponent) frame.getContentPane(), "history", 960, 560);
                JTable historyTable = findTable(frame.getContentPane(), "ID");
                check(sharedContent.equals(historyTable.getParent().getParent().getParent().getBounds()),
                        "history occupies the same content frame as Lobby");
                float historyFont = historyTable.getFont().getSize2D();
                frame.setSize(1920, 1080);
                frame.refreshResponsiveUi();
                check(historyTable.getFont().getSize2D() > historyFont,
                        "match history text scales with a projection-sized window");
                capture((JComponent) frame.getContentPane(), "history-projection", 1920, 1080);
                frame.setSize(3840, 2160);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "history-4k", 3840, 2160);
                frame.setSize(900, 540);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "history-minimum", 900, 540);
                check(historyTable.getColumnModel().getColumn(1).getWidth()
                                >= historyTable.getFontMetrics(historyTable.getFont())
                                .stringWidth(historyTable.getValueAt(0, 1).toString()) + 8,
                        "minimum history view displays the complete match timestamp");
                frame.setSize(960, 560);
                frame.refreshResponsiveUi();
                findButton(frame.getContentPane(), "Lobby").doClick();
                check(findButton(frame.getContentPane(), "Lobby").isSelected()
                                && findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible(),
                        "Lobby navigation restores the player list and account header");
                frame.setNavigationHandlers(() -> { }, () -> { });
                findButton(frame.getContentPane(), "Xếp hạng").doClick();
                findButton(frame.getContentPane(), "Lobby").doClick();
                frame.showRanking(List.of(new RankingEntry("player_a", 12, 422, 8, 3, 1)));
                check(findButton(frame.getContentPane(), "Lobby").isSelected()
                                && findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible(),
                        "late ranking response does not pull the player back from Lobby");
                JButton rankingNavigation = findButton(frame.getContentPane(), "Xếp hạng");
                JButton historyNavigation = findButton(frame.getContentPane(), "Lịch sử đấu");
                rankingNavigation.doClick();
                capture((JComponent) frame.getContentPane(), "ranking-loading", 960, 560);
                check(rankingNavigation.isSelected() && !rankingNavigation.isEnabled()
                                && !historyNavigation.isSelected(),
                        "ranking is the only green menu item while it is loading");
                historyNavigation.doClick();
                check(historyNavigation.isSelected() && !historyNavigation.isEnabled()
                                && !rankingNavigation.isSelected()
                                && findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible(),
                        "switching to loading history keeps navigation and selects only history");
                frame.showRanking(List.of());
                check(historyNavigation.isSelected() && !rankingNavigation.isSelected(),
                        "late ranking data does not replace the active history view");
                frame.showHistory(List.of());
                capture((JComponent) frame.getContentPane(), "history-empty", 900, 540);
                check(historyNavigation.isSelected() && historyNavigation.isEnabled(),
                        "empty history finishes loading without changing the selected menu");
                findButton(frame.getContentPane(), "Lobby").doClick();
                frame.showPending("challenge-preview", "player_a", 30);
                capture((JComponent) frame.getContentPane(), "pending", 960, 560);
                frame.closeChallenge("challenge-preview", "ACCEPTED");
                frame.showRoom(new RoomState("12345678-0000-0000-0000-000000000000", "doanngocquang", "player_a", 10));
                check(!findLabel(frame.getContentPane(), "doanngocquang").getParent().isVisible()
                                && !findButton(frame.getContentPane(), "Lobby").getParent().getParent().isVisible(),
                        "waiting room hides account header and sidebar before gameplay");
                capture((JComponent) frame.getContentPane(), "room", 960, 560);
                capture((JComponent) frame.getContentPane(), "room-minimum", 900, 540);
                float baseRoomNameFont = findLabel(frame.getContentPane(), "doanngocquang").getFont().getSize2D();
                frame.setSize(1920, 1080);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "room-projection", 1920, 1080);
                frame.setSize(3840, 2160);
                frame.refreshResponsiveUi();
                capture((JComponent) frame.getContentPane(), "room-4k", 3840, 2160);
                TankPortrait scaledPortrait = find(frame.getContentPane(), TankPortrait.class);
                check(findLabel(frame.getContentPane(), "doanngocquang").getFont().getSize2D() > baseRoomNameFont
                                && scaledPortrait.getWidth() >= 132 && scaledPortrait.getHeight() >= 68,
                        "waiting room names and tank portraits grow on a large display");
                frame.setSize(960, 560);
                frame.refreshResponsiveUi();
                frame.showRoom(new RoomState("12345678-0000-0000-0000-000000000000",
                        "doanngocquang", "player_a", 3, true, true));
                capture((JComponent) frame.getContentPane(), "room-ready", 960, 560);
                frame.showMatchStarted(true);
                capture((JComponent) frame.getContentPane(), "handoff", 960, 560);
                frame.openPlayerDetails("player_a");
                javax.swing.JDialog openDetails = null;
                for (java.awt.Window window : frame.getOwnedWindows()) {
                    if (window instanceof javax.swing.JDialog candidate && candidate.isVisible()) { openDetails = candidate; }
                }
                frame.showGame("12345678-0000-0000-0000-000000000000", "doanngocquang", "player_a");
                check(openDetails != null && !openDetails.isDisplayable(), "entering a match closes the modeless profile window");
                check(!findButton(frame.getContentPane(), "Lobby").getParent().getParent().isVisible(),
                        "windowed match uses the full content area");
                frame.updateGame(new GameState("12345678-0000-0000-0000-000000000000", 179,
                        new GameState.Tank(400, 100, 49, 1), new GameState.Tank(280, 80, 45, 0),
                        List.of(new GameState.Bullet("doanngocquang", 400, 220))));
                capture((JComponent) frame.getContentPane(), "gameplay", 960, 560);
                capture((JComponent) frame.getContentPane(), "gameplay-minimum", 900, 540);
                frame.showHitEffect("12345678-0000-0000-0000-000000000000", 280, 40);
                capture((JComponent) frame.getContentPane(), "hit-effect", 960, 560);
                frame.showGame("12345678-0000-0000-0000-000000000000", "player_a", "doanngocquang");
                frame.updateGame(new GameState("12345678-0000-0000-0000-000000000000", 145,
                        new GameState.Tank(280, 60, 40, 2), new GameState.Tank(400, 80, 42, 3), List.of()));
                frame.showHitEffect("12345678-0000-0000-0000-000000000000", 400, 40);
                capture((JComponent) frame.getContentPane(), "hit-effect-player2", 900, 540);
                frame.showGame("12345678-0000-0000-0000-000000000000", "doanngocquang", "player_a");
                frame.updateGame(new GameState("12345678-0000-0000-0000-000000000000", 179,
                        new GameState.Tank(400, 100, 49, 1), new GameState.Tank(280, 80, 45, 0), List.of()));
                frame.setVisible(true);
                java.awt.Rectangle windowedBounds = frame.getBounds();
                java.awt.GraphicsDevice screen = frame.getGraphicsConfiguration().getDevice();
                GamePlayPanel game = find(frame.getContentPane(), GamePlayPanel.class);
                JButton fullScreen = findButton(game, "Toàn màn hình");
                check(fullScreen != null && !fullScreen.isFocusable(), "fullscreen button preserves gameplay keyboard input");
                for (int attempt = 0; attempt < 2; attempt++) {
                    fullScreen.doClick();
                    check(frame.isUndecorated() && screen.getFullScreenWindow() == frame,
                            "game occupies the current screen without window decorations");
                    check(!findButton(frame.getContentPane(), "Lobby").isShowing(), "lobby menu hidden in fullscreen");
                    check("Về cửa sổ".equals(fullScreen.getText()), "fullscreen button shows restore action");
                    if (attempt == 0) {
                        capture((JComponent) frame.getContentPane(), "gameplay-fullscreen",
                                frame.getContentPane().getWidth(), frame.getContentPane().getHeight());
                        frame.showGameOver("12345678-0000-0000-0000-000000000000", "player_a", "Hết thời gian.");
                        MatchResultOverlay result = find(game, MatchResultOverlay.class);
                        java.awt.Insets gameInsets = game.getInsets();
                        check(result.isShowing() && result.getWidth()
                                        == game.getWidth() - gameInsets.left - gameInsets.right,
                                "result covers gameplay in fullscreen");
                        findButton(result, "Về cửa sổ").doClick();
                    } else {
                        Object escape = game.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                                .get(javax.swing.KeyStroke.getKeyStroke("ESCAPE"));
                        game.getActionMap().get(escape).actionPerformed(
                                new java.awt.event.ActionEvent(game, 0, "escape"));
                    }
                    check(!frame.isUndecorated() && screen.getFullScreenWindow() == null,
                            "button and Escape release fullscreen");
                    check(frame.getBounds().equals(windowedBounds), "window size and position restored");
                    check(!findButton(frame.getContentPane(), "Lobby").isShowing(),
                            "windowed match keeps lobby chrome hidden");
                }
                fullScreen.doClick();
                frame.updateProfile(new PlayerProfile("doanngocquang", 14, 103, 11, 2, 1, "ONLINE"));
                frame.closeRoom("12345678-0000-0000-0000-000000000000", "Đã rời trận.");
                check(!find(game, MatchResultOverlay.class).isAnimating(), "leaving a match stops result animation");
                check(!frame.isUndecorated() && screen.getFullScreenWindow() == null,
                        "leaving a match restores windowed mode");
                check(findButton(frame.getContentPane(), "Lobby").isShowing(),
                        "leaving a match restores lobby layout");
                check(findLabel(frame.getContentPane(), "103") != null,
                        "backend profile score received during a match is visible on return to Lobby");
                frame.setVisible(false);

                AtomicInteger accepts = new AtomicInteger(), rejects = new AtomicInteger();
                dialog = new ChallengeDialog(frame, "doanngocquang", 30, accepts::incrementAndGet, rejects::incrementAndGet);
                capture((JComponent) dialog.getContentPane(), "invitation", 520, 330);
                float invitationFont = findButton(dialog.getContentPane(), "Chấp nhận").getFont().getSize2D();
                float invitationScale = ResponsiveUi.scaleFor(1920, 1080);
                ResponsiveUi.apply((JComponent) dialog.getContentPane(), invitationScale);
                dialog.setSize(ResponsiveUi.scaled(new java.awt.Dimension(520, 360), invitationScale));
                check(findButton(dialog.getContentPane(), "Chấp nhận").getFont().getSize2D() > invitationFont,
                        "challenge invitation text scales for a large display");
                capture((JComponent) dialog.getContentPane(), "invitation-projection",
                        dialog.getWidth(), dialog.getHeight());
                findButton(dialog.getContentPane(), "Chấp nhận").doClick();
                findButton(dialog.getContentPane(), "Chấp nhận").doClick();
                check(accepts.get() == 1, "accept disabled while waiting for response");
                dialog.dispose();
                dialog = new ChallengeDialog(frame, "player_a", 30, accepts::incrementAndGet, rejects::incrementAndGet);
                dialog.dispatchEvent(new java.awt.event.WindowEvent(dialog, java.awt.event.WindowEvent.WINDOW_CLOSING));
                check(rejects.get() == 1, "closing invitation sends rejection");
                dialog.dispose();
                String longOpponent = "opponent_with_a_very_long_username_123456789";
                dialog = new ChallengeDialog(frame, longOpponent, 30, accepts::incrementAndGet, rejects::incrementAndGet);
                capture((JComponent) dialog.getContentPane(), "invitation-minimum-long-name", 470, 290);
                JLabel opponentLabel = findLabel(dialog.getContentPane(), longOpponent);
                check(opponentLabel.getHeight() >= opponentLabel.getIcon().getIconHeight(),
                        "invitation avatar remains fully visible at minimum content size");
                check(longOpponent.equals(findLabel(dialog.getContentPane(), longOpponent).getToolTipText())
                                && !dialog.isModal(),
                        "invitation keeps the full opponent name accessible and does not block server callbacks");
                dialog.setBusy(true);
                capture((JComponent) dialog.getContentPane(), "invitation-busy", 470, 290);
                check(!findButton(dialog.getContentPane(), "Chấp nhận").isEnabled()
                                && !findButton(dialog.getContentPane(), "Từ chối").isEnabled(),
                        "waiting for the server locks both invitation responses");
                dialog.setBusy(false);
                findButton(dialog.getContentPane(), "Từ chối").doClick();
                findButton(dialog.getContentPane(), "Từ chối").doClick();
                check(rejects.get() == 2, "retrying then double-clicking reject sends one response");
                dialog.dispose();
                frame.showInvitation("expiry-preview", "player_a", 30);
                javax.swing.JDialog expiring = null;
                for (java.awt.Window owned : frame.getOwnedWindows()) {
                    if (owned instanceof ChallengeDialog candidate && candidate.isVisible()) { expiring = candidate; }
                }
                frame.closeChallenge("expiry-preview", "TIMEOUT");
                check(expiring != null && !expiring.isDisplayable(),
                        "server expiry disposes the modeless invitation without a local response");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                if (dialog != null) { dialog.dispose(); }
                if (frame != null) { frame.dispose(); }
            }
        });
        checks += MatchResultUiCheck.verifyLiveAnimation();
        checks += MatchResultUiCheck.verifyLethalImpact();
        System.out.println("PASS: " + checks + " Swing checks; previews: client/out/preview/");
    }

    static BufferedImage capture(JComponent component, String name, int width, int height) throws Exception {
        component.setSize(width, height);
        layout(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        Path directory = Path.of("client", "out", "preview");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        return image;
    }

    static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) { layout(container); }
        }
    }

    static <T> T find(Container parent, Class<T> type) {
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) { return type.cast(component); }
            if (component instanceof Container container) {
                T result = find(container, type);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static int count(Container parent, Class<?> type) {
        int total = 0;
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) { total++; }
            if (component instanceof Container container) { total += count(container, type); }
        }
        return total;
    }

    private static JTable findTable(Container parent, String firstColumn) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JTable table && table.getColumnCount() > 0
                    && firstColumn.equals(table.getColumnName(0))) { return table; }
            if (component instanceof Container container) {
                JTable result = findTable(container, firstColumn);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    static JButton findButton(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) { return button; }
            if (component instanceof Container container) {
                JButton result = findButton(container, text);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    static JLabel findLabel(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) { return label; }
            if (component instanceof Container container) {
                JLabel result = findLabel(container, text);
                if (result != null) { return result; }
            }
        }
        return null;
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }
}
