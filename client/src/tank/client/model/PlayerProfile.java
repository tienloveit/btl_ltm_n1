package tank.client.model;

public final class PlayerProfile {
    private final String username;
    private final int matchesPlayed;
    private final int score;
    private final int wins;
    private final int losses;
    private final int draws;
    private final String status;

    public PlayerProfile(String username, int matchesPlayed, int score, int wins, int losses, int draws, String status) {
        this.username = username;
        this.matchesPlayed = matchesPlayed;
        this.score = score;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public int getScore() {
        return score;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public String getStatus() {
        return status;
    }

    public PlayerProfile withStatus(String nextStatus) {
        return new PlayerProfile(username, matchesPlayed, score, wins, losses, draws, nextStatus);
    }
}
