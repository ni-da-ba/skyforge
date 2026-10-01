package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authors sparse exterior-exposure intent for existing cave systems.
 *
 * <p>AUTH-0028 does not extend cave geometry to a boundary. It chooses at most one first-generation
 * opening intent per system from existing chamber/passage geometry, geological support, weathering,
 * hydrology, and morphology-scale exposure preference.
 */
public final class SkyIslandCaveExposurePlanner {
    private static final double ACCEPTANCE_THRESHOLD = 0.49;
    private static final double PROXIMITY_RANGE = 0.52;

    private SkyIslandCaveExposurePlanner() {}

    public static SkyIslandCaveExposurePlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandCaveGeometryPlan geometry = SkyIslandCaveGeometryPlanner.plan(descriptor);
        if (geometry.systems().isEmpty()) {
            return new SkyIslandCaveExposurePlan(descriptor, geometry, List.of());
        }

        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        Map<Integer, SkyIslandCaveSystem> topologyBySystem = new HashMap<>();
        for (SkyIslandCaveSystem system : geometry.topology().systems()) {
            topologyBySystem.put(system.systemId(), system);
        }

        List<SkyIslandCaveExposureIntent> intents = new ArrayList<>();
        for (SkyIslandCaveSystemGeometry system : geometry.systems()) {
            SkyIslandCaveSystem topology = topologyBySystem.get(system.systemId());
            if (topology == null) {
                throw new IllegalStateException("cave geometry lost AUTH-0024 topology system");
            }

            Candidate upper = bestCandidate(
                    descriptor,
                    geology,
                    topology,
                    system,
                    SkyIslandCaveExposureSide.UPPER_SURFACE);
            Candidate underside = bestCandidate(
                    descriptor,
                    geology,
                    topology,
                    system,
                    SkyIslandCaveExposureSide.UNDERSIDE);

            Candidate accepted = chooseAccepted(upper, underside);
            if (accepted != null) {
                intents.add(accepted.toIntent(system.systemId()));
            }
        }

