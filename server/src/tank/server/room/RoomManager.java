package tank.server.room;

import tank.server.protocol.Protocol;
import tank.server.session.PlayerSessionManager;
import tank.server.session.PlayerStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** All transitions share the session-manager monitor with challenges and disconnects. */
public final class RoomManager {
    public record RoomInfo(String roomId, String player1, String player2) { }

    public interface GameListener {
        default void onMatchStart(RoomInfo room) { }
        default void onPlayerLeft(RoomInfo room, String username) { }
    }

    private static final class Room {
        final RoomInfo info;
        int seconds = 10;
        boolean player1Ready;
        boolean player2Ready;
        boolean started;
        ScheduledFuture<?> countdown;

        Room(RoomInfo info) { this.info = info; }
    }

    private final PlayerSessionManager sessions;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, String> roomByPlayer = new HashMap<>();
    private GameListener gameListener = new GameListener() { };

    public RoomManager(PlayerSessionManager sessions, ScheduledExecutorService scheduler) {
        this.sessions = sessions;
        this.scheduler = scheduler;
    }

    public void setGameListener(GameListener listener) {
        synchronized (sessions) { gameListener = Objects.requireNonNull(listener); }
    }

    /** Called by ChallengeManager after atomically accepting a valid invitation. */
    public RoomInfo createRoom(String player1, String player2) {
        synchronized (sessions) {
            if (!sessions.isOnline(player1) || !sessions.isOnline(player2)
                    || player1.equals(player2) || roomByPlayer.containsKey(player1)
                    || roomByPlayer.containsKey(player2)) {
                throw new IllegalStateException("Người chơi không thể vào phòng.");
            }
            Room room = new Room(new RoomInfo(UUID.randomUUID().toString(), player1, player2));
            rooms.put(room.info.roomId(), room);
            roomByPlayer.put(player1, room.info.roomId());
            roomByPlayer.put(player2, room.info.roomId());
            // Don't broadcast yet - will be done after ROOM_STATE is sent
            room.countdown = scheduler.scheduleAtFixedRate(
                    () -> tick(room.info.roomId()), 1, 1, TimeUnit.SECONDS);
            // Send ROOM_STATE first before broadcasting player list
            broadcastState(room);
            return room.info;
        }
    }

    private void tick(String roomId) {
        synchronized (sessions) {
            Room room = rooms.get(roomId);
            if (room == null || room.started) { return; }
            if (!sessions.isOnline(room.info.player1()) || !sessions.isOnline(room.info.player2())) {
                closeRoom(room, "Một người chơi đã mất kết nối.");
                return;
            }
            if (--room.seconds > 0) {
                broadcastState(room);
                return;
            }
            room.started = true;
            room.countdown.cancel(false);
            sessions.setStatuses(PlayerStatus.PLAYING, room.info.player1(), room.info.player2());
            // The listener can register the authoritative match before clients receive MATCH_START.
            if (!notifyMatchStart(room.info)) {
                closeRoom(room, "Không thể khởi động gameplay.");
                return;
            }
            if (!rooms.containsKey(roomId)) { return; }
            sendBoth(room, Protocol.MATCH_START, room.info.roomId(), room.info.player1(), room.info.player2());
        }
    }

    public void leave(String username, String roomId) {
        synchronized (sessions) {
            Room room = rooms.get(roomId);
            if (room == null || !roomId.equals(roomByPlayer.get(username))) {
                sessions.sendTo(username, Protocol.LOBBY_ERROR, "Bạn không ở trong phòng này.");
                return;
            }
            if (room.started) { notifyPlayerLeft(room.info, username); }
            closeRoom(room, username + " đã rời phòng.");
        }
    }

    /** Records a player's one-way ready signal and shortens the countdown once both are ready. */
    public void ready(String username, String roomId) {
        synchronized (sessions) {
            Room room = rooms.get(roomId);
            if (room == null || !roomId.equals(roomByPlayer.get(username))) {
                sessions.sendTo(username, Protocol.LOBBY_ERROR, "Bạn không ở trong phòng này.");
                return;
            }
            if (room.started) {
                sessions.sendTo(username, Protocol.LOBBY_ERROR, "Trận đấu đã bắt đầu.");
                return;
            }
            if (room.info.player1().equals(username)) { room.player1Ready = true; }
            else if (room.info.player2().equals(username)) { room.player2Ready = true; }
            if (room.player1Ready && room.player2Ready) {
                room.seconds = Math.min(room.seconds, 3);
            }
            broadcastState(room);
        }
    }

    public void onDisconnect(String username) {
        synchronized (sessions) {
            Room room = rooms.get(roomByPlayer.get(username));
            if (room != null) {
                if (room.started) { notifyPlayerLeft(room.info, username); }
                closeRoom(room, username + " đã mất kết nối.");
            }
        }
    }

    /** Integration hook for the result module; safe to call more than once. */
    public void finishRoom(String roomId, String reason) {
        synchronized (sessions) {
            Room room = rooms.get(roomId);
            if (room != null) { closeRoom(room, reason); }
        }
    }

    private void closeRoom(Room room, String reason) {
        if (rooms.remove(room.info.roomId()) == null) { return; }
        if (room.countdown != null) { room.countdown.cancel(false); }
        roomByPlayer.remove(room.info.player1());
        roomByPlayer.remove(room.info.player2());
        sendBoth(room, Protocol.ROOM_CLOSED, room.info.roomId(), reason);
        sessions.setStatuses(PlayerStatus.ONLINE, room.info.player1(), room.info.player2());
    }

    private void broadcastState(Room room) {
        sendBoth(room, Protocol.ROOM_STATE, room.info.roomId(), room.info.player1(),
                room.info.player2(), Integer.toString(room.seconds),
                Boolean.toString(room.player1Ready), Boolean.toString(room.player2Ready));
    }

    private void sendBoth(Room room, String type, String... values) {
        sessions.sendTo(room.info.player1(), type, values);
        sessions.sendTo(room.info.player2(), type, values);
    }

    private boolean notifyMatchStart(RoomInfo room) {
        try { gameListener.onMatchStart(room); return true; }
        catch (RuntimeException exception) {
            System.err.println("Gameplay callback failed: " + exception.getMessage());
            return false;
        }
    }

    private void notifyPlayerLeft(RoomInfo room, String username) {
        try { gameListener.onPlayerLeft(room, username); }
        catch (RuntimeException exception) {
            System.err.println("Player-left callback failed: " + exception.getMessage());
        }
    }
}
