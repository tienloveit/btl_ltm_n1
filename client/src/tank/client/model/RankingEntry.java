package tank.client.model;

public record RankingEntry(String username, int matchesPlayed, int score, int wins, int losses, int draws) {
}
