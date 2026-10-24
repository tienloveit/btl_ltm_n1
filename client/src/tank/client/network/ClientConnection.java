package tank.client.network;

import tank.client.model.PlayerSummary;
import tank.client.model.PlayerProfile;
import tank.client.model.PlayerDetails;
import tank.client.model.RoomState;
import tank.client.model.GameState;
import tank.client.model.MatchResultStats;
import tank.client.model.RankingEntry;
import tank.client.model.MatchHistoryEntry;
import tank.client.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientConnection implements AutoCloseable {
    public interface Listener {
        void onLoginSuccess(PlayerProfile profile);
        void onLoginFailure(String message);
        void onRegisterResult(boolean success, String message);
        void onPlayerList(List<PlayerSummary> players);
        default void onProfileUpdate(PlayerProfile profile) { }
        default void onRanking(List<RankingEntry> entries) { }
        default void onHistory(List<MatchHistoryEntry> entries) { }
        default void onPlayerDetails(PlayerDetails details) { }
        default void onPlayerDetailsError(String username, String message) { }
        void onLogoutSuccess();
        void onDisconnected(String message);
        default void onChallengePending(String id, String opponent, int timeoutSeconds) { }
        default void onChallengeReceived(String id, String opponent, int timeoutSeconds) { }
        default void onChallengeClosed(String id, String reason) { }
        default void onRoomState(RoomState room) { }
        default void onRoomClosed(String roomId, String reason) { }
        default void onMatchStart(String roomId, String player1, String player2) { }
        default void onGameState(GameState state) { }
        default void onGameOver(String roomId, String winner, String reason) { }
        default void onMatchResult(String roomId, MatchResultStats stats) { }
        default void onHitEffect(String roomId, int x, int y) { }
        default void onLobbyError(String reason) { }
        default void onServerError(String reason) { onLoginFailure(reason); }
    }

    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public ClientConnection(Listener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setKeepAlive(true);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public synchronized void login(String username, String password) throws IOException {
        send(Protocol.LOGIN_REQUEST, username, password);
    }

    public synchronized void register(String username, String email, String password) throws IOException {
        send(Protocol.REGISTER_REQUEST, username, email, password);
    }

    public synchronized void logout() throws IOException {
        send(Protocol.LOGOUT_REQUEST);
    }

    public synchronized void challenge(String username) throws IOException { send(Protocol.CHALLENGE, username); }
    public synchronized void acceptChallenge(String id) throws IOException { send(Protocol.ACCEPT, id); }
    public synchronized void rejectChallenge(String id) throws IOException { send(Protocol.REJECT, id); }
    public synchronized void cancelChallenge(String id) throws IOException { send(Protocol.CHALLENGE_CANCEL, id); }
    public synchronized void leaveRoom(String id) throws IOException { send(Protocol.ROOM_LEAVE, id); }
    public synchronized void readyRoom(String id) throws IOException { send(Protocol.ROOM_READY, id); }
    public synchronized void moveLeft() throws IOException { send(Protocol.MOVE_LEFT); }
    public synchronized void moveRight() throws IOException { send(Protocol.MOVE_RIGHT); }
    public synchronized void shoot() throws IOException { send(Protocol.SHOOT); }
    public synchronized void exitGame() throws IOException { send(Protocol.EXIT); }
    public synchronized void playAgain() throws IOException { send(Protocol.PLAY_AGAIN); }
    public synchronized void requestRanking() throws IOException { send(Protocol.RANKING_REQUEST); }
    public synchronized void requestHistory() throws IOException { send(Protocol.HISTORY_REQUEST); }
    public synchronized void requestPlayerDetails(String username) throws IOException {
        send(Protocol.PLAYER_DETAILS_REQUEST, username);
    }

    private void send(String messageType, String... values) throws IOException {
        if (closed.get() || output == null) {
            throw new IOException("Chưa kết nối tới server.");
        }
        output.writeUTF(messageType);
        for (String value : values) {
            output.writeUTF(value);
        }
        output.flush();
    }

    private void readLoop() {
        String closeMessage = "Mất kết nối tới server.";
        try {
            while (!closed.get()) {
                String messageType = input.readUTF();
                if (Protocol.LOGIN_SUCCESS.equals(messageType)) {
                    listener.onLoginSuccess(readProfile());
                } else if (Protocol.LOGIN_FAIL.equals(messageType)) {
                    listener.onLoginFailure(input.readUTF());
                } else if (Protocol.REGISTER_SUCCESS.equals(messageType)) {
                    listener.onRegisterResult(true, input.readUTF());
                } else if (Protocol.REGISTER_FAIL.equals(messageType)) {
                    listener.onRegisterResult(false, input.readUTF());
                } else if (Protocol.PLAYER_LIST.equals(messageType)) {
                    listener.onPlayerList(readPlayerList());
                } else if (Protocol.PROFILE_UPDATE.equals(messageType)) {
                    listener.onProfileUpdate(readProfile());
                } else if (Protocol.LOGOUT_SUCCESS.equals(messageType)) {
                    listener.onLogoutSuccess();
                    return;
                } else if (Protocol.SERVER_ERROR.equals(messageType)) {
                    listener.onServerError(input.readUTF());
                } else if (Protocol.CHALLENGE_PENDING.equals(messageType)) {
                    listener.onChallengePending(input.readUTF(), input.readUTF(), readInt());
                } else if (Protocol.CHALLENGE_RECEIVED.equals(messageType)) {
                    listener.onChallengeReceived(input.readUTF(), input.readUTF(), readInt());
                } else if (Protocol.CHALLENGE_CLOSED.equals(messageType)) {
                    listener.onChallengeClosed(input.readUTF(), input.readUTF());
                } else if (Protocol.ROOM_STATE.equals(messageType)) {
                    listener.onRoomState(new RoomState(input.readUTF(), input.readUTF(), input.readUTF(), readInt(),
                            readBoolean(), readBoolean()));
                } else if (Protocol.ROOM_CLOSED.equals(messageType)) {
                    listener.onRoomClosed(input.readUTF(), input.readUTF());
                } else if (Protocol.MATCH_START.equals(messageType)) {
                    listener.onMatchStart(input.readUTF(), input.readUTF(), input.readUTF());
                } else if (Protocol.GAME_STATE.equals(messageType)) {
                    listener.onGameState(readGameState());
                } else if (Protocol.GAME_OVER.equals(messageType)) {
                    listener.onGameOver(input.readUTF(), input.readUTF(), input.readUTF());
                } else if (Protocol.MATCH_RESULT.equals(messageType)) {
                    listener.onMatchResult(input.readUTF(), new MatchResultStats(readInt(), readInt(), readInt(), readInt()));
                } else if (Protocol.HIT_EFFECT.equals(messageType)) {
                    listener.onHitEffect(input.readUTF(), readInt(), readInt());
                } else if (Protocol.RANKING_RESPONSE.equals(messageType)) {
                    listener.onRanking(readRanking());
                } else if (Protocol.HISTORY_RESPONSE.equals(messageType)) {
                    listener.onHistory(readHistory());
                } else if (Protocol.PLAYER_DETAILS_RESPONSE.equals(messageType)) {
                    listener.onPlayerDetails(new PlayerDetails(readProfile(), readHistory()));
                } else if (Protocol.PLAYER_DETAILS_ERROR.equals(messageType)) {
                    listener.onPlayerDetailsError(input.readUTF(), input.readUTF());
                } else if (Protocol.LOBBY_ERROR.equals(messageType)) {
                    listener.onLobbyError(input.readUTF());
                } else {
                    throw new IOException("Server gửi message không nhận diện được.");
                }
            }
        } catch (EOFException exception) {
            closeMessage = "Server đã đóng kết nối.";
        } catch (IOException exception) {
            closeMessage = exception.getMessage() == null ? closeMessage : exception.getMessage();
        } finally {
            boolean notify = closed.compareAndSet(false, true);
            closeSocket();
            if (notify) {
                listener.onDisconnected(closeMessage);
            }
        }
    }

    private List<PlayerSummary> readPlayerList() throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 1000) {
            throw new IOException("Danh sách người chơi từ server không hợp lệ.");
        }
        List<PlayerSummary> players = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            players.add(new PlayerSummary(input.readUTF(), readInt(), input.readUTF()));
        }
        return players;
    }

    private PlayerProfile readProfile() throws IOException {
        return new PlayerProfile(input.readUTF(), readInt(), readInt(), readInt(), readInt(), readInt(),
                input.readUTF());
    }

    private int readInt() throws IOException {
        try {
            return Integer.parseInt(input.readUTF());
        } catch (NumberFormatException exception) {
            throw new IOException("Server gửi dữ liệu số không hợp lệ.", exception);
        }
    }

    private boolean readBoolean() throws IOException {
        String value = input.readUTF();
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IOException("Server gửi trạng thái sẵn sàng không hợp lệ.");
        }
        return Boolean.parseBoolean(value);
    }

    private long readLong() throws IOException {
        try {
            return Long.parseLong(input.readUTF());
        } catch (NumberFormatException exception) {
            throw new IOException("Server gửi dữ liệu số dài không hợp lệ.", exception);
        }
    }

    private GameState readGameState() throws IOException {
        String roomId = input.readUTF();
        int seconds = readInt();
        GameState.Tank player1 = new GameState.Tank(readInt(), readInt(), readInt(), readInt());
        GameState.Tank player2 = new GameState.Tank(readInt(), readInt(), readInt(), readInt());
        int count = readInt();
        if (count < 0 || count > 100) { throw new IOException("Server gửi số lượng đạn không hợp lệ."); }
        List<GameState.Bullet> bullets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bullets.add(new GameState.Bullet(input.readUTF(), readInt(), readInt()));
        }
        return new GameState(roomId, seconds, player1, player2, List.copyOf(bullets));
    }

    private List<RankingEntry> readRanking() throws IOException {
        int count = readBoundedCount("bảng xếp hạng", 1000);
        List<RankingEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new RankingEntry(input.readUTF(), readInt(), readInt(), readInt(), readInt(), readInt()));
        }
        return List.copyOf(entries);
    }

    private List<MatchHistoryEntry> readHistory() throws IOException {
        int count = readBoundedCount("lịch sử đấu", 1000);
        List<MatchHistoryEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new MatchHistoryEntry(readLong(), input.readUTF(), input.readUTF(),
                    readInt(), readInt(), input.readUTF()));
        }
        return List.copyOf(entries);
    }

    private int readBoundedCount(String label, int max) throws IOException {
        int count = readInt();
        if (count < 0 || count > max) {
            throw new IOException("Server gửi " + label + " không hợp lệ.");
        }
        return count;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeSocket();
        }
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
