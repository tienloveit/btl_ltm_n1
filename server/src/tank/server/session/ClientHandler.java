package tank.server.session;

import tank.server.auth.AccountService;
import tank.server.protocol.Protocol;
import tank.server.lobby.ChallengeManager;
import tank.server.room.RoomManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final AccountService accountService;
    private final PlayerSessionManager sessions;
    private final ChallengeManager challenges;
    private final RoomManager rooms;
    private final BlockingQueue<OutgoingMessage> outgoing = new ArrayBlockingQueue<>(256);
    private Thread writer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private DataInputStream input;
    private DataOutputStream output;
    private volatile String username;
    private volatile int matchesPlayed;
    private volatile int score;
    private volatile int wins;
    private volatile int losses;
    private volatile int draws;
    private volatile PlayerStatus status = PlayerStatus.ONLINE;

    public ClientHandler(Socket socket, AccountService accountService, PlayerSessionManager sessions,
                         ChallengeManager challenges, RoomManager rooms) {
        this.socket = socket;
        this.accountService = accountService;
        this.sessions = sessions;
        this.challenges = challenges;
        this.rooms = rooms;
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket;
             DataInputStream clientInput = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream clientOutput = new DataOutputStream(clientSocket.getOutputStream())) {
            input = clientInput;
            output = clientOutput;
            clientSocket.setKeepAlive(true);
            writer = new Thread(this::writeLoop, "client-writer-" + socket.getPort());
            writer.setDaemon(true);
            writer.start();
            while (!closed.get()) {
                String messageType = input.readUTF();
                if (Protocol.REGISTER_REQUEST.equals(messageType)) {
                    handleRegister();
                } else if (Protocol.LOGIN_REQUEST.equals(messageType)) {
                    handleLogin();
                } else if (Protocol.LOGOUT_REQUEST.equals(messageType)) {
                    // The writer closes only after the acknowledgement has reached the socket.
                    CountDownLatch acknowledged = new CountDownLatch(1);
                    enqueue(stream -> {
                        stream.writeUTF(Protocol.LOGOUT_SUCCESS);
                        stream.flush();
                        acknowledged.countDown();
                        close();
                    });
                    // Do not accept more actions from a logging-out session.
                    sessions.remove(this);
                    acknowledged.await(2, TimeUnit.SECONDS);
                    break;
                } else if (Protocol.CHALLENGE.equals(messageType)) {
                    String target = input.readUTF();
                    if (requireLogin()) { challenges.challenge(username, target); }
                } else if (Protocol.ACCEPT.equals(messageType)) {
                    String id = input.readUTF();
                    if (requireLogin()) { challenges.accept(username, id); }
                } else if (Protocol.REJECT.equals(messageType)) {
                    String id = input.readUTF();
                    if (requireLogin()) { challenges.reject(username, id); }
                } else if (Protocol.CHALLENGE_CANCEL.equals(messageType)) {
                    String id = input.readUTF();
                    if (requireLogin()) { challenges.cancel(username, id); }
                } else if (Protocol.ROOM_LEAVE.equals(messageType)) {
                    String id = input.readUTF();
                    if (requireLogin()) { rooms.leave(username, id); }
                } else {
                    send(Protocol.SERVER_ERROR, "Message không được hỗ trợ.");
                }
            }
        } catch (EOFException exception) {
            System.out.println("Client disconnected" + clientLabel());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (SQLException exception) {
            System.err.println("Database operation failed" + clientLabel() + ": " + exception.getMessage());
            try {
                send(Protocol.SERVER_ERROR, "Không thể truy cập MySQL.");
            } catch (IOException ignored) {
            }
        } catch (IOException exception) {
            if (!closed.get()) {
                System.out.println("Connection ended" + clientLabel() + ": " + exception.getMessage());
            }
        } finally {
            close();
            sessions.remove(this);
        }
    }

    private void handleRegister() throws IOException, SQLException {
        String requestedUsername = input.readUTF();
        String requestedEmail = input.readUTF();
        char[] password = input.readUTF().toCharArray();
        if (username != null) {
            java.util.Arrays.fill(password, '\0');
            send(Protocol.REGISTER_FAIL, "Không thể đăng ký khi đã đăng nhập.");
            return;
        }
        String error;
        try {
            error = accountService.register(requestedUsername, requestedEmail, password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        if (error == null) {
            send(Protocol.REGISTER_SUCCESS, "Tạo tài khoản thành công. Hãy đăng nhập.");
        } else {
            send(Protocol.REGISTER_FAIL, error);
        }
    }

    private void handleLogin() throws IOException, SQLException {
        String requestedUsername = input.readUTF();
        char[] password = input.readUTF().toCharArray();
        if (username != null) {
            java.util.Arrays.fill(password, '\0');
            send(Protocol.LOGIN_FAIL, "Kết nối này đã đăng nhập.");
            return;
        }
        AccountService.Account account;
        try {
            account = accountService.authenticate(requestedUsername, password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        if (account == null) {
            send(Protocol.LOGIN_FAIL, "Username hoặc mật khẩu không đúng.");
            return;
        }

        username = account.username();
        matchesPlayed = account.matchesPlayed();
        score = account.score();
        wins = account.wins();
        losses = account.losses();
        draws = account.draws();
        status = PlayerStatus.ONLINE;
        synchronized (sessions) {
            if (!sessions.add(this)) {
                username = null;
                status = PlayerStatus.ONLINE;
                send(Protocol.LOGIN_FAIL, "Tài khoản đang đăng nhập ở một kết nối khác.");
                return;
            }

            send(Protocol.LOGIN_SUCCESS, username, Integer.toString(matchesPlayed), Integer.toString(score),
                Integer.toString(wins), Integer.toString(losses), Integer.toString(draws), status.name());
            sessions.broadcastPlayerList();
        }
    }

    void sendPlayerList(List<PlayerSessionManager.PlayerSummary> players) throws IOException {
        enqueue(stream -> {
            stream.writeUTF(Protocol.PLAYER_LIST);
            stream.writeInt(players.size());
            for (PlayerSessionManager.PlayerSummary player : players) {
                stream.writeUTF(player.getUsername());
                stream.writeUTF(Integer.toString(player.getScore()));
                stream.writeUTF(player.getStatus());
            }
        });
    }

    public void send(String messageType, String... values) throws IOException {
        String[] payload = values.clone();
        enqueue(stream -> {
            stream.writeUTF(messageType);
            for (String value : payload) { stream.writeUTF(value); }
        });
    }

    private boolean requireLogin() throws IOException {
        if (username == null) {
            send(Protocol.LOBBY_ERROR, "Bạn cần đăng nhập trước.");
            return false;
        }
        return true;
    }

    private void enqueue(OutgoingMessage message) throws IOException {
        if (closed.get() || output == null || !outgoing.offer(message)) {
            close();
            throw new IOException("Client đã ngắt kết nối hoặc nhận dữ liệu quá chậm.");
        }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                outgoing.take().write(output);
                output.flush();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            close();
        }
    }

    @FunctionalInterface
    private interface OutgoingMessage {
        void write(DataOutputStream output) throws IOException;
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            if (writer != null) { writer.interrupt(); }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isClosed() { return closed.get(); }

    String getUsername() {
        return username;
    }

    int getScore() {
        return score;
    }

    PlayerStatus getStatus() {
        return status;
    }

    void setStatus(PlayerStatus status) {
        this.status = status;
    }

    private String clientLabel() {
        return username == null ? "" : " for " + username;
    }
}
