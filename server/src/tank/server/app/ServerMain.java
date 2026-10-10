package tank.server.app;

import tank.server.auth.AccountService;
import tank.server.persistence.DatabaseConfig;
import tank.server.persistence.JdbcAccountRepository;
import tank.server.session.ClientHandler;
import tank.server.session.PlayerSessionManager;
import tank.server.lobby.ChallengeManager;
import tank.server.room.RoomManager;
import tank.server.game.GameStateManager;
import tank.server.persistence.MatchHistoryRepository;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class ServerMain {
    private static final int DEFAULT_PORT = 5000;
    private static final int MAX_CLIENTS = 200;

    private ServerMain() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        JdbcAccountRepository accountRepository = new JdbcAccountRepository(databaseConfig);
        accountRepository.initialize();
        AccountService accountService = new AccountService(accountRepository);
        PlayerSessionManager sessions = new PlayerSessionManager();
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "lobby-clock");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        RoomManager rooms = new RoomManager(sessions, scheduler);
        GameStateManager games = new GameStateManager(sessions, rooms, scheduler, accountService,
                new MatchHistoryRepository(databaseConfig));
        rooms.setGameListener(games);
        ChallengeManager challenges = new ChallengeManager(sessions, rooms, scheduler);
        sessions.setDisconnectListener(challenges::onDisconnect);
        ThreadPoolExecutor workers = createWorkerPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Tank game server listening on port " + port);
            System.out.println("MySQL account repository ready.");
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                try {
                    workers.execute(new ClientHandler(socket, accountService, sessions, challenges, rooms, games));
                    System.out.println("Accepted connection from " + socket.getRemoteSocketAddress());
                } catch (java.util.concurrent.RejectedExecutionException exception) {
                    socket.close();
                    System.out.println("Connection limit reached; rejected " + socket.getRemoteSocketAddress());
                }
            }
        } finally {
            scheduler.shutdownNow();
            workers.shutdownNow();
        }
    }

    private static ThreadPoolExecutor createWorkerPool() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                0,
                MAX_CLIENTS,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                task -> {
                    Thread thread = new Thread(task, "client-session-" + sequence.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
