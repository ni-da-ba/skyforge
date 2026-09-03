package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects sparse discrete waterfall/cascade events from accepted channel profiles and edge outflows. */
public final class SkyIslandChannelDropPlanner {
    private static final double INTERIOR_GRADIENT_THRESHOLD = 0.58;
    private static final double INTERIOR_DROP_THRESHOLD = 0.62;
    private static final double MIN_INTERIOR_SEPARATION_RADIUS_FRACTION = 0.08;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandChannelDropPlanner() {}

    public static SkyIslandChannelDropPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandChannelProfilePlan profilePlan = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandHydrologicFeaturePlan featurePlan = SkyIslandHydrologicFeaturePlanner.plan(descriptor);

        Map<Integer, SkyIslandChannelProfile> bySource = new HashMap<>();
        Map<Integer, List<SkyIslandChannelProfile>> upstream = new HashMap<>();
        Map<Integer, Double> dropPotentials = new HashMap<>();
        for (SkyIslandChannelProfile profile : profilePlan.profiles()) {
            bySource.put(profile.segment().sourceCellIndex(), profile);
            upstream.computeIfAbsent(profile.segment().downstreamCellIndex(), ignored -> new ArrayList<>())
                    .add(profile);
            dropPotentials.put(profile.segment().sourceCellIndex(), dropPotential(profile, descriptor));
        }
        upstream.values().forEach(list -> list.sort(Comparator.comparingInt(
                profile -> profile.segment().sourceCellIndex())));

        Set<Integer> edgeCells = new HashSet<>();
        for (SkyIslandHydrologicFeature feature : featurePlan.features()) {
            if (feature.kind() == SkyIslandHydrologicFeatureKind.EDGE_WATERFALL) {
                edgeCells.add(feature.sourceCellIndex());
            }
        }

        List<InteriorCandidate> interior = new ArrayList<>();
        for (SkyIslandChannelProfile profile : profilePlan.profiles()) {
            double dropPotential = dropPotentials.get(profile.segment().sourceCellIndex());
            if (profile.gradientPotential() < INTERIOR_GRADIENT_THRESHOLD
                    || dropPotential < INTERIOR_DROP_THRESHOLD
                    || edgeCells.contains(profile.segment().downstreamCellIndex())
                    || hasStrongerAdjacent(profile, bySource, upstream, dropPotentials)) {
                continue;
            }
            double eventStrength = dropPotential
                    * (0.55 + 0.45 * profile.segment().relativeDischarge());
            interior.add(new InteriorCandidate(profile, dropPotential, eventStrength));
        }

        interior.sort(Comparator.comparingDouble(InteriorCandidate::eventStrength)
                .reversed()
                .thenComparingInt(candidate -> candidate.profile().segment().sourceCellIndex()));
        int interiorBudget = Math.max(1, (int) Math.ceil(profilePlan.profiles().size() * 0.08));
        double minimumSeparation = descriptor.nominalRadius() * MIN_INTERIOR_SEPARATION_RADIUS_FRACTION;
        List<InteriorCandidate> selectedInterior = new ArrayList<>();
        for (InteriorCandidate candidate : interior) {
            if (selectedInterior.size() >= interiorBudget) {
                break;
            }
            if (spatiallySeparated(candidate, selectedInterior, minimumSeparation)) {
                selectedInterior.add(candidate);
            }
        }

