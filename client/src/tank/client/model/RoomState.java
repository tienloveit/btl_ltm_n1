package tank.client.model;

/** Snapshot supplied by the server; the client does not advance the countdown itself. */
public record RoomState(String roomId, String player1, String player2, int secondsRemaining,
                        boolean player1Ready, boolean player2Ready) {
    public RoomState(String roomId, String player1, String player2, int secondsRemaining) {
        this(roomId, player1, player2, secondsRemaining, false, false);
    }

    public boolean isReady(String username) {
        if (player1.equals(username)) { return player1Ready; }
        return player2.equals(username) && player2Ready;
    }
}
