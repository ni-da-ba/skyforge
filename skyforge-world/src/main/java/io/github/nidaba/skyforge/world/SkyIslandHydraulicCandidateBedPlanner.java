package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Constructs a fixed, bounded bed and valley candidate before any water-surface calculation.
 *
 * <p>The candidate consumes C2 centerline geometry and discharge-scaled section dimensions, plus
 * terrain sampled across each valley. It never reads solved heads or D2 head envelopes. D2's
 * geomorphic limits bound the proposed incision/section and must still be evaluated independently
 * after the hydraulic solve.
 *
 * <p>CASCADE, confluence, and non-edge-terminal reaches remain owned by their explicit transition
 * or retained-water boundary. They are deferred rather than absorbed into an ordinary profile.
 */
public final class SkyIslandHydraulicCandidateBedPlanner {
    private SkyIslandHydraulicCandidateBedPlanner() {}

    public static Optional<SkyIslandHydraulicCandidateBed> plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(skeleton, "skeleton");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(skeleton.descriptor())
                || skeleton.reaches().stream().noneMatch(reach::equals)) {
            throw new IllegalArgumentException("candidate reach must belong to the descriptor skeleton");
        }
        SkyIslandGeomorphicChannelNetworkPlan network = skeleton.geomorphicNetwork();
        SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            return Optional.empty();
        }
        SkyIslandGeomorphicNetworkNode startNode = network.requireNode(semantic.startCellIndex());
        SkyIslandGeomorphicNetworkNode endNode = network.requireNode(semantic.endCellIndex());
        if (startNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                || endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            return Optional.empty();
        }
        if (endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
            SkyIslandChannelTerminalFate fate = SkyIslandChannelTerminalFatePlanner
                    .plan(descriptor, network)
                    .stream()
                    .filter(candidate -> candidate.channelTerminalCellIndex() == endNode.cellIndex())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "candidate terminal is missing explicit watershed fate"));
            if (SkyIslandChannelTerminalFatePolicy.deferralReason(fate.kind()).isPresent()) {
                return Optional.empty();
            }
        }

        double relief = descriptor.reliefBudget();
        if (!Double.isFinite(relief) || relief <= 0.0) {
            throw new IllegalArgumentException("descriptor relief budget must be finite and positive");
        }
        SkyIslandGeomorphicProfileLimits limits = policy.limits(semantic);
        double maximumIncision = limits.maximumCenterlineLoweringPotential() * relief;
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        List<SkyIslandLocalPosition> centerline = reach.centerline().points();
        List<SkyIslandHydraulicCandidateBed.Station> stations =
                new ArrayList<>(reach.samples().size());
        for (int i = 0; i < reach.samples().size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = reach.samples().get(i);
            double targetDepth = sample.waterDepthPotential() * relief;
            double lowering = Math.min(targetDepth, maximumIncision);
            double originalTerrain = sample.terrainElevation() * relief;
            double bed = originalTerrain - lowering;
            if (!(bed >= 0.0) || !(bed < originalTerrain)) {
                // The bounded incision would leave the authored vertical domain. Do not clamp
                // the bed or silently change the target geometry.
                return Optional.empty();
            }

            Vector tangent = tangent(centerline, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            double bankHalfWidth = sample.bankfullHalfWidth();
            double valleyHalfWidth = Math.max(
                    2.0 * bankHalfWidth,
                    lowering / (2.0 * limits.maximumReliefToValleyWidthRatio()));
            double originalLeftBank = elevationAt(
                    terrain, sample.position(), normal, bankHalfWidth, relief);
            double originalRightBank = elevationAt(
                    terrain, sample.position(), normal, -bankHalfWidth, relief);
            double originalLeftShoulder = elevationAt(
                    terrain, sample.position(), normal, valleyHalfWidth, relief);
            double originalRightShoulder = elevationAt(
                    terrain, sample.position(), normal, -valleyHalfWidth, relief);
            if (originalLeftBank < bed || originalRightBank < bed
                    || originalLeftShoulder < bed || originalRightShoulder < bed) {
                return Optional.empty();
            }
            double candidateLeftBank = Math.min(
                    originalLeftBank,
                    bed + limits.maximumLateralRecoveryGrade() * bankHalfWidth);
            double candidateRightBank = Math.min(
                    originalRightBank,
                    bed + limits.maximumLateralRecoveryGrade() * bankHalfWidth);

            List<SkyIslandHydraulicCandidateBed.CrossSectionPoint> crossSection = List.of(
                    new SkyIslandHydraulicCandidateBed.CrossSectionPoint(
                            -valleyHalfWidth, originalRightShoulder, originalRightShoulder),
                    new SkyIslandHydraulicCandidateBed.CrossSectionPoint(
                            -bankHalfWidth, originalRightBank, candidateRightBank),
                    new SkyIslandHydraulicCandidateBed.CrossSectionPoint(
                            0.0, originalTerrain, bed),
                    new SkyIslandHydraulicCandidateBed.CrossSectionPoint(
                            bankHalfWidth, originalLeftBank, candidateLeftBank),
                    new SkyIslandHydraulicCandidateBed.CrossSectionPoint(
                            valleyHalfWidth, originalLeftShoulder, originalLeftShoulder));

            stations.add(new SkyIslandHydraulicCandidateBed.Station(
                    sample.position(),
                    sample.arcLength(),
                    sample.relativeDischarge(),
                    bankHalfWidth,
                    targetDepth,
                    originalTerrain,
                    bed,
                    crossSection));
        }
        return Optional.of(new SkyIslandHydraulicCandidateBed(
                reach.geomorphicRoute(),
                reach.centerline(),
                stations,
                reach.pathLength()));
    }

    private static double elevationAt(
            SkyIslandSemanticField terrain,
            SkyIslandLocalPosition origin,
            Vector normal,
            double offset,
            double relief) {
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                origin.x() + normal.x() * offset,
                origin.z() + normal.z() * offset);
        return terrain.sample(position) * relief;
    }

    private static Vector tangent(List<SkyIslandLocalPosition> points, int index) {
        SkyIslandLocalPosition a = index == 0 ? points.getFirst() : points.get(index - 1);
        SkyIslandLocalPosition b = index == points.size() - 1 ? points.getLast() : points.get(index + 1);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        if (!(length > 1.0e-12)) {
            throw new IllegalStateException("candidate bed centerline must have non-zero tangent");
        }
        return new Vector(dx / length, dz / length);
    }

    private record Vector(double x, double z) {}
}
