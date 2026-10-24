package tank.server;

import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import tank.client.model.RoomState;
import tank.client.network.ClientConnection;

import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Replays both ROOM_STATE wire formats, including the reported next-header-as-boolean failure. */
public final class RoomStateProtocolTest {
    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " room protocol checks");
    }

    public static int run() throws Exception {
        int checks = 0;
        for (String flags : List.of("legacy", "false,false", "true,false", "false,true", "true,true", "bad,false")) {
            List<RoomState> rooms = new CopyOnWriteArrayList<>();
            AtomicReference<String> disconnected = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (ServerSocket server = new ServerSocket(0);
                 ClientConnection client = new ClientConnection(new ClientConnection.Listener() {
                     public void onLoginSuccess(PlayerProfile profile) { }
                     public void onLoginFailure(String message) { }
                     public void onRegisterResult(boolean success, String message) { }
                     public void onPlayerList(List<PlayerSummary> players) { }
                     public void onLogoutSuccess() { }
                     public void onRoomState(RoomState room) { rooms.add(room); }
                     public void onMatchStart(String id, String p1, String p2) { done.countDown(); }
                     public void onDisconnected(String message) { disconnected.set(message); done.countDown(); }
                 })) {
                var sender = Executors.newSingleThreadExecutor();
                try {
                    var sent = sender.submit(() -> {
                        try (Socket socket = server.accept();
                             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                            for (int seconds : List.of(10, 9)) {
                                for (String field : List.of("ROOM_STATE", "room", "alice", "bob", "" + seconds)) {
                                    out.writeUTF(field);
                                    out.flush();
                                }
                                if (!flags.equals("legacy")) {
                                    for (String flag : flags.split(",")) { out.writeUTF(flag); }
                                }
                                out.flush();
                            }
                            for (String field : List.of("MATCH_START", "room", "alice", "bob")) { out.writeUTF(field); }
                            out.flush();
                            if (!release.await(5, TimeUnit.SECONDS)) { throw new AssertionError("reader stalled"); }
                        }
                        return null;
                    });
                    client.connect("127.0.0.1", server.getLocalPort());
                    if (!done.await(4, TimeUnit.SECONDS)) { throw new AssertionError("ROOM_STATE reader stalled: " + flags); }
                    if (flags.startsWith("bad")) {
                        if (disconnected.get() == null || !rooms.isEmpty()) {
                            throw new AssertionError("Malformed flags must be rejected");
                        }
                    } else {
                        if (disconnected.get() != null || rooms.size() != 2) {
                            throw new AssertionError("ROOM_STATE lost framing for " + flags + ": " + disconnected.get());
                        }
                        boolean p1 = flags.startsWith("true"), p2 = flags.endsWith("true");
                        if (rooms.get(0).secondsRemaining() != 10 || rooms.get(1).secondsRemaining() != 9
                                || rooms.stream().anyMatch(r -> r.player1Ready() != p1 || r.player2Ready() != p2)) {
                            throw new AssertionError("ROOM_STATE fields changed: " + rooms);
                        }
                        if (flags.equals("legacy")) {
                            try {
                                client.readyRoom("room");
                                throw new AssertionError("Do not send ROOM_READY to a legacy server");
                            } catch (java.io.IOException expected) { }
                        }
                    }
                    checks++;
                    release.countDown();
                    // A malformed frame can cause a broken pipe in the synthetic sender.
                    try { sent.get(3, TimeUnit.SECONDS); }
                    catch (java.util.concurrent.ExecutionException exception) {
                        if (!flags.startsWith("bad")) { throw exception; }
                    }
                } finally {
                    release.countDown();
                    sender.shutdownNow();
                    sender.awaitTermination(3, TimeUnit.SECONDS);
                }
            }
        }
        return checks;
    }
}
