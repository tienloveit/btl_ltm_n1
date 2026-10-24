package tank.server.persistence;

import java.sql.SQLException;
import java.util.List;

@FunctionalInterface
public interface MatchHistoryReader {
    List<MatchHistoryRecord> listForPlayer(String username, int limit) throws SQLException;
}
