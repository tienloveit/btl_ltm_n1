package tank.client.model;

/** Match reward and backend account total for each player; a negative total means unavailable. */
public record MatchResultStats(int player1Points, int player1TotalScore,
                               int player2Points, int player2TotalScore) { }
