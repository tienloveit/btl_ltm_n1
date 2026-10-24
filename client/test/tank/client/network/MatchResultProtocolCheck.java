package tank.client.network;

import tank.client.model.MatchResultStats;
import tank.client.model.PlayerSummary;
import tank.client.model.PlayerProfile;
import tank.client.protocol.Protocol;

import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies the production UTF reader decodes the result-stats payload in wire order. */
public final class MatchResultProtocolCheck {
    private MatchResultProtocolCheck() { }

    public static int run() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0);
             ClientConnection client = new ClientConnection(new ClientConnection.Listener() {
                 @Override public void onLoginSuccess(PlayerProfile profile) { }
                 @Override public void onLoginFailure(String message) { }
                 @Override public void onRegisterResult(boolean success, String message) { }
                 @Override public void onPlayerList(List<PlayerSummary> players) { }
                 @Override public void onLogoutSuccess() { }
                 @Override public void onDisconnected(String message) { }
                 @Override public void onMatchResult(String roomId, MatchResultStats stats) {
                     result.set(roomId + ":" + stats.player1Points() + ":" + stats.player1TotalScore()
                             + ":" + stats.player2Points() + ":" + stats.player2TotalScore());
                     received.countDown();
                 }
             })) {
            ExecutorService sender = Executors.newSingleThreadExecutor();
            try {
                var response = sender.submit(() -> {
                    try {
                        try (Socket socket = server.accept();
                             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                            output.writeUTF(Protocol.MATCH_RESULT);
                            output.writeUTF("room-42");
                            output.writeUTF("3");
                            output.writeUTF("16");
                            output.writeUTF("0");
                            output.writeUTF("7");
                            output.flush();
                        }
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
                client.connect("127.0.0.1", server.getLocalPort());
                if (!received.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("MATCH_RESULT was not decoded");
                }
                response.get(3, TimeUnit.SECONDS);
                if (!"room-42:3:16:0:7".equals(result.get())) {
                    throw new AssertionError("MATCH_RESULT field order changed: " + result.get());
                }
                return 1;
            } finally {
                sender.shutdownNow();
                sender.awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }
}
