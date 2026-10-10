package tank.client.model;

import java.util.List;

/** Immutable server snapshot rendered by the gameplay screen. */
public record GameState(String roomId, int secondsRemaining, Tank player1, Tank player2, List<Bullet> bullets) {
    public record Tank(int x, int hp, int ammo, int score) { }
    public record Bullet(String owner, int x, int y) { }
}
