package tank.server.persistence;

import java.sql.SQLException;
import java.util.Optional;

public interface AccountRepository {
    void initialize() throws SQLException;

    void createAccount(String username, String email, byte[] passwordSalt, byte[] passwordHash)
            throws SQLException;

    Optional<AccountRecord> findByUsername(String username) throws SQLException;

        boolean updateStatistics(String username, int matchesPlayed, int score, int wins, int losses, int draws)
            throws SQLException;
}