package tank.client.model;

/** Snapshot supplied by the server; the client does not advance the countdown itself. */
public record RoomState(String roomId, String player1, String player2, int secondsRemaining) { }
