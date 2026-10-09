package tank.server.persistence;

public record AccountRecord(
    String username,
    String email,
    byte[] passwordSalt,
    byte[] passwordHash,
    int matchesPlayed,
    int score,
    int wins,
    int losses,
    int draws) {
    public AccountRecord {
        passwordSalt = passwordSalt.clone();
        passwordHash = passwordHash.clone();
    }

    @Override
    public byte[] passwordSalt() {
        return passwordSalt.clone();
    }

    @Override
    public byte[] passwordHash() {
        return passwordHash.clone();
    }
}