package tank.server.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseConfig {
    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/tank_game?allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private DatabaseConfig(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig fromEnvironment() {
        Map<String, String> dotenv = loadDotEnv();
        String jdbcUrl = setting("TANK_DB_URL", DEFAULT_URL, dotenv);
        String username = setting("TANK_DB_USER", "tank_app", dotenv);
        String password = setting("TANK_DB_PASSWORD", "", dotenv);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set TANK_DB_PASSWORD in server/.env or the server environment.");
        }
        return new DatabaseConfig(jdbcUrl, username, password);
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    private static String setting(String name, String defaultValue, Map<String, String> dotenv) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = dotenv.get(name);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Map<String, String> loadDotEnv() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            envFile = Path.of("server", ".env");
        }
        if (!Files.exists(envFile)) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + envFile.toAbsolutePath(), exception);
        }
        return values;
    }
}