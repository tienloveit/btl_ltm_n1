package tank.client.controller;

import tank.client.model.PlayerSummary;
import tank.client.model.PlayerProfile;
import tank.client.model.RoomState;
import tank.client.network.ClientConnection;
import tank.client.ui.LoginFrame;
import tank.client.ui.PlayerFrame;
import tank.client.ui.RegisterFrame;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientController implements ClientConnection.Listener {
    private final String serverHost;
    private final int serverPort;
    private LoginFrame loginFrame;
    private RegisterFrame registerFrame;
    private PlayerFrame playerFrame;
    private volatile ClientConnection connection;
    private volatile boolean exitAfterLogout;
    private volatile boolean logoutRequested;
    private final ExecutorService requests = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "client-requests");
        thread.setDaemon(true);
        return thread;
    });
    private Runnable rankingHandler;
    private Runnable historyHandler;
    private MatchStartHandler matchStartHandler;

    @FunctionalInterface
    public interface MatchStartHandler {
        void onMatchStart(String roomId, String player1, String player2);
    }

    /** Register integration callbacks on the Swing EDT, before or after login. */
    public void setNavigationHandlers(Runnable rankingHandler, Runnable historyHandler) {
        this.rankingHandler = rankingHandler;
        this.historyHandler = historyHandler;
        if (playerFrame != null) { playerFrame.setNavigationHandlers(rankingHandler, historyHandler); }
    }

    public void setMatchStartHandler(MatchStartHandler handler) { matchStartHandler = handler; }

    public ClientController(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public void showLogin() {
        if (loginFrame != null) {
            loginFrame.dispose();
            loginFrame = null;
        }
        if (registerFrame != null) {
            registerFrame.dispose();
            registerFrame = null;
        }
        loginFrame = new LoginFrame(this);
        loginFrame.setVisible(true);
    }

    public void showRegister() {
        if (loginFrame != null) {
            loginFrame.dispose();
            loginFrame = null;
        }
        registerFrame = new RegisterFrame(this);
        registerFrame.setVisible(true);
    }

    public void login(String username, String password) {
        runConnectionRequest(connection -> connection.login(username, password));
    }

    public void register(String username, String email, char[] password) {
        String passwordValue = new String(password);
        runConnectionRequest(connection -> connection.register(username, email, passwordValue));
    }

    private void runConnectionRequest(ConnectionAction action) {
        exitAfterLogout = false;
        logoutRequested = false;
        requests.execute(() -> {
            try {
                ClientConnection previous = connection;
                if (previous != null) {
                    previous.close();
                }
                ClientConnection next = new ClientConnection(this);
                connection = next;
                next.connect(serverHost, serverPort);
                action.send(next);
            } catch (IOException exception) {
                showConnectionMessage(exception.getMessage());
            }
        });
    }

    public void logout(boolean exitApplication) {
        exitAfterLogout |= exitApplication;
        if (logoutRequested) { return; }
        logoutRequested = true;
        ClientConnection current = connection;
        if (current == null) {
            finishLogout();
            return;
        }
        requests.execute(() -> {
            try { current.logout(); }
            catch (IOException exception) {
                current.close();
                finishLogout();
            }
        });
    }

    public void challenge(String username) { sendLobbyAction(client -> client.challenge(username)); }
    public void acceptChallenge(String id) { sendLobbyAction(client -> client.acceptChallenge(id)); }
    public void rejectChallenge(String id) { sendLobbyAction(client -> client.rejectChallenge(id)); }
    public void cancelChallenge(String id) { sendLobbyAction(client -> client.cancelChallenge(id)); }
    public void leaveRoom(String id) { sendLobbyAction(client -> client.leaveRoom(id)); }

    private void sendLobbyAction(ConnectionAction action) {
        ClientConnection current = connection;
        requests.execute(() -> {
            try {
                if (current == null) { throw new IOException("Chưa kết nối tới Server."); }
                action.send(current);
            } catch (IOException exception) { onLobbyError(exception.getMessage()); }
        });
    }

    @Override
    public void onLoginSuccess(PlayerProfile profile) {
        SwingUtilities.invokeLater(() -> {
            if (loginFrame != null) {
                loginFrame.dispose();
                loginFrame = null;
            }
            playerFrame = new PlayerFrame(this, profile);
            playerFrame.setNavigationHandlers(rankingHandler, historyHandler);
            playerFrame.setVisible(true);
        });
    }

    @Override
    public void onLoginFailure(String message) {
        showLoginMessage(message);
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            if (registerFrame == null) {
                return;
            }
            registerFrame.setBusy(false);
            registerFrame.setMessage(message);
            if (success) {
                ClientConnection current = connection;
                connection = null;
                if (current != null) {
                    current.close();
                }
                registerFrame.dispose();
                registerFrame = null;
                showLogin();
                loginFrame.setMessage("Account created. Please login.");
            }
        });
    }

    @Override
    public void onPlayerList(List<PlayerSummary> players) {
        SwingUtilities.invokeLater(() -> {
            if (playerFrame != null && playerFrame.isDisplayable()) {
                playerFrame.updatePlayers(players);
            }
        });
    }

    @Override public void onChallengePending(String id, String opponent, int timeoutSeconds) {
        withPlayerFrame(frame -> frame.showPending(id, opponent, timeoutSeconds));
    }

    @Override public void onChallengeReceived(String id, String opponent, int timeoutSeconds) {
        withPlayerFrame(frame -> frame.showInvitation(id, opponent, timeoutSeconds));
    }

    @Override public void onChallengeClosed(String id, String reason) {
        withPlayerFrame(frame -> frame.closeChallenge(id, reason));
    }

    @Override public void onRoomState(RoomState room) { withPlayerFrame(frame -> frame.showRoom(room)); }

    @Override public void onRoomClosed(String id, String reason) {
        withPlayerFrame(frame -> frame.closeRoom(id, reason));
    }

    @Override public void onMatchStart(String roomId, String player1, String player2) {
        withPlayerFrame(frame -> {
            frame.showMatchStarted(matchStartHandler != null);
            if (matchStartHandler != null) {
                try { matchStartHandler.onMatchStart(roomId, player1, player2); }
                catch (RuntimeException exception) {
                    frame.showLobbyError("Không thể mở gameplay: " + exception.getMessage());
                }
            }
        });
    }

    @Override public void onLobbyError(String reason) { withPlayerFrame(frame -> frame.showLobbyError(reason)); }

    @Override public void onServerError(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (playerFrame != null) { playerFrame.showLobbyError(reason); }
            else { showConnectionMessage(reason); }
        });
    }

    private void withPlayerFrame(java.util.function.Consumer<PlayerFrame> action) {
        SwingUtilities.invokeLater(() -> {
            if (playerFrame != null && playerFrame.isDisplayable()) { action.accept(playerFrame); }
        });
    }

    @Override
    public void onLogoutSuccess() {
        ClientConnection current = connection;
        if (current != null) {
            current.close();
        }
        finishLogout();
    }

    @Override
    public void onDisconnected(String message) {
        connection = null;
        SwingUtilities.invokeLater(() -> {
            if (playerFrame != null) {
                playerFrame.dispose();
                playerFrame = null;
            }
            if (!exitAfterLogout) {
                if (registerFrame != null && registerFrame.isDisplayable()) {
                    registerFrame.setBusy(false);
                    registerFrame.setMessage(message);
                } else {
                    if (loginFrame == null || !loginFrame.isDisplayable()) {
                        showLogin();
                    }
                    loginFrame.setMessage(message);
                }
            }
        });
    }

    private void finishLogout() {
        connection = null;
        SwingUtilities.invokeLater(() -> {
            if (playerFrame != null) {
                playerFrame.dispose();
                playerFrame = null;
            }
            if (exitAfterLogout) {
                System.exit(0);
            }
            showLogin();
        });
    }

    private void showLoginMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (loginFrame == null || !loginFrame.isDisplayable()) {
                showLogin();
            }
            loginFrame.setMessage(message == null ? "Không thể kết nối tới server." : message);
        });
    }

    private void showConnectionMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (registerFrame != null && registerFrame.isDisplayable()) {
                registerFrame.setBusy(false);
                registerFrame.setMessage(message == null ? "Could not connect to server." : message);
            } else {
                showLoginMessage(message);
            }
        });
    }

    private interface ConnectionAction {
        void send(ClientConnection connection) throws IOException;
    }
}
