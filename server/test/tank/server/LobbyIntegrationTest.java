package tank.server;

import tank.client.model.PlayerProfile;
import tank.client.model.PlayerSummary;
import tank.client.model.RoomState;
import tank.client.network.ClientConnection;
import tank.server.auth.AccountService;
import tank.server.lobby.ChallengeManager;
import tank.server.persistence.AccountRecord;
import tank.server.persistence.AccountRepository;
import tank.server.room.RoomManager;
import tank.server.session.ClientHandler;
import tank.server.session.PlayerSessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Runs the production socket handler and ClientConnection without an external database. */
public final class LobbyIntegrationTest {
    private static final String PASSWORD = "password123";
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (Fixture server = new Fixture();
             Probe alice = server.login("alice");
             Probe bob = server.login("bob");
             Probe carol = server.login("carol")) {
            alice.await(0, "ROSTER", event -> event.values.size() == 3);
            bob.await(0, "ROSTER", event -> event.values.size() == 3);
            check("ONLINE".equals(alice.profile.getStatus()), "login profile");
            check(server.sessions.getStatus("alice").name().equals("ONLINE"), "online status");

            int mark = alice.mark();
            alice.connection.challenge("alice");
            alice.await(mark, "ERROR");
            mark = alice.mark();
            alice.connection.challenge("nobody");
            alice.await(mark, "ERROR");

            try (Probe guest = new Probe(server.port())) {
                mark = guest.mark();
                guest.connection.challenge("alice");
                guest.await(mark, "ERROR");
                // A rejected unauthenticated action must consume its payload before the next message.
                guest.connection.login("alice", PASSWORD);
                guest.await(mark, "LOGIN_FAIL");
            }

            String id = invite(alice, bob);
            mark = carol.mark();
            carol.connection.challenge("bob");
            carol.await(mark, "ERROR");
            mark = carol.mark();
            carol.connection.acceptChallenge(id);
            carol.await(mark, "ERROR");
            mark = alice.mark();
            String cancelledInvitation = id;
            alice.connection.cancelChallenge(id);
            alice.await(mark, "CLOSED", event -> event.values.get(0).equals(cancelledInvitation));
            bob.await(0, "CLOSED", event -> event.values.get(0).equals(cancelledInvitation));
            awaitOnline(alice, bob);

            id = invite(alice, bob);
            String rejected = id;
            mark = bob.mark();
            bob.connection.rejectChallenge(id);
            bob.await(mark, "CLOSED", event -> event.values.get(0).equals(rejected));
            alice.await(0, "CLOSED", event -> event.values.get(0).equals(rejected));
            awaitOnline(alice, bob);

            id = invite(alice, bob);
            String expired = id;
            alice.await(0, "CLOSED", event -> event.values.get(0).equals(expired));
            awaitOnline(alice, bob);
            mark = bob.mark();
            bob.connection.acceptChallenge(id);
            bob.await(mark, "ERROR");

            // A delayed scheduler must not let an already-expired invitation be accepted.
            var releaseClock = new java.util.concurrent.CountDownLatch(1);
            var clockBlocked = new java.util.concurrent.CountDownLatch(1);
            server.clock.execute(() -> {
                clockBlocked.countDown();
                try { releaseClock.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            check(clockBlocked.await(3, TimeUnit.SECONDS), "clock blocked for expiry boundary test");
            try {
                id = invite(alice, bob);
                String delayedExpiry = id;
                alice.assertAbsent(alice.mark(), "CLOSED", event -> event.values.get(0).equals(delayedExpiry), 2100);
                mark = bob.mark();
                bob.connection.acceptChallenge(id);
                bob.await(mark, "CLOSED", event -> event.values.get(0).equals(delayedExpiry));
                awaitOnline(alice, bob);
            } finally { releaseClock.countDown(); }

            // An accepted invitation's old expiry must not release its players from the room.
            id = invite(alice, bob);
            int aStart = alice.mark();
            int bStart = bob.mark();
            bob.connection.acceptChallenge(id);
            RoomState firstRoom = room(alice.await(aStart, "ROOM"));
            RoomState otherRoom = room(bob.await(bStart, "ROOM"));
            check(firstRoom.equals(otherRoom), "same room and initial countdown");
            check(firstRoom.player1().equals("alice") && firstRoom.player2().equals("bob"), "player slots");
            mark = bob.mark();
            bob.connection.acceptChallenge(id);
            bob.await(mark, "ERROR");
            mark = carol.mark();
            carol.connection.leaveRoom(firstRoom.roomId());
            carol.await(mark, "ERROR");
            alice.await(aStart, "START", event -> event.values.get(0).equals(firstRoom.roomId()));
            bob.await(bStart, "START", event -> event.values.get(0).equals(firstRoom.roomId()));
            check(alice.count(aStart, "START") == 1 && bob.count(bStart, "START") == 1, "single MATCH_START");
            check(alice.countdown(aStart).equals(List.of("3", "2", "1")), "server countdown 3-2-1");
            check(alice.countdown(aStart).equals(bob.countdown(bStart)), "matching countdowns");
            check(server.starts.get() == 1, "gameplay start callback");
            check(server.sessions.getStatus("bob").name().equals("PLAYING"), "playing status");
            mark = alice.mark();
            alice.connection.leaveRoom(firstRoom.roomId());
            alice.await(mark, "ROOM_CLOSED");
            awaitOnline(alice, bob);
            check(server.leaves.get() == 1, "active-room exit callback");
            server.rooms.finishRoom(firstRoom.roomId(), "duplicate finish");

            // Leaving during countdown cancels the scheduled start.
            id = invite(alice, bob);
            aStart = alice.mark();
            bob.connection.acceptChallenge(id);
            RoomState cancelledRoom = room(alice.await(aStart, "ROOM"));
            alice.connection.leaveRoom(cancelledRoom.roomId());
            alice.await(aStart, "ROOM_CLOSED");
            awaitOnline(alice, bob);

            // Two concurrent requests to the same player: exactly one invitation succeeds.
            int raceAlice = alice.mark(), raceBob = bob.mark(), raceCarol = carol.mark();
            try (var barrier = new Race(alice, bob, carol)) { barrier.run(); }
            Event received = bob.await(raceBob, "RECEIVED");
            String raceId = received.values.get(0);
            boolean aliceWon = received.values.get(1).equals("alice");
            Probe winner = aliceWon ? alice : carol;
            Probe loser = aliceWon ? carol : alice;
            loser.await(aliceWon ? raceCarol : raceAlice, "ERROR");
            winner.await(aliceWon ? raceAlice : raceCarol, "PENDING");
            bob.connection.rejectChallenge(raceId);
            bob.await(raceBob, "CLOSED");
            awaitOnline(alice, bob);
            check(bob.count(raceBob, "RECEIVED") == 1, "one invitation in concurrent race");

            // Abrupt disconnect cancels invitations, including on a later reconnect.
            id = invite(carol, bob);
            int bDisconnect = bob.mark();
            carol.close();
            bob.await(bDisconnect, "CLOSED");
            bob.await(bDisconnect, "ROSTER", event -> !event.values.contains("carol:ONLINE")
                    && event.values.contains("bob:ONLINE") && event.values.size() == 2);
            try (Probe reconnected = server.login("carol")) {
                id = invite(reconnected, bob);
                int bRoom = bob.mark();
                bob.connection.acceptChallenge(id);
                bob.await(bRoom, "ROOM");
                reconnected.close();
                bob.await(bRoom, "ROOM_CLOSED");
            }

            // Give the cancelled countdown enough time to have fired if cancellation were broken.
            bob.assertAbsent(bob.mark(), "START", event -> event.values.get(0).equals(cancelledRoom.roomId()), 3300);
            check(server.starts.get() == 1, "cancelled/disconnected rooms never start");

            // Existing register and logout paths still use the same framing/acknowledgement.
            try (Probe registered = new Probe(server.port())) {
                registered.connection.register("dave", "dave@example.test", PASSWORD);
                registered.await(0, "REGISTER_OK");
                registered.connection.login("dave", PASSWORD);
                registered.await(0, "LOGIN_OK");
                int before = alice.mark();
                registered.connection.logout();
                registered.await(0, "LOGOUT");
                alice.await(before, "ROSTER", event -> event.values.size() == 2);
            }
            System.out.println("PASS: " + checks + " checks, real TCP with three clients; no MySQL required.");
        }
    }

    private static String invite(Probe sender, Probe receiver) throws Exception {
        int sent = sender.mark(), got = receiver.mark();
        sender.connection.challenge(receiver.username);
        Event pending = sender.await(sent, "PENDING");
        Event incoming = receiver.await(got, "RECEIVED");
        check(pending.values.get(0).equals(incoming.values.get(0)), "same challenge id");
        return pending.values.get(0);
    }

    private static void awaitOnline(Probe first, Probe second) throws Exception {
        first.await(0, "ROSTER", event -> event.values.contains(first.username + ":ONLINE")
                && event.values.contains(second.username + ":ONLINE"));
        second.await(0, "ROSTER", event -> event.values.contains(first.username + ":ONLINE")
                && event.values.contains(second.username + ":ONLINE"));
        // Wait for the newest server snapshot, rather than relying only on a historical event.
        first.awaitLatestOnline(second.username);
        second.awaitLatestOnline(first.username);
    }

    private static RoomState room(Event event) {
        return new RoomState(event.values.get(0), event.values.get(1), event.values.get(2),
                Integer.parseInt(event.values.get(3)));
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }

    private record Event(String type, List<String> values) { }

    private static final class Probe implements ClientConnection.Listener, AutoCloseable {
        final ClientConnection connection = new ClientConnection(this);
        final List<Event> events = new ArrayList<>();
        volatile PlayerProfile profile;
        String username;
        List<String> latestRoster = List.of();

        Probe(int port) throws IOException { connection.connect("127.0.0.1", port); }
        synchronized int mark() { return events.size(); }
        synchronized void add(String type, String... values) {
            events.add(new Event(type, List.of(values)));
            notifyAll();
        }
        Event await(int start, String type) throws Exception { return await(start, type, event -> true); }
        synchronized Event await(int start, String type, Predicate<Event> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (true) {
                for (int i = start; i < events.size(); i++) {
                    Event event = events.get(i);
                    if (event.type.equals(type) && predicate.test(event)) { checks++; return event; }
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) { throw new AssertionError("Timeout waiting for " + username + " " + type + ": " + events); }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }
        synchronized void awaitLatestOnline(String other) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!latestRoster.contains(username + ":ONLINE") || !latestRoster.contains(other + ":ONLINE")) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) { throw new AssertionError("Players did not return to lobby: " + latestRoster); }
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }
        synchronized int count(int start, String type) {
            return (int) events.subList(start, events.size()).stream().filter(event -> event.type.equals(type)).count();
        }
        synchronized List<String> countdown(int start) {
            return events.subList(start, events.size()).stream().filter(event -> event.type.equals("ROOM"))
                    .map(event -> event.values.get(3)).toList();
        }
        synchronized void assertAbsent(int start, String type, Predicate<Event> predicate, int millis) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (System.nanoTime() < deadline) {
                if (events.subList(start, events.size()).stream().anyMatch(event -> event.type.equals(type) && predicate.test(event))) {
                    throw new AssertionError("Unexpected " + type);
                }
                TimeUnit.NANOSECONDS.timedWait(this, Math.max(1, deadline - System.nanoTime()));
            }
            checks++;
        }
        @Override public void onLoginSuccess(PlayerProfile value) { profile = value; add("LOGIN_OK"); }
        @Override public void onLoginFailure(String reason) { add("LOGIN_FAIL", reason); }
        @Override public void onRegisterResult(boolean ok, String reason) { add(ok ? "REGISTER_OK" : "REGISTER_FAIL", reason); }
        @Override public synchronized void onPlayerList(List<PlayerSummary> players) {
            latestRoster = players.stream().map(player -> player.getUsername() + ":" + player.getStatus()).toList();
            add("ROSTER", latestRoster.toArray(String[]::new));
        }
        @Override public void onLogoutSuccess() { add("LOGOUT"); connection.close(); }
        @Override public void onDisconnected(String reason) { add("DISCONNECTED", reason); }
        @Override public void onChallengePending(String id, String opponent, int timeout) { add("PENDING", id, opponent); }
        @Override public void onChallengeReceived(String id, String opponent, int timeout) { add("RECEIVED", id, opponent); }
        @Override public void onChallengeClosed(String id, String reason) { add("CLOSED", id, reason); }
        @Override public void onRoomState(RoomState room) {
            add("ROOM", room.roomId(), room.player1(), room.player2(), Integer.toString(room.secondsRemaining()));
        }
        @Override public void onRoomClosed(String id, String reason) { add("ROOM_CLOSED", id, reason); }
        @Override public void onMatchStart(String id, String player1, String player2) { add("START", id, player1, player2); }
        @Override public void onLobbyError(String reason) { add("ERROR", reason); }
        @Override public void close() { connection.close(); }
    }

    private static final class Race implements AutoCloseable {
        private final ExecutorService workers = Executors.newFixedThreadPool(2);
        private final Probe alice, bob, carol;
        Race(Probe alice, Probe bob, Probe carol) { this.alice = alice; this.bob = bob; this.carol = carol; }
        void run() throws Exception {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var first = workers.submit(() -> { gate.await(); alice.connection.challenge(bob.username); return null; });
            var second = workers.submit(() -> { gate.await(); carol.connection.challenge(bob.username); return null; });
            gate.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
        }
        @Override public void close() { workers.shutdownNow(); }
    }

    private static final class Fixture implements AutoCloseable {
        final PlayerSessionManager sessions = new PlayerSessionManager();
        final ScheduledThreadPoolExecutor clock = new ScheduledThreadPoolExecutor(1);
        final RoomManager rooms = new RoomManager(sessions, clock);
        final ChallengeManager challenges = new ChallengeManager(sessions, rooms, clock, 2);
        final AccountService accounts = new AccountService(new MemoryAccounts());
        final ServerSocket listener = new ServerSocket(0);
        final ExecutorService workers = Executors.newCachedThreadPool();
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final AtomicInteger starts = new AtomicInteger(), leaves = new AtomicInteger();

        Fixture() throws Exception {
            clock.setRemoveOnCancelPolicy(true);
            sessions.setDisconnectListener(challenges::onDisconnect);
            rooms.setGameListener(new RoomManager.GameListener() {
                @Override public void onMatchStart(RoomManager.RoomInfo room) { starts.incrementAndGet(); }
                @Override public void onPlayerLeft(RoomManager.RoomInfo room, String username) { leaves.incrementAndGet(); }
            });
            for (String name : List.of("alice", "bob", "carol")) {
                check(accounts.register(name, name + "@example.test", PASSWORD.toCharArray()) == null, "seed account");
            }
            workers.execute(() -> {
                try {
                    while (!listener.isClosed()) {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        workers.execute(new ClientHandler(socket, accounts, sessions, challenges, rooms));
                    }
                } catch (IOException exception) {
                    if (!listener.isClosed()) { throw new RuntimeException(exception); }
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        Probe login(String name) throws Exception {
            Probe probe = new Probe(port());
            probe.username = name;
            probe.connection.login(name, PASSWORD);
            probe.await(0, "LOGIN_OK");
            return probe;
        }
        @Override public void close() throws Exception {
            listener.close();
            for (Socket socket : sockets) { socket.close(); }
            workers.shutdownNow();
            workers.awaitTermination(3, TimeUnit.SECONDS);
            clock.shutdownNow();
            clock.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    private static final class MemoryAccounts implements AccountRepository {
        final Map<String, AccountRecord> records = new ConcurrentHashMap<>();
        @Override public void initialize() { }
        @Override public void createAccount(String name, String email, byte[] salt, byte[] hash)
                throws SQLIntegrityConstraintViolationException {
            if (records.putIfAbsent(name, new AccountRecord(name, email, salt, hash, 0, 0, 0, 0, 0)) != null) {
                throw new SQLIntegrityConstraintViolationException("Duplicate account");
            }
        }
        @Override public Optional<AccountRecord> findByUsername(String name) { return Optional.ofNullable(records.get(name)); }
        @Override public boolean updateStatistics(String name, int played, int score, int wins, int losses, int draws) {
            return records.computeIfPresent(name, (key, old) -> new AccountRecord(name, old.email(), old.passwordSalt(),
                    old.passwordHash(), played, score, wins, losses, draws)) != null;
        }
    }
}
