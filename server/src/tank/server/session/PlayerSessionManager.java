package tank.server.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSessionManager {
    private final ConcurrentHashMap<String, ClientHandler> sessions = new ConcurrentHashMap<>();
    private java.util.function.Consumer<String> disconnectListener = username -> { };

    synchronized boolean add(ClientHandler handler) {
        return sessions.putIfAbsent(handler.getUsername(), handler) == null;
    }

    synchronized void remove(ClientHandler handler) {
        if (handler.getUsername() != null && sessions.remove(handler.getUsername(), handler)) {
            disconnectListener.accept(handler.getUsername());
            broadcastPlayerList();
        }
    }

    public synchronized void setStatus(String username, PlayerStatus status) {
        setStatuses(status, username);
    }

    public synchronized void setStatuses(PlayerStatus status, String... usernames) {
        for (String username : usernames) {
            ClientHandler handler = sessions.get(username);
            if (handler != null) { handler.setStatus(status); }
        }
        broadcastPlayerList();
    }

    public synchronized void broadcastPlayerList() {
        List<ClientHandler> recipients = new ArrayList<>(sessions.values());
        List<PlayerSummary> players = snapshot();
        for (ClientHandler recipient : recipients) {
            try {
                recipient.sendPlayerList(players);
            } catch (IOException exception) {
                recipient.close();
            }
        }
    }

    public synchronized void setDisconnectListener(java.util.function.Consumer<String> listener) {
        disconnectListener = java.util.Objects.requireNonNull(listener);
    }

    public synchronized boolean isOnline(String username) {
        ClientHandler handler = sessions.get(username);
        return handler != null && !handler.isClosed();
    }

    public synchronized PlayerStatus getStatus(String username) {
        ClientHandler handler = sessions.get(username);
        return handler == null || handler.isClosed() ? PlayerStatus.OFFLINE : handler.getStatus();
    }

    /** Queue a complete message; never block the lobby lock on a slow socket. */
    public synchronized void sendTo(String username, String type, String... values) {
        ClientHandler handler = sessions.get(username);
        if (handler != null) {
            try {
                handler.send(type, values);
            } catch (IOException exception) {
                handler.close();
            }
        }
    }

    private List<PlayerSummary> snapshot() {
        List<PlayerSummary> result = new ArrayList<>();
        for (ClientHandler handler : sessions.values()) {
            result.add(new PlayerSummary(
                    handler.getUsername(),
                    handler.getScore(),
                    handler.getStatus().name()));
        }
        result.sort(Comparator.comparing(PlayerSummary::getUsername, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    static final class PlayerSummary {
        private final String username;
        private final int score;
        private final String status;

        PlayerSummary(String username, int score, String status) {
            this.username = username;
            this.score = score;
            this.status = status;
        }

        String getUsername() {
            return username;
        }

        int getScore() {
            return score;
        }

        String getStatus() {
            return status;
        }
    }
}
