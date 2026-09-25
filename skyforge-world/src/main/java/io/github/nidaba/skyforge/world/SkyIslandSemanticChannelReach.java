package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One semantic channel reach between hydrologically meaningful graph anchors.
 *
 * <p>Intermediate watershed cells remain guidance for the later continuous route solver; they are
 * deliberately not hard geometric waypoints. Reach endpoints occur only at sources, confluences,
 * or terminals selected by {@link SkyIslandSemanticChannelReachPlanner}.
 */
public record SkyIslandSemanticChannelReach(
        int startCellIndex,
        int endCellIndex,
        List<SkyIslandChannelProfile> profiles) {

    public SkyIslandSemanticChannelReach {
        if (startCellIndex < 0 || endCellIndex < 0 || startCellIndex == endCellIndex) {
            throw new IllegalArgumentException("semantic reach requires distinct non-negative anchors");
        }
        profiles = List.copyOf(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("semantic reach must contain at least one profile");
        }
        profiles.forEach(profile -> Objects.requireNonNull(profile, "profile"));

        int expectedSource = startCellIndex;
        for (SkyIslandChannelProfile profile : profiles) {
            SkyIslandChannelSegment segment = profile.segment();
            if (segment.sourceCellIndex() != expectedSource) {
                throw new IllegalArgumentException("semantic reach profiles must form one downstream chain");
            }
            expectedSource = segment.downstreamCellIndex();
        }
        if (expectedSource != endCellIndex) {
            throw new IllegalArgumentException("semantic reach must terminate at endCellIndex");
        }
    }

    public List<SkyIslandLocalPosition> guidancePoints() {
        List<SkyIslandLocalPosition> result = new ArrayList<>(profiles.size() + 1);
        result.add(profiles.getFirst().segment().start());
        for (SkyIslandChannelProfile profile : profiles) {
            result.add(profile.segment().end());
        }
        return List.copyOf(result);
    }

    public int coarseSegmentCount() {
        return profiles.size();
    }

    public double downstreamRelativeDischarge() {
        return profiles.getLast().segment().relativeDischarge();
    }

    public int downstreamStreamOrder() {
        return profiles.getLast().segment().streamOrder();
    }
}
