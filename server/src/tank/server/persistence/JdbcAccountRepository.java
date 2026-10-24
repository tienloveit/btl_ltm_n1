package tank.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcAccountRepository implements AccountRepository {
    // Only completed match results contribute to profiles and the leaderboard.
    private static final String STATISTICS_JOIN = " LEFT JOIN ("
            + "SELECT mp.username, COUNT(*) AS matches_played, "
            + "SUM(CASE mp.result WHEN 'WIN' THEN 3 WHEN 'DRAW' THEN 1 ELSE 0 END) AS score, "
            + "SUM(mp.result = 'WIN') AS wins, SUM(mp.result = 'LOSS') AS losses, "
            + "SUM(mp.result = 'DRAW') AS draws "
            + "FROM match_players mp JOIN matches m ON m.match_id = mp.match_id "
            + "WHERE m.status = 'COMPLETED' GROUP BY mp.username"
            + ") stats ON stats.username = a.username ";
    private static final String STATISTICS_COLUMNS = "COALESCE(stats.matches_played, 0) AS matches_played, "
            + "COALESCE(stats.score, 0) AS score, COALESCE(stats.wins, 0) AS wins, "
            + "COALESCE(stats.losses, 0) AS losses, COALESCE(stats.draws, 0) AS draws ";
    private final DatabaseConfig databaseConfig;

    public JdbcAccountRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    public void initialize() throws SQLException {
        try (Connection connection = databaseConfig.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_accounts ("
                    + "username VARCHAR(24) NOT NULL PRIMARY KEY, display_name VARCHAR(40) NOT NULL, "
                    + "email VARCHAR(254) NULL, password_salt BINARY(16) NOT NULL, "
                    + "password_hash BINARY(32) NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            try (ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "player_accounts", "matches_played")) {
                if (columns.next()) {
                    throw new SQLException("Legacy schema detected. Stop the server, back up the database, "
                            + "then run server/database/migrate_3nf.sql before restarting.");
                }
            }
            ensureColumn(connection, statement, "email", "VARCHAR(254) NULL");
            ensureEmailIndex(connection, statement);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS matches ("
                    + "match_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at TIMESTAMP NULL, "
                    + "status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS match_players ("
                    + "match_id BIGINT NOT NULL, username VARCHAR(24) NOT NULL, score INT NOT NULL DEFAULT 0, "
                    + "result VARCHAR(8) NOT NULL, PRIMARY KEY (match_id, username), "
                    + "CONSTRAINT chk_match_players_score CHECK (score >= 0), "
                    + "CONSTRAINT chk_match_players_result CHECK (result IN ('WIN', 'LOSS', 'DRAW')), "
                    + "FOREIGN KEY (match_id) REFERENCES matches(match_id), "
                    + "FOREIGN KEY (username) REFERENCES player_accounts(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void ensureColumn(Connection connection, Statement statement, String column, String definition)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "player_accounts", column)) {
            if (columns.next()) {
                return;
            }
        }
        statement.executeUpdate("ALTER TABLE player_accounts ADD COLUMN " + column + " " + definition);
    }

    private static void ensureEmailIndex(Connection connection, Statement statement) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "player_accounts", false, false)) {
            while (indexes.next()) {
                if ("uq_player_accounts_email".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.executeUpdate("CREATE UNIQUE INDEX uq_player_accounts_email ON player_accounts (email)");
    }

    @Override
    public void createAccount(String username, String email, byte[] passwordSalt, byte[] passwordHash)
            throws SQLException {
        String sql = "INSERT INTO player_accounts "
                + "(username, display_name, email, password_salt, password_hash) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, username);
            statement.setString(3, email);
            statement.setBytes(4, passwordSalt);
            statement.setBytes(5, passwordHash);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<AccountRecord> findByUsername(String username) throws SQLException {
        String sql = "SELECT a.username, a.email, a.password_salt, a.password_hash, "
                + STATISTICS_COLUMNS + "FROM player_accounts a" + STATISTICS_JOIN + "WHERE a.username = ?";
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AccountRecord(
                        result.getString("username"),
                        result.getString("email"),
                        result.getBytes("password_salt"),
                        result.getBytes("password_hash"),
                        result.getInt("matches_played"),
                        result.getInt("score"),
                        result.getInt("wins"),
                        result.getInt("losses"),
                        result.getInt("draws")));
            }
        }
    }

    @Override
    public List<RankingRecord> listRanking(int limit) throws SQLException {
        String sql = "SELECT a.username, " + STATISTICS_COLUMNS
                + "FROM player_accounts a" + STATISTICS_JOIN
                + "ORDER BY score DESC, wins DESC, draws DESC, losses ASC, a.username ASC LIMIT ?";
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                List<RankingRecord> records = new ArrayList<>();
                while (result.next()) {
                    records.add(new RankingRecord(
                            result.getString("username"),
                            result.getInt("matches_played"),
                            result.getInt("score"),
                            result.getInt("wins"),
                            result.getInt("losses"),
                            result.getInt("draws")));
                }
                return records;
            }
        }
    }

}
