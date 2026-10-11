package tank.client.model;

public record MatchHistoryEntry(long matchId, String endedAt, String opponent,
                                int playerScore, int opponentScore, String result) {
}
