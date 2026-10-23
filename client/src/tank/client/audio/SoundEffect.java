package tank.client.audio;

public enum SoundEffect {
    SHOT("shot.wav"), HIT("hit.wav"), EXPLOSION("explosion.wav"),
    COUNTDOWN_TICK("countdown_tick.wav"), START("start.wav"),
    VICTORY("victory.wav"), DEFEAT("defeat.wav"), DRAW("draw.wav");

    private final String fileName;

    SoundEffect(String fileName) { this.fileName = fileName; }

    public String resourcePath() { return "/audio/" + fileName; }
}
