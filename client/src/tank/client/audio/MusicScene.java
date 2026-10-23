package tank.client.audio;

/** Idempotent scene selection; countdown/result lower the same running track. */
public enum MusicScene {
    OFF(null, 0), LOBBY("/music/lobby.wav", 0.20), COUNTDOWN("/music/lobby.wav", 0.06),
    BATTLE("/music/battle.wav", 0.20), RESULT("/music/battle.wav", 0.06);

    final String resource;
    final double gain;

    MusicScene(String resource, double gain) { this.resource = resource; this.gain = gain; }
}
