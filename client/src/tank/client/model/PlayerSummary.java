package tank.client.model;

public final class PlayerSummary {
    private final String username;
    private final int score;
    private final String status;

    public PlayerSummary(String username, int score, String status) {
        this.username = username;
        this.score = score;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public int getScore() {
        return score;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return username + " - " + score + " - " + status;
    }
}