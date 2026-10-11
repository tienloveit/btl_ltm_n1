package tank.server.persistence;

public record RankingRecord(String username, int matchesPlayed, int score, int wins, int losses, int draws) {
}
