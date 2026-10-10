package tank.server.game;

import tank.server.protocol.Protocol;
import tank.server.room.RoomManager;
import tank.server.session.PlayerSessionManager;
import tank.server.auth.AccountService;
import tank.server.persistence.MatchHistoryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Authoritative, in-memory match simulation. Clients only submit input and render snapshots. */
public final class GameStateManager implements RoomManager.GameListener {
    private static final int FIELD_WIDTH = 800;
    private static final int START_SECONDS = 180;
    private static final int START_HP = 100;
    private static final int START_AMMO = 50;
    private static final int MOVE_STEP = 24;
    private static final int BULLET_STEP = 12;
    private static final int HIT_DAMAGE = 20;
    private static final long SHOT_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(350);

    private final PlayerSessionManager sessions;
    private final RoomManager rooms;
    private final ScheduledExecutorService scheduler;
    private final AccountService accounts;
    private final MatchHistoryRepository history;
    private final Map<String, Match> matches = new ConcurrentHashMap<>();

    public GameStateManager(PlayerSessionManager sessions, RoomManager rooms,
                            ScheduledExecutorService scheduler, AccountService accounts, MatchHistoryRepository history) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.scheduler = scheduler;
        this.accounts = accounts;
        this.history = history;
    }

    @Override public void onMatchStart(RoomManager.RoomInfo room) {
        Match match = new Match(room);
        if (matches.putIfAbsent(room.roomId(), match) != null) {
            throw new IllegalStateException("Phòng đã có trận đấu.");
        }
        match.task = scheduler.scheduleAtFixedRate(() -> tick(match), 0, 50, TimeUnit.MILLISECONDS);
    }

    @Override public void onPlayerLeft(RoomManager.RoomInfo room, String username) {
        Match match = matches.remove(room.roomId());
        if (match != null && match.task != null) { match.task.cancel(false); }
    }

    public void move(String username, boolean left) {
        Match match = findPlayerMatch(username);
        if (match == null) { return; }
        synchronized (match) {
            Tank tank = match.tankFor(username);
            if (tank == null || match.finished) { return; }
            tank.x = clamp(tank.x + (left ? -MOVE_STEP : MOVE_STEP), 32, FIELD_WIDTH - 32);
            broadcast(match);
        }
    }

    public void shoot(String username) {
        Match match = findPlayerMatch(username);
        if (match == null) { return; }
        synchronized (match) {
            Tank tank = match.tankFor(username);
            if (tank == null || match.finished || tank.ammo == 0) { return; }
            long now = System.nanoTime();
            if (now - tank.lastShot < SHOT_COOLDOWN_NANOS) { return; }
            tank.lastShot = now;
            tank.ammo--;
            match.bullets.add(new Bullet(username, tank.x, tank.bottomPlayer ? 350 : 70,
                    tank.bottomPlayer ? -BULLET_STEP : BULLET_STEP));
            broadcast(match);
        }
    }

    public void exit(String username) {
        Match match = findPlayerMatch(username);
        if (match == null) { return; }
        synchronized (match) {
            if (match.finished) {
                matches.remove(match.room.roomId(), match);
                rooms.finishRoom(match.room.roomId(), username + " đã rời phòng.");
            } else {
                finish(match, match.other(username).username, username + " đã rời trận.");
                closeRoom(match, username + " đã rời trận.");
            }
        }
    }

    public void playAgain(String username) {
        Match match = findPlayerMatch(username);
        if (match == null) { return; }
        synchronized (match) {
            if (!match.finished) { return; }
            if (match.player1.username.equals(username)) { match.player1Ready = true; }
            else { match.player2Ready = true; }
            sessions.sendTo(match.player1.username, Protocol.GAME_OVER, match.room.roomId(),
                    "WAITING", "Đang chờ đối thủ chọn Chơi tiếp.");
            sessions.sendTo(match.player2.username, Protocol.GAME_OVER, match.room.roomId(),
                    "WAITING", "Đang chờ đối thủ chọn Chơi tiếp.");
            if (match.player1Ready && match.player2Ready) { reset(match); }
        }
    }

    private Match findPlayerMatch(String username) {
        for (Match match : matches.values()) {
            if (match.player1.username.equals(username) || match.player2.username.equals(username)) { return match; }
        }
        return null;
    }

    private void tick(Match match) {
        synchronized (match) {
            if (match.finished || matches.get(match.room.roomId()) != match) { return; }
            match.ticks++;
            if (match.ticks % 20 == 0 && --match.seconds <= 0) {
                String winner = match.player1.hp == match.player2.hp ? "DRAW"
                        : (match.player1.hp > match.player2.hp ? match.player1.username : match.player2.username);
                finish(match, winner, "Hết thời gian.");
                return;
            }
            for (int index = match.bullets.size() - 1; index >= 0; index--) {
                Bullet bullet = match.bullets.get(index);
                bullet.y += bullet.velocity;
                Tank target = bullet.owner.equals(match.player1.username) ? match.player2 : match.player1;
                if (hit(bullet, target)) {
                    target.hp = Math.max(0, target.hp - HIT_DAMAGE);
                    match.tankFor(bullet.owner).score++;
                    match.bullets.remove(index);
                    String[] impact = {match.room.roomId(), Integer.toString(bullet.x), Integer.toString(bullet.y)};
                    sessions.sendTo(match.player1.username, Protocol.HIT_EFFECT, impact);
                    sessions.sendTo(match.player2.username, Protocol.HIT_EFFECT, impact);
                    if (target.hp == 0) {
                        finish(match, match.other(target.username).username, target.username + " đã hết HP.");
                        return;
                    }
                } else if (bullet.y < 0 || bullet.y > 420) {
                    match.bullets.remove(index);
                }
            }
            if (match.bullets.isEmpty() && match.player1.ammo == 0 && match.player2.ammo == 0) {
                String winner = match.player1.hp == match.player2.hp ? "DRAW"
                        : (match.player1.hp > match.player2.hp ? match.player1.username : match.player2.username);
                finish(match, winner, "Cả hai người chơi đã hết đạn.");
                return;
            }
            broadcast(match);
        }
    }

    private static boolean hit(Bullet bullet, Tank tank) {
        int tankY = tank.bottomPlayer ? 360 : 40;
        return Math.abs(bullet.x - tank.x) <= 42 && bullet.y >= tankY - 12 && bullet.y <= tankY + 45;
    }

    private void finish(Match match, String winner, String reason) {
        synchronized (match) {
            if (match.finished) { return; }
            match.finished = true;
            if (match.task != null) { match.task.cancel(false); }
            // Preserve the final HP/ammo/bullet snapshot before the result notification.
            broadcast(match);
            persistResult(match, winner);
            sessions.sendTo(match.player1.username, Protocol.GAME_OVER, match.room.roomId(), winner, reason);
            sessions.sendTo(match.player2.username, Protocol.GAME_OVER, match.room.roomId(), winner, reason);
        }
    }

    private void closeRoom(Match match, String reason) {
        if (match.task != null) { match.task.cancel(false); }
        matches.remove(match.room.roomId(), match);
        rooms.finishRoom(match.room.roomId(), reason);
    }

    private void reset(Match match) {
        match.player1.reset();
        match.player2.reset();
        match.bullets.clear();
        match.seconds = START_SECONDS;
        match.ticks = 0;
        match.finished = false;
        match.player1Ready = false;
        match.player2Ready = false;
        match.task = scheduler.scheduleAtFixedRate(() -> tick(match), 0, 50, TimeUnit.MILLISECONDS);
        sessions.sendTo(match.player1.username, Protocol.MATCH_START, match.room.roomId(),
                match.player1.username, match.player2.username);
        sessions.sendTo(match.player2.username, Protocol.MATCH_START, match.room.roomId(),
                match.player1.username, match.player2.username);
        broadcast(match);
    }

    private void persistResult(Match match, String winner) {
        try {
            boolean draw = "DRAW".equals(winner);
            AccountService.Account p1 = accounts.recordResult(match.player1.username, winner.equals(match.player1.username), draw);
            AccountService.Account p2 = accounts.recordResult(match.player2.username, winner.equals(match.player2.username), draw);
            sessions.updateStatistics(p1.username(), p1.matchesPlayed(), p1.score(), p1.wins(), p1.losses(), p1.draws());
            sessions.updateStatistics(p2.username(), p2.matchesPlayed(), p2.score(), p2.wins(), p2.losses(), p2.draws());
            history.record(match.player1.username, match.player1.score, match.player2.username, match.player2.score, winner);
        } catch (java.sql.SQLException exception) {
            System.err.println("Không lưu được kết quả trận đấu: " + exception.getMessage());
        }
    }

    private void broadcast(Match match) {
        List<String> values = new ArrayList<>();
        values.add(match.room.roomId());
        values.add(Integer.toString(match.seconds));
        addTank(values, match.player1);
        addTank(values, match.player2);
        values.add(Integer.toString(match.bullets.size()));
        for (Bullet bullet : match.bullets) {
            values.add(bullet.owner);
            values.add(Integer.toString(bullet.x));
            values.add(Integer.toString(bullet.y));
        }
        String[] payload = values.toArray(String[]::new);
        sessions.sendTo(match.player1.username, Protocol.GAME_STATE, payload);
        sessions.sendTo(match.player2.username, Protocol.GAME_STATE, payload);
    }

    private static void addTank(List<String> values, Tank tank) {
        values.add(Integer.toString(tank.x));
        values.add(Integer.toString(tank.hp));
        values.add(Integer.toString(tank.ammo));
        values.add(Integer.toString(tank.score));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Match {
        final RoomManager.RoomInfo room;
        final Tank player1;
        final Tank player2;
        final List<Bullet> bullets = new ArrayList<>();
        int seconds = START_SECONDS;
        int ticks;
        boolean finished;
        boolean player1Ready;
        boolean player2Ready;
        ScheduledFuture<?> task;

        Match(RoomManager.RoomInfo room) {
            this.room = room;
            player1 = new Tank(room.player1(), FIELD_WIDTH / 2, true);
            player2 = new Tank(room.player2(), FIELD_WIDTH / 2, false);
        }
        Tank tankFor(String username) { return player1.username.equals(username) ? player1 : player2.username.equals(username) ? player2 : null; }
        Tank other(String username) { return player1.username.equals(username) ? player2 : player1; }
    }

    private static final class Tank {
        final String username;
        final boolean bottomPlayer;
        int x;
        int hp = START_HP;
        int ammo = START_AMMO;
        int score;
        long lastShot;
        Tank(String username, int x, boolean bottomPlayer) { this.username = username; this.x = x; this.bottomPlayer = bottomPlayer; }
        void reset() { x = FIELD_WIDTH / 2; hp = START_HP; ammo = START_AMMO; score = 0; lastShot = 0; }
    }

    private static final class Bullet {
        final String owner;
        final int x;
        final int velocity;
        int y;
        Bullet(String owner, int x, int y, int velocity) { this.owner = owner; this.x = x; this.y = y; this.velocity = velocity; }
    }
}
