package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One connected accepted channel component retained by the coherence pass. */
public record SkyIslandCoherentChannelComponent(
        int terminalCellIndex,
        SkyIslandLocalPosition terminalPosition,
        double terminalRelativeDischarge,
        int maxStreamOrder,
        List<SkyIslandChannelProfile> profiles) {

    public SkyIslandCoherentChannelComponent {
        if (terminalCellIndex < 0) {
            throw new IllegalArgumentException("terminalCellIndex must be non-negative");
        }
        terminalPosition = Objects.requireNonNull(terminalPosition, "terminalPosition");
        if (!Double.isFinite(terminalRelativeDischarge)
                || terminalRelativeDischarge < 0.0
                || terminalRelativeDischarge > 1.0) {
            throw new IllegalArgumentException("terminalRelativeDischarge must be finite and in [0, 1]");
        }
        if (maxStreamOrder < 1) {
            throw new IllegalArgumentException("maxStreamOrder must be positive");
        }
        profiles = List.copyOf(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("coherent component must contain at least one profile");
        }
        profiles.forEach(profile -> Objects.requireNonNull(profile, "profile"));
    }

    public int reachCount() {
        return profiles.size();
    }
}
