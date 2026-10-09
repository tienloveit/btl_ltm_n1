package tank.server.auth;

import tank.server.persistence.AccountRecord;
import tank.server.persistence.AccountRepository;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountService {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{3,24}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AccountRepository repository;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public String register(String username, String email, char[] password) throws SQLException {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            return "Username phải dài 3-24 ký tự và chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.";
        }
        if (normalizedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return "Email không hợp lệ.";
        }
        if (password == null || password.length < 6 || password.length > 128) {
            return "Mật khẩu phải dài từ 6 đến 128 ký tự.";
        }

        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] passwordHash = hash(password, salt);
        try {
            repository.createAccount(normalizedUsername, normalizedEmail, salt, passwordHash);
            return null;
        } catch (SQLIntegrityConstraintViolationException exception) {
            return "Username hoặc email đã được sử dụng.";
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) {
                return "Username hoặc email đã được sử dụng.";
            }
            throw exception;
        }
    }

    public Account authenticate(String username, char[] password) throws SQLException {
        if (username == null || password == null) {
            return null;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        AccountRecord record = repository.findByUsername(normalizedUsername).orElse(null);
        if (record == null) {
            return null;
        }
        byte[] candidateHash = hash(password, record.passwordSalt());
        if (!MessageDigest.isEqual(candidateHash, record.passwordHash())) {
            return null;
        }
        return new Account(record.username(), record.email(), record.matchesPlayed(), record.score(),
            record.wins(), record.losses(), record.draws());
    }

    private static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public record Account(String username, String email, int matchesPlayed, int score, int wins, int losses, int draws) {
    }
}