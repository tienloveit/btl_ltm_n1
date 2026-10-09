package tank.server.lobby;

import tank.server.protocol.Protocol;
import tank.server.room.RoomManager;
import tank.server.session.PlayerSessionManager;
import tank.server.session.PlayerStatus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ChallengeManager {
    private static final class Challenge {
        final String id = UUID.randomUUID().toString();
        final String sender;
        final String receiver;
        final long deadline;
        ScheduledFuture<?> expiry;

        Challenge(String sender, String receiver, int timeoutSeconds) {
            this.sender = sender;
            this.receiver = receiver;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        }
    }

    private final PlayerSessionManager sessions;
    private final RoomManager rooms;
    private final ScheduledExecutorService scheduler;
    private final int timeoutSeconds;
    private final Map<String, Challenge> invitations = new HashMap<>();
    private final Map<String, String> invitationByPlayer = new HashMap<>();

    public ChallengeManager(PlayerSessionManager sessions, RoomManager rooms,
                            ScheduledExecutorService scheduler) {
        this(sessions, rooms, scheduler, 30);
    }

    public ChallengeManager(PlayerSessionManager sessions, RoomManager rooms,
                            ScheduledExecutorService scheduler, int timeoutSeconds) {
        if (timeoutSeconds < 1) { throw new IllegalArgumentException("timeoutSeconds must be positive"); }
        this.sessions = sessions;
        this.rooms = rooms;
        this.scheduler = scheduler;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void challenge(String sender, String requestedTarget) {
        String target = requestedTarget.trim().toLowerCase(Locale.ROOT);
        synchronized (sessions) {
            if (!sessions.isOnline(sender)) { return; }
            if (sender.equals(target)) { error(sender, "Không thể thách đấu chính mình."); return; }
            if (!sessions.isOnline(target)) { error(sender, "Đối thủ đã offline."); return; }
            if (sessions.getStatus(sender) != PlayerStatus.ONLINE
                    || sessions.getStatus(target) != PlayerStatus.ONLINE) {
                error(sender, "Bạn hoặc đối thủ đang bận.");
                return;
            }
            Challenge invitation = new Challenge(sender, target, timeoutSeconds);
            invitations.put(invitation.id, invitation);
            invitationByPlayer.put(sender, invitation.id);
            invitationByPlayer.put(target, invitation.id);
            sessions.setStatuses(PlayerStatus.WAITING, sender, target);
            sessions.sendTo(sender, Protocol.CHALLENGE_PENDING, invitation.id, target,
                    Integer.toString(timeoutSeconds));
            sessions.sendTo(target, Protocol.CHALLENGE_RECEIVED, invitation.id, sender,
                    Integer.toString(timeoutSeconds));
            invitation.expiry = scheduler.schedule(() -> expire(invitation.id), timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    public void accept(String username, String challengeId) {
        synchronized (sessions) {
            Challenge invitation = invitations.get(challengeId);
            if (invitation == null || !invitation.receiver.equals(username)) {
                error(username, "Lời mời không còn hợp lệ hoặc không dành cho bạn.");
                return;
            }
            if (System.nanoTime() >= invitation.deadline) {
                close(invitation, "Lời mời đã hết hạn.", true);
                return;
            }
            if (!sessions.isOnline(invitation.sender) || !sessions.isOnline(invitation.receiver)) {
                close(invitation, "Một người chơi đã mất kết nối.", true);
                return;
            }
            close(invitation, "ACCEPTED", false);
            rooms.createRoom(invitation.sender, invitation.receiver);
        }
    }

    public void reject(String username, String challengeId) {
        respond(username, challengeId, false);
    }

    public void cancel(String username, String challengeId) {
        respond(username, challengeId, true);
    }

    private void respond(String username, String id, boolean cancel) {
        synchronized (sessions) {
            Challenge invitation = invitations.get(id);
            if (invitation == null || !(cancel ? invitation.sender : invitation.receiver).equals(username)) {
                error(username, "Lời mời không còn hợp lệ.");
                return;
            }
            close(invitation, cancel ? "Người mời đã hủy lời mời." : "Đối thủ đã từ chối lời mời.", true);
        }
    }

    private void expire(String id) {
        synchronized (sessions) {
            Challenge invitation = invitations.get(id);
            if (invitation != null) { close(invitation, "Lời mời đã hết hạn.", true); }
        }
    }

    public void onDisconnect(String username) {
        synchronized (sessions) {
            Challenge invitation = invitations.get(invitationByPlayer.get(username));
            if (invitation != null) { close(invitation, username + " đã mất kết nối.", true); }
            rooms.onDisconnect(username);
        }
    }

    private void close(Challenge invitation, String reason, boolean releasePlayers) {
        invitations.remove(invitation.id);
        invitationByPlayer.remove(invitation.sender);
        invitationByPlayer.remove(invitation.receiver);
        if (invitation.expiry != null) { invitation.expiry.cancel(false); }
        sessions.sendTo(invitation.sender, Protocol.CHALLENGE_CLOSED, invitation.id, reason);
        sessions.sendTo(invitation.receiver, Protocol.CHALLENGE_CLOSED, invitation.id, reason);
        if (releasePlayers) {
            sessions.setStatuses(PlayerStatus.ONLINE, invitation.sender, invitation.receiver);
        }
    }

    private void error(String username, String reason) {
        sessions.sendTo(username, Protocol.LOBBY_ERROR, reason);
    }
}
