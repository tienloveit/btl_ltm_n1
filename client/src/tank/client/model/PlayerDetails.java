package tank.client.model;

import java.util.List;

/** Public statistics and recent matches, from the selected player's perspective. */
public record PlayerDetails(PlayerProfile profile, List<MatchHistoryEntry> history) {
    public PlayerDetails {
        java.util.Objects.requireNonNull(profile);
        history = List.copyOf(history);
    }
}