        intents.sort(Comparator.comparingInt(SkyIslandCaveExposureIntent::systemId));
        return new SkyIslandCaveExposurePlan(descriptor, geometry, intents);
    }

    private static Candidate chooseAccepted(Candidate upper, Candidate underside) {
        Candidate best;
        if (upper.score() > underside.score() + 1.0e-12) {
            best = upper;
        } else if (underside.score() > upper.score() + 1.0e-12) {
            best = underside;
        } else {
            best = upper;
        }
        return best.score() >= ACCEPTANCE_THRESHOLD ? best : null;
    }

    private static Candidate bestCandidate(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            SkyIslandCaveSystem topology,
            SkyIslandCaveSystemGeometry geometry,
            SkyIslandCaveExposureSide side) {
        List<Candidate> candidates = new ArrayList<>();

        for (SkyIslandCaveChamberGeometry chamber : geometry.chambers()) {
            double boundaryDepth = side == SkyIslandCaveExposureSide.UPPER_SURFACE
                    ? chamber.center().depthFraction() - chamber.depthRadius()
                    : chamber.center().depthFraction() + chamber.depthRadius();
            boundaryDepth = clamp01(boundaryDepth);
            SkyIslandSubsurfacePosition caveAnchor = new SkyIslandSubsurfacePosition(
                    chamber.center().surfacePosition(),
                    boundaryDepth);
            candidates.add(candidate(
                    descriptor,
                    geology,
                    topology,
                    side,
                    SkyIslandCaveVolumeSample.PrimitiveKind.CHAMBER,
                    chamber.nodeId(),
                    caveAnchor));
        }

        for (SkyIslandCavePassageGeometry passage : geometry.passages()) {
            for (SkyIslandCavePassagePoint point : passage.points()) {
                double boundaryDepth = side == SkyIslandCaveExposureSide.UPPER_SURFACE
                        ? point.position().depthFraction() - point.depthRadius()
                        : point.position().depthFraction() + point.depthRadius();
                boundaryDepth = clamp01(boundaryDepth);
                SkyIslandSubsurfacePosition caveAnchor = new SkyIslandSubsurfacePosition(
                        point.position().surfacePosition(),
                        boundaryDepth);
                candidates.add(candidate(
                        descriptor,
                        geology,
                        topology,
                        side,
                        SkyIslandCaveVolumeSample.PrimitiveKind.PASSAGE,
                        passage.linkId(),
                        caveAnchor));
            }
        }

        return candidates.stream()
                .max(Candidate.ORDER)
                .orElseThrow(() -> new IllegalStateException("cave system geometry has no exposure candidates"));
    }

    private static Candidate candidate(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            SkyIslandCaveSystem topology,
            SkyIslandCaveExposureSide side,
            SkyIslandCaveVolumeSample.PrimitiveKind primitiveKind,
            int primitiveId,
            SkyIslandSubsurfacePosition caveAnchor) {
        double gap = side == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? caveAnchor.depthFraction()
                : 1.0 - caveAnchor.depthFraction();
        gap = clamp01(gap);

        double proximity = clamp01(1.0 - gap / PROXIMITY_RANGE);
        SkyIslandGeologySample sample = geology.sample(caveAnchor);
        double fracture = sample.fractureIntensity();
        double weathering = clamp01(
                0.58 * descriptor.erosionMaturity()
                        + 0.42 * (1.0 - descriptor.rockCompetence()));
        double hydrology = clamp01(
                0.62 * sample.groundwaterPotential()
                        + 0.38 * (topology.waterInfluenced() ? 1.0 : 0.0));

        double score = side == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? 0.42 * proximity
                        + 0.18 * fracture
                        + 0.24 * weathering
                        + 0.09 * hydrology
                        + upperMorphologyBias(descriptor.morphologyFamily())
                : 0.44 * proximity
                        + 0.20 * fracture
                        + 0.24 * weathering
                        + 0.08 * descriptor.exposureTendency()
                        + undersideMorphologyBias(descriptor.morphologyFamily());

        SkyIslandSubsurfacePosition boundaryAnchor = new SkyIslandSubsurfacePosition(
                caveAnchor.surfacePosition(),
                side == SkyIslandCaveExposureSide.UPPER_SURFACE ? 0.0 : 1.0);

        return new Candidate(
                side,
                primitiveKind,
                primitiveId,
                caveAnchor,
                boundaryAnchor,
                gap,
                clamp01(score),
                proximity,
                fracture,
                weathering,
                hydrology);
    }

    private static double upperMorphologyBias(SkyIslandMorphologyFamily family) {
        return switch (family) {
            case MASSIF -> 0.00;
            case TABLELAND -> 0.05;
            case SPINE -> 0.00;
            case BASIN -> 0.07;
            case LOBED -> 0.02;
        };
    }

    private static double undersideMorphologyBias(SkyIslandMorphologyFamily family) {
        return switch (family) {
            case MASSIF -> 0.01;
            case TABLELAND -> 0.00;
            case SPINE -> 0.08;
            case BASIN -> 0.00;
            case LOBED -> 0.03;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Candidate(
            SkyIslandCaveExposureSide side,
            SkyIslandCaveVolumeSample.PrimitiveKind primitiveKind,
            int primitiveId,
            SkyIslandSubsurfacePosition caveAnchor,
            SkyIslandSubsurfacePosition boundaryAnchor,
            double gap,
            double score,
            double proximity,
            double fracture,
            double weathering,
            double hydrology) {

        private static final Comparator<Candidate> ORDER = Comparator
                .comparingDouble(Candidate::score)
                .thenComparingDouble(candidate -> -candidate.gap())
                .thenComparingInt(candidate -> primitiveOrder(candidate.primitiveKind()))
                .thenComparingInt(candidate -> -candidate.primitiveId());

        private SkyIslandCaveExposureIntent toIntent(int systemId) {
            return new SkyIslandCaveExposureIntent(
                    systemId,
                    side,
                    primitiveKind,
                    primitiveId,
                    caveAnchor,
                    boundaryAnchor,
                    gap,
                    score,
                    proximity,
                    fracture,
                    weathering,
                    hydrology);
        }

        private static int primitiveOrder(SkyIslandCaveVolumeSample.PrimitiveKind kind) {
            return switch (kind) {
                case CHAMBER -> 1;
                case PASSAGE -> 0;
                case NONE -> -1;
            };
        }
    }
}
