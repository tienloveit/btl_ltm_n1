package tank.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Stores one completed round and both participants for later history views. */
public final class MatchHistoryRepository {
    private final DatabaseConfig databaseConfig;

    public MatchHistoryRepository(DatabaseConfig databaseConfig) { this.databaseConfig = databaseConfig; }

    public void record(String player1, int player1Score, String player2, int player2Score, String winner)
            throws SQLException {
        boolean draw = "DRAW".equals(winner);
        String loser = draw ? null : (winner.equals(player1) ? player2 : player1);
        try (Connection connection = databaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long matchId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO matches (ended_at, status, winner_username, loser_username, is_draw) "
                                + "VALUES (CURRENT_TIMESTAMP, 'COMPLETED', ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, draw ? null : winner);
                    statement.setString(2, loser);
                    statement.setBoolean(3, draw);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) { throw new SQLException("Không tạo được mã lịch sử trận đấu."); }
                        matchId = keys.getLong(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO match_players (match_id, username, score, result) VALUES (?, ?, ?, ?)")) {
                    addPlayer(statement, matchId, player1, player1Score, draw ? "DRAW" : winner.equals(player1) ? "WIN" : "LOSS");
                    addPlayer(statement, matchId, player2, player2Score, draw ? "DRAW" : winner.equals(player2) ? "WIN" : "LOSS");
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void addPlayer(PreparedStatement statement, long matchId, String username, int score, String result)
            throws SQLException {
        statement.setLong(1, matchId);
        statement.setString(2, username);
        statement.setInt(3, score);
        statement.setString(4, result);
        statement.addBatch();
    }
}
