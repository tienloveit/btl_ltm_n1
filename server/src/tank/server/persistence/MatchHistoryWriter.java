package tank.server.persistence;

import java.sql.SQLException;

@FunctionalInterface
public interface MatchHistoryWriter {
    void record(String player1, int player1Score, String player2, int player2Score, String winner)
            throws SQLException;
}
