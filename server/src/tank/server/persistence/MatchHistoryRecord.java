package tank.server.persistence;

public record MatchHistoryRecord(long matchId, String endedAt, String opponent,
                                 int playerScore, int opponentScore, String result) {
}
