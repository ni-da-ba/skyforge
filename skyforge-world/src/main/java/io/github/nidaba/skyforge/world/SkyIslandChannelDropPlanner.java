package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Selects sparse discrete waterfall/cascade events from accepted channel profiles and edge outflows. */
public final class SkyIslandChannelDropPlanner {
    private static final double INTERIOR_GRADIENT_THRESHOLD = 0.58;
    private static final double INTERIOR_DROP_THRESHOLD = 0.62;
    private static final double MIN_INTERIOR_SEPARATION_RADIUS_FRACTION = 0.08;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandChannelDropPlanner() {}

    /** Historical/raw visible-channel diagnostic retained for accepted AUTH-0013 evidence. */
    public static SkyIslandChannelDropPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandChannelProfilePlanner.plan(descriptor).profiles());
    }

    /**
     * Selects interior drop events from an explicit channel-profile subset.
     *
     * <p>Edge-outlet events remain watershed-derived rather than visible-channel-derived. This
     * preserves physically meaningful minor edge discharge while AUTH-0019 removes redundant
     * channel/riparian terrain shaping.
     */
    public static SkyIslandChannelDropPlan plan(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles) {
        Objects.requireNonNull(descriptor, "descriptor");
        profiles = List.copyOf(profiles);
        SkyIslandHydrologicFeaturePlan featurePlan = SkyIslandHydrologicFeaturePlanner.plan(descriptor);

        Map<Integer, SkyIslandChannelProfile> bySource = new HashMap<>();
        Map<Integer, List<SkyIslandChannelProfile>> upstream = new HashMap<>();
        Map<Integer, Double> dropPotentials = new HashMap<>();
        for (SkyIslandChannelProfile profile : profiles) {
            bySource.put(profile.segment().sourceCellIndex(), profile);
            upstream.computeIfAbsent(profile.segment().downstreamCellIndex(), ignored -> new ArrayList<>())
                    .add(profile);
            dropPotentials.put(profile.segment().sourceCellIndex(), dropPotential(profile, descriptor));
        }
        upstream.values().forEach(list -> list.sort(Comparator.comparingInt(
                profile -> profile.segment().sourceCellIndex())));

        Set<Integer> routedSourceCells = new HashSet<>();
        for (SkyIslandChannelProfile profile : profiles) {
            routedSourceCells.add(profile.segment().sourceCellIndex());
        }
        Set<Integer> routedTerminalCells = new HashSet<>();
        for (SkyIslandChannelProfile profile : profiles) {
            int downstream = profile.segment().downstreamCellIndex();
            if (!routedSourceCells.contains(downstream)) {
                routedTerminalCells.add(downstream);
            }
        }

        Set<Integer> edgeCells = new HashSet<>();
        for (SkyIslandHydrologicFeature feature : featurePlan.features()) {
            if (feature.kind() == SkyIslandHydrologicFeatureKind.EDGE_WATERFALL
                    && routedTerminalCells.contains(feature.sourceCellIndex())) {
                edgeCells.add(feature.sourceCellIndex());
            }
        }

        List<InteriorCandidate> interior = new ArrayList<>();
        for (SkyIslandChannelProfile profile : profiles) {
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
        int interiorBudget = profiles.isEmpty()
                ? 0
                : Math.max(1, (int) Math.ceil(profiles.size() * 0.08));
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

        List<SkyIslandChannelDrop> result = new ArrayList<>();
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
            result.add(new SkyIslandChannelDrop(
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
                .filter(feature -> edgeCells.contains(feature.sourceCellIndex()))
                .sorted(Comparator.comparingInt(SkyIslandHydrologicFeature::sourceCellIndex))
                .forEach(feature -> {
                    double discharge = feature.significance();
                    double dropPotential = clamp01(0.55 + 0.45 * discharge);
                    double persistence = clamp01(
                            0.60 * discharge + 0.40 * descriptor.hydrologicalPotential());
                    result.add(new SkyIslandChannelDrop(
                            SkyIslandChannelDropKind.EDGE_FALL,
                            feature.sourceCellIndex(),
                            -1,
                            feature.position(),
                            dropPotential,
                            discharge,
                            persistence,
                            0.0));
                });

        result.sort(Comparator.comparingInt(SkyIslandChannelDrop::sourceCellIndex)
                .thenComparing(drop -> drop.kind().ordinal()));
        return new SkyIslandChannelDropPlan(descriptor, result);
    }

    /**
     * Preserves already-selected drop identities/strengths while localizing each interior event
     * onto its naturalized path.
     *
     * <p>A reach with exactly one retained-water endpoint localizes its discrete fall at the shared
     * retained shoreline boundary; otherwise the strongest authored descent remains authoritative.
     * This keeps watershed/drop selection unchanged while ensuring standing-water datum, fluvial
     * throat geometry, and backend realization agree on where the discrete fall occurs.
     */
    public static SkyIslandChannelDropPlan localize(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelDropPlan selected,
            SkyIslandNaturalizedChannelPlan naturalized) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(naturalized, "naturalized");
        if (!selected.descriptor().equals(descriptor)
                || !naturalized.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "drop localization inputs must share the same descriptor");
        }

        Map<SegmentKey, SkyIslandNaturalizedChannelPath> pathBySegment = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : naturalized.paths()) {
            var segment = path.profile().segment();
            pathBySegment.put(
                    new SegmentKey(segment.sourceCellIndex(), segment.downstreamCellIndex()),
                    path);
        }

        SkyIslandSemanticField terrain =
                SkyIslandSemanticFieldSet.create(descriptor).elevationTendency();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        Map<Integer, SkyIslandWaterbodyFootprint> retainedByCell = new HashMap<>();
        for (SkyIslandWaterbodyFootprint footprint :
                SkyIslandWaterbodyFootprintPlanner.plan(descriptor).footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                SkyIslandWaterbodyFootprint previous =
                        retainedByCell.put(cell.watershedCellIndex(), footprint);
                if (previous != null && previous != footprint) {
                    throw new IllegalStateException(
                            "accepted retained-water footprints overlap one watershed cell");
                }
            }
        }
        List<SkyIslandChannelDrop> localized = new ArrayList<>(selected.drops().size());
        for (SkyIslandChannelDrop drop : selected.drops()) {
            if (drop.kind() == SkyIslandChannelDropKind.EDGE_FALL) {
                localized.add(drop);
                continue;
            }
            SkyIslandNaturalizedChannelPath path = pathBySegment.get(
                    new SegmentKey(drop.sourceCellIndex(), drop.downstreamCellIndex()));
            if (path == null) {
                throw new IllegalStateException(
                        "selected interior drop lost its naturalized channel path");
            }
            SkyIslandWaterbodyFootprint sourceRetained =
                    retainedByCell.get(drop.sourceCellIndex());
            SkyIslandWaterbodyFootprint downstreamRetained =
                    retainedByCell.get(drop.downstreamCellIndex());
            SkyIslandLocalPosition position = strongestDescentPosition(path, terrain);
            if ((sourceRetained == null) != (downstreamRetained == null)) {
                SkyIslandWaterbodyFootprint retained =
                        sourceRetained != null ? sourceRetained : downstreamRetained;
                position = SkyIslandRetainedWaterFootprintGeometry.endpointBoundaryCrossing(
                                descriptor, watershed, retained, path)
                        .orElse(position);
            }
            localized.add(new SkyIslandChannelDrop(
                    drop.kind(),
                    drop.sourceCellIndex(),
                    drop.downstreamCellIndex(),
                    position,
                    drop.dropPotential(),
                    drop.dischargePotential(),
                    drop.persistencePotential(),
                    drop.plungePoolPotential()));
        }
        return new SkyIslandChannelDropPlan(descriptor, localized);
    }

    private static SkyIslandLocalPosition strongestDescentPosition(
            SkyIslandNaturalizedChannelPath path,
            SkyIslandSemanticField terrain) {
        List<SkyIslandLocalPosition> points = path.points();
        double bestScore = Double.NEGATIVE_INFINITY;
        SkyIslandLocalPosition best = path.profile().segment().end();

        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition a = points.get(index - 1);
            SkyIslandLocalPosition b = points.get(index);
            double length = Math.hypot(b.x() - a.x(), b.z() - a.z());
            if (length <= EPSILON) {
                continue;
            }
            double descent = terrain.sample(a) - terrain.sample(b);
            double score = descent / length;
            if (score > bestScore + EPSILON) {
                bestScore = score;
                best = new SkyIslandLocalPosition(
                        0.5 * (a.x() + b.x()),
                        0.5 * (a.z() + b.z()));
            }
        }
        return best;
    }

    private record SegmentKey(int source, int downstream) {}

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
