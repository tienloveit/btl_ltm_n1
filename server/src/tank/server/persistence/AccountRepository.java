package tank.server.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    void initialize() throws SQLException;

    void createAccount(String username, String email, byte[] passwordSalt, byte[] passwordHash)
            throws SQLException;

    Optional<AccountRecord> findByUsername(String username) throws SQLException;

    List<RankingRecord> listRanking(int limit) throws SQLException;

}
