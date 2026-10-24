package tank.server;

import tank.server.persistence.*;
import java.sql.*;

/** Run only against a disposable database whose name ends in _3nf_test. */
public final class DatabaseNormalizationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        try (Connection c = config.openConnection()) {
            if (!c.getCatalog().endsWith("_3nf_test")) {
                throw new IllegalArgumentException("A disposable _3nf_test database is required.");
            }
        }
        JdbcAccountRepository accounts = new JdbcAccountRepository(config);
        accounts.initialize();
        MatchHistoryRepository history = new MatchHistoryRepository(config);
        accounts.createAccount("test_alpha", "alpha@normalization.test", new byte[16], new byte[32]);
        accounts.createAccount("test_beta", "beta@normalization.test", new byte[16], new byte[32]);
        expect(accounts.findByUsername("test_alpha").orElseThrow().matchesPlayed() == 0, "zero matches");
        history.record("test_alpha", 5, "test_beta", 2, "test_alpha");
        history.record("test_alpha", 0, "test_beta", 0, "DRAW");
        AccountRecord a = accounts.findByUsername("test_alpha").orElseThrow();
        AccountRecord b = accounts.findByUsername("test_beta").orElseThrow();
        expect(a.matchesPlayed() == 2 && a.wins() == 1 && a.draws() == 1 && a.losses() == 0
                && a.score() == 4, "winner ranking and draw");
        expect(b.matchesPlayed() == 2 && b.wins() == 0 && b.draws() == 1 && b.losses() == 1
                && b.score() == 1, "loser ranking and draw");
        expect(history.listForPlayer("test_alpha", 10).size() == 2, "history rows");
        expect(history.listForPlayer("test_alpha", 10).get(1).playerScore() == 5,
                "in-game score is independent of ranking points");
        expect(accounts.listRanking(1).get(0).username().equals("test_alpha"), "ranking order and limit");
        long before = count(config, "matches");
        try {
            history.record("test_alpha", 1, "missing_account", 0, "test_alpha");
            throw new AssertionError("Foreign key should reject missing account");
        } catch (SQLException expected) { checks++; }
        expect(count(config, "matches") == before, "failed participant insert rolls back match");
        expect(accounts.findByUsername("test_alpha").orElseThrow().score() == 4,
                "failed match does not award points");
        try {
            history.record("test_alpha", 1, "test_beta", 0, "unknown_winner");
            throw new AssertionError("Unknown winner should be rejected");
        } catch (IllegalArgumentException expected) { checks++; }
        try (Connection c = config.openConnection(); Statement s = c.createStatement()) {
            try {
                s.executeUpdate("UPDATE match_players SET score = -1 WHERE username = 'test_alpha'");
                throw new AssertionError("Negative score must be rejected");
            } catch (SQLException expected) { checks++; }
            try {
                s.executeUpdate("UPDATE match_players SET result = 'INVALID' WHERE username = 'test_alpha'");
                throw new AssertionError("Invalid result must be rejected");
            } catch (SQLException expected) { checks++; }
        }
        expect(count(config, "matches") == before, "invalid winner does not create a match");
        System.out.println("PASS: " + checks + " JDBC normalization checks.");
    }

    private static long count(DatabaseConfig config, String table) throws SQLException {
        try (Connection c = config.openConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next();
            return r.getLong(1);
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
