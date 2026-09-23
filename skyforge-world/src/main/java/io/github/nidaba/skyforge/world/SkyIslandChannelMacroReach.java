package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * One hydrologic macro-reach between hard network control nodes.
 *
 * <p>Headwaters, confluences and terminals remain exact authored graph controls. Degree-two
 * watershed cells inside the macro-reach describe the admissible drainage corridor but are not
 * required to become literal visible-river vertices.
 */
public record SkyIslandChannelMacroReach(List<SkyIslandChannelProfile> profiles) {
    public SkyIslandChannelMacroReach {
        profiles = List.copyOf(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("macro reach requires at least one profile");
        }
        profiles.forEach(profile -> Objects.requireNonNull(profile, "profile"));
        for (int i = 1; i < profiles.size(); i++) {
            int previous = profiles.get(i - 1).segment().downstreamCellIndex();
            int current = profiles.get(i).segment().sourceCellIndex();
            if (previous != current) {
                throw new IllegalArgumentException("macro reach profiles must form one directed chain");
            }
        }
    }

    public int sourceCellIndex() {
        return profiles.getFirst().segment().sourceCellIndex();
    }

    public int terminalCellIndex() {
        return profiles.getLast().segment().downstreamCellIndex();
    }

    public SkyIslandLocalPosition start() {
        return profiles.getFirst().segment().start();
    }

    public SkyIslandLocalPosition end() {
        return profiles.getLast().segment().end();
    }

    public int coarseReachCount() {
        return profiles.size();
    }
}
