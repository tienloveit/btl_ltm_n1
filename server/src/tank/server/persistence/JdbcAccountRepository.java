package tank.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class JdbcAccountRepository implements AccountRepository {
    private final DatabaseConfig databaseConfig;

    public JdbcAccountRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    public void initialize() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_accounts ("
                + "username VARCHAR(24) NOT NULL PRIMARY KEY, "
                + "display_name VARCHAR(40) NOT NULL, "
                + "email VARCHAR(254) NULL, "
                + "password_salt BINARY(16) NOT NULL, "
                + "password_hash BINARY(32) NOT NULL, "
                + "matches_played INT NOT NULL DEFAULT 0, "
                + "score INT NOT NULL DEFAULT 0, "
                + "wins INT NOT NULL DEFAULT 0, "
                + "losses INT NOT NULL DEFAULT 0, "
                + "draws INT NOT NULL DEFAULT 0, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection connection = databaseConfig.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            ensureColumn(connection, statement, "email", "VARCHAR(254) NULL");
            ensureColumn(connection, statement, "matches_played", "INT NOT NULL DEFAULT 0");
            ensureColumn(connection, statement, "score", "INT NOT NULL DEFAULT 0");
            ensureColumn(connection, statement, "wins", "INT NOT NULL DEFAULT 0");
            ensureColumn(connection, statement, "losses", "INT NOT NULL DEFAULT 0");
            ensureColumn(connection, statement, "draws", "INT NOT NULL DEFAULT 0");
            ensureEmailIndex(connection, statement);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS matches ("
                    + "match_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at TIMESTAMP NULL, "
                    + "status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED', winner_username VARCHAR(24) NULL, "
                    + "loser_username VARCHAR(24) NULL, is_draw BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "FOREIGN KEY (winner_username) REFERENCES player_accounts(username), "
                    + "FOREIGN KEY (loser_username) REFERENCES player_accounts(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS match_players ("
                    + "match_id BIGINT NOT NULL, username VARCHAR(24) NOT NULL, score INT NOT NULL DEFAULT 0, "
                    + "result VARCHAR(8) NOT NULL, PRIMARY KEY (match_id, username), "
                    + "FOREIGN KEY (match_id) REFERENCES matches(match_id), "
                    + "FOREIGN KEY (username) REFERENCES player_accounts(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void ensureColumn(Connection connection, Statement statement, String column, String definition)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "player_accounts", column)) {
            if (columns.next()) {
                return;
            }
        }
        statement.executeUpdate("ALTER TABLE player_accounts ADD COLUMN " + column + " " + definition);
    }

    private static void ensureEmailIndex(Connection connection, Statement statement) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, "player_accounts", false, false)) {
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
        String sql = "SELECT username, email, password_salt, password_hash, "
            + "matches_played, score, wins, losses, draws "
                + "FROM player_accounts WHERE username = ?";
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
    public boolean updateStatistics(String username, int matchesPlayed, int score, int wins, int losses, int draws)
            throws SQLException {
        if (matchesPlayed < 0 || score < 0 || wins < 0 || losses < 0 || draws < 0
                || matchesPlayed != wins + losses + draws) {
            throw new IllegalArgumentException("Player statistics must be nonnegative and match totals must agree.");
        }
        String sql = "UPDATE player_accounts SET matches_played = ?, score = ?, wins = ?, losses = ?, draws = ? "
                + "WHERE username = ?";
        try (Connection connection = databaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, matchesPlayed);
            statement.setInt(2, score);
            statement.setInt(3, wins);
            statement.setInt(4, losses);
            statement.setInt(5, draws);
            statement.setString(6, username);
            return statement.executeUpdate() == 1;
        }
    }
}
