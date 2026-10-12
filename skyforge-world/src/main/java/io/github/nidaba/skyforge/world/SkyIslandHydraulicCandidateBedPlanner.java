package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Constructs an explicit fixed candidate bed from an already refined C2 reach geometry.
 *
 * <p>This is a bounded first integration seam: it converts the existing discharge-scaled geometry
 * skeleton into a bed candidate before any water-surface calculation. It never reads a solved
 * water surface or a D2 head envelope. D2's centerline-incision limit bounds the proposed geometry
 * here and must still be evaluated independently after a hydraulic solve.
 *
 * <p>CASCADE-owned reaches are deferred: their bed/drop geometry belongs to an explicit transition
 * candidate, not an ordinary channel profile.
 */
public final class SkyIslandHydraulicCandidateBedPlanner {
    private SkyIslandHydraulicCandidateBedPlanner() {}

    public static Optional<SkyIslandHydraulicCandidateBed> plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(policy, "policy");
        SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            return Optional.empty();
        }

        double relief = descriptor.reliefBudget();
        if (!Double.isFinite(relief) || relief <= 0.0) {
            throw new IllegalArgumentException("descriptor relief budget must be finite and positive");
        }
        double maximumIncision = policy.limits(semantic).maximumCenterlineLoweringPotential() * relief;
        List<SkyIslandHydraulicCandidateBed.Station> stations =
                new ArrayList<>(reach.samples().size());
        for (SkyIslandHydraulicGeometrySkeletonSample sample : reach.samples()) {
            double targetDepth = sample.waterDepthPotential() * relief;
            double lowering = Math.min(targetDepth, maximumIncision);
            double originalTerrain = sample.terrainElevation() * relief;
            double bed = originalTerrain - lowering;
            if (!(bed >= 0.0) || !(bed < originalTerrain)) {
                // The bounded incision would leave the authored vertical domain. Do not clamp
                // the bed or silently change the target geometry.
                return Optional.empty();
            }
            stations.add(new SkyIslandHydraulicCandidateBed.Station(
                    sample.position(),
                    sample.arcLength(),
                    sample.relativeDischarge(),
                    sample.bankfullHalfWidth() * relief,
                    targetDepth,
                    originalTerrain,
                    bed));
        }
        return Optional.of(new SkyIslandHydraulicCandidateBed(
                reach.geomorphicRoute(),
                reach.centerline(),
                stations,
                reach.pathLength()));
    }
}
