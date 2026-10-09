package tank.client.network;

import tank.client.model.PlayerSummary;
import tank.client.model.PlayerProfile;
import tank.client.model.RoomState;
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
        void onLogoutSuccess();
        void onDisconnected(String message);
        default void onChallengePending(String id, String opponent, int timeoutSeconds) { }
        default void onChallengeReceived(String id, String opponent, int timeoutSeconds) { }
        default void onChallengeClosed(String id, String reason) { }
        default void onRoomState(RoomState room) { }
        default void onRoomClosed(String roomId, String reason) { }
        default void onMatchStart(String roomId, String player1, String player2) { }
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
                    listener.onLoginSuccess(new PlayerProfile(
                            input.readUTF(),
                            readInt(),
                            readInt(),
                            readInt(),
                            readInt(),
                            readInt(),
                            input.readUTF()));
                } else if (Protocol.LOGIN_FAIL.equals(messageType)) {
                    listener.onLoginFailure(input.readUTF());
                } else if (Protocol.REGISTER_SUCCESS.equals(messageType)) {
                    listener.onRegisterResult(true, input.readUTF());
                } else if (Protocol.REGISTER_FAIL.equals(messageType)) {
                    listener.onRegisterResult(false, input.readUTF());
                } else if (Protocol.PLAYER_LIST.equals(messageType)) {
                    listener.onPlayerList(readPlayerList());
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
                    listener.onRoomState(new RoomState(input.readUTF(), input.readUTF(), input.readUTF(), readInt()));
                } else if (Protocol.ROOM_CLOSED.equals(messageType)) {
                    listener.onRoomClosed(input.readUTF(), input.readUTF());
                } else if (Protocol.MATCH_START.equals(messageType)) {
                    listener.onMatchStart(input.readUTF(), input.readUTF(), input.readUTF());
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

    private int readInt() throws IOException {
        try {
            return Integer.parseInt(input.readUTF());
        } catch (NumberFormatException exception) {
            throw new IOException("Server gửi dữ liệu số không hợp lệ.", exception);
        }
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