        List<SkyIslandChannelDrop> drops = new ArrayList<>();
        for (InteriorCandidate candidate : selectedInterior) {
            SkyIslandChannelProfile profile = candidate.profile();
            SkyIslandChannelDropKind kind = candidate.dropPotential() >= 0.82
                            && profile.streamPowerPotential() >= 0.75
                    ? SkyIslandChannelDropKind.WATERFALL
                    : SkyIslandChannelDropKind.CASCADE_STEP;
            double discharge = profile.segment().relativeDischarge();
            double persistence = clamp01(
                    0.45 * discharge
                            + 0.35 * descriptor.hydrologicalPotential()
                            + 0.20 * profile.segment().corridorScale());
            double plungePool = clamp01(
                    0.45 * profile.streamPowerPotential()
                            + 0.30 * discharge
                            + 0.25 * (1.0 - descriptor.rockCompetence()));
            drops.add(new SkyIslandChannelDrop(
                    kind,
                    profile.segment().sourceCellIndex(),
                    profile.segment().downstreamCellIndex(),
                    profile.segment().end(),
                    candidate.dropPotential(),
                    discharge,
                    persistence,
                    plungePool));
        }

        featurePlan.features().stream()
                .filter(feature -> feature.kind() == SkyIslandHydrologicFeatureKind.EDGE_WATERFALL)
                .sorted(Comparator.comparingInt(SkyIslandHydrologicFeature::sourceCellIndex))
                .forEach(feature -> {
                    double discharge = feature.significance();
                    double dropPotential = clamp01(0.55 + 0.45 * discharge);
                    double persistence = clamp01(
                            0.60 * discharge + 0.40 * descriptor.hydrologicalPotential());
                    drops.add(new SkyIslandChannelDrop(
                            SkyIslandChannelDropKind.EDGE_FALL,
                            feature.sourceCellIndex(),
                            -1,
                            feature.position(),
                            dropPotential,
                            discharge,
                            persistence,
                            0.0));
                });

        drops.sort(Comparator.comparingInt(SkyIslandChannelDrop::sourceCellIndex)
                .thenComparing(drop -> drop.kind().ordinal()));
        return new SkyIslandChannelDropPlan(descriptor, drops);
    }

    private static boolean spatiallySeparated(
            InteriorCandidate candidate,
            List<InteriorCandidate> selected,
            double minimumSeparation) {
        SkyIslandLocalPosition position = candidate.profile().segment().end();
        double minimumSquared = minimumSeparation * minimumSeparation;
        for (InteriorCandidate existing : selected) {
            SkyIslandLocalPosition other = existing.profile().segment().end();
            double dx = position.x() - other.x();
            double dz = position.z() - other.z();
            if (dx * dx + dz * dz < minimumSquared - EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasStrongerAdjacent(
            SkyIslandChannelProfile current,
            Map<Integer, SkyIslandChannelProfile> bySource,
            Map<Integer, List<SkyIslandChannelProfile>> upstream,
            Map<Integer, Double> dropPotentials) {
        int source = current.segment().sourceCellIndex();
        double currentPotential = dropPotentials.get(source);
        for (SkyIslandChannelProfile neighbor : upstream.getOrDefault(source, List.of())) {
            if (stronger(neighbor, currentPotential, source, dropPotentials)) {
                return true;
            }
        }
        SkyIslandChannelProfile downstream = bySource.get(current.segment().downstreamCellIndex());
        return downstream != null && stronger(downstream, currentPotential, source, dropPotentials);
    }

    private static boolean stronger(
            SkyIslandChannelProfile neighbor,
            double currentPotential,
            int currentSource,
            Map<Integer, Double> dropPotentials) {
        double neighborPotential = dropPotentials.get(neighbor.segment().sourceCellIndex());
        if (neighborPotential > currentPotential + EPSILON) {
            return true;
        }
        return Math.abs(neighborPotential - currentPotential) <= EPSILON
                && neighbor.segment().sourceCellIndex() < currentSource;
    }

    private static double dropPotential(SkyIslandChannelProfile profile, SkyIslandDescriptor descriptor) {
        return clamp01(
                0.52 * profile.gradientPotential()
                        + 0.26 * profile.streamPowerPotential()
                        + 0.22 * descriptor.rockCompetence());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record InteriorCandidate(
            SkyIslandChannelProfile profile,
            double dropPotential,
            double eventStrength) {}
}
