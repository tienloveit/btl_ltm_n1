package tank.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Stores one completed round and both participants for later history views. */
public final class MatchHistoryRepository {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
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

    public List<MatchHistoryRecord> listForPlayer(String username, int limit) throws SQLException {
        String sql = "SELECT m.match_id, COALESCE(m.ended_at, m.started_at) AS ended_at, "
                + "self.score AS player_score, opponent.username AS opponent_username, "
                + "opponent.score AS opponent_score, self.result AS result "
                + "FROM matches m "
                + "JOIN match_players self ON self.match_id = m.match_id "
                + "JOIN match_players opponent ON opponent.match_id = m.match_id "
                + "WHERE self.username = ? AND opponent.username <> ? "
                + "ORDER BY COALESCE(m.ended_at, m.started_at) DESC, m.match_id DESC "
                + "LIMIT ?";
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, username);
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                List<MatchHistoryRecord> records = new ArrayList<>();
                while (result.next()) {
                    records.add(new MatchHistoryRecord(
                            result.getLong("match_id"),
                            formatTime(result.getTimestamp("ended_at")),
                            result.getString("opponent_username"),
                            result.getInt("player_score"),
                            result.getInt("opponent_score"),
                            result.getString("result")));
                }
                return records;
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

    private static String formatTime(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(DISPLAY_TIME);
    }
}
