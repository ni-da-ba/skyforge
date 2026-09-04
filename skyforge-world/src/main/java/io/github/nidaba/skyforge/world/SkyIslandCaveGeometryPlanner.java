package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts AUTH-0024 semantic cave topology into broad backend-neutral chamber and passage geometry.
 *
 * <p>Passages are geology-steered sampled quadratic corridors. The sampled points are an authored
 * geometric corridor skeleton, not backend carve coordinates or a promise that a voxel backend must
 * reproduce every point literally.
 */
public final class SkyIslandCaveGeometryPlanner {
    private static final int PASSAGE_SAMPLES = 13;

    private SkyIslandCaveGeometryPlanner() {}

    public static SkyIslandCaveGeometryPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandCaveSystemPlan topology = SkyIslandCaveSystemPlanner.plan(descriptor);
        if (topology.systems().isEmpty()) {
            return new SkyIslandCaveGeometryPlan(descriptor, topology, List.of());
        }

        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        RegionMembership membership = RegionMembership.from(topology.geology());

        List<SkyIslandCaveSystemGeometry> systems = new ArrayList<>();
        for (SkyIslandCaveSystem system : topology.systems()) {
            List<SkyIslandCaveChamberGeometry> chambers = system.nodes().stream()
                    .map(node -> chamberGeometry(descriptor, node))
                    .sorted(Comparator.comparingInt(SkyIslandCaveChamberGeometry::nodeId))
                    .toList();
            Map<Integer, SkyIslandCaveChamberGeometry> chambersByNode = new HashMap<>();
            for (SkyIslandCaveChamberGeometry chamber : chambers) {
                chambersByNode.put(chamber.nodeId(), chamber);
            }

            List<SkyIslandCavePassageGeometry> passages = new ArrayList<>();
            for (SkyIslandCaveLink link : system.links()) {
                SkyIslandCaveNode first = node(system, link.firstNodeId());
                SkyIslandCaveNode second = node(system, link.secondNodeId());
                passages.add(passageGeometry(
                        descriptor,
                        topology.geology(),
                        geology,
                        semantic,
                        membership,
                        first,
                        second,
                        chambersByNode.get(first.nodeId()),
                        chambersByNode.get(second.nodeId()),
                        link));
            }
            passages.sort(Comparator.comparingInt(SkyIslandCavePassageGeometry::linkId));
            systems.add(new SkyIslandCaveSystemGeometry(system.systemId(), chambers, passages));
        }

        return new SkyIslandCaveGeometryPlan(descriptor, topology, systems);
    }

    private static SkyIslandCaveChamberGeometry chamberGeometry(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveNode node) {
        double radiusFraction = clamp(
                0.032
                        + 0.040 * node.chamberPotential()
                        + 0.018 * (1.0 - descriptor.rockCompetence()),
                0.030,
                0.085);
        double horizontalRadius = descriptor.nominalRadius() * radiusFraction;

        double requestedDepthRadius = clamp(
                0.030
                        + 0.060 * node.chamberPotential()
                        + 0.018 * (1.0 - descriptor.rockCompetence()),
                0.028,
                0.115);
        double boundaryAllowance = 0.82 * Math.min(
                node.position().depthFraction(),
                1.0 - node.position().depthFraction());
        double depthRadius = Math.max(0.012, Math.min(requestedDepthRadius, boundaryAllowance));

        double irregularity = clamp01(
                0.18
                        + 0.36 * (1.0 - descriptor.rockCompetence())
                        + 0.28 * descriptor.erosionMaturity()
                        + 0.18 * node.chamberPotential());

        return new SkyIslandCaveChamberGeometry(
                node.nodeId(),
                node.position(),
                horizontalRadius,
                depthRadius,
                irregularity);
    }

    private static SkyIslandCavePassageGeometry passageGeometry(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan regionPlan,
            SkyIslandGeologyFieldSet geology,
            SkyIslandSemanticFieldSet semantic,
            RegionMembership membership,
            SkyIslandCaveNode first,
            SkyIslandCaveNode second,
            SkyIslandCaveChamberGeometry firstChamber,
            SkyIslandCaveChamberGeometry secondChamber,
            SkyIslandCaveLink link) {
        SkyIslandSubsurfacePosition midpoint = midpoint(first.position(), second.position());
        double dx = second.position().x() - first.position().x();
        double dz = second.position().z() - first.position().z();
        double horizontalLength = Math.hypot(dx, dz);
        double normalX = horizontalLength > 1.0e-9 ? -dz / horizontalLength : 1.0;
        double normalZ = horizontalLength > 1.0e-9 ? dx / horizontalLength : 0.0;

        double maxLateral = Math.min(
                descriptor.nominalRadius()
                        * (0.035
                                + 0.030 * (1.0 - descriptor.rockCompetence())
                                + 0.018 * descriptor.erosionMaturity()),
                Math.max(descriptor.nominalRadius() * 0.018, horizontalLength * 0.28));
        double maxDepth = 0.020 + 0.030 * descriptor.erosionMaturity();

        List<ControlCandidate> candidates = new ArrayList<>();
        candidates.add(new ControlCandidate(midpoint, 0.0));
        double[] lateralFactors = {-1.0, -0.55, 0.55, 1.0};
        double[] depthFactors = {-0.65, 0.0, 0.65};
        for (double lateralFactor : lateralFactors) {
            for (double depthFactor : depthFactors) {
                SkyIslandSubsurfacePosition control = offset(
                        midpoint,
                        normalX * maxLateral * lateralFactor,
                        normalZ * maxLateral * lateralFactor,
                        maxDepth * depthFactor);
                candidates.add(new ControlCandidate(
                        control,
                        Math.abs(lateralFactor) + 0.5 * Math.abs(depthFactor)));
            }
        }

        EvaluatedCurve best = null;
        for (int index = 0; index < candidates.size(); index++) {
            ControlCandidate candidate = candidates.get(index);
            EvaluatedCurve evaluated = evaluateCurve(
                    descriptor,
                    regionPlan,
                    geology,
                    semantic,
                    membership,
                    first.position(),
                    candidate.control(),
                    second.position(),
                    link.kind(),
                    candidate.curvature(),
                    index);
            if (evaluated == null) {
                continue;
            }
            if (best == null || EvaluatedCurve.ORDER.compare(evaluated, best) < 0) {
                best = evaluated;
            }
        }
        if (best == null) {
            throw new IllegalStateException("AUTH-0025 could not keep an accepted cave link inside island ownership");
        }

        double chamberScale = Math.min(
                firstChamber.horizontalRadius(),
                secondChamber.horizontalRadius());
        double baseHorizontalRadius = clamp(
                chamberScale
                        * (0.34
                                + 0.12 * best.support()
                                + 0.08 * linkSupport(link)),
                descriptor.nominalRadius() * 0.010,
                descriptor.nominalRadius() * 0.038);
        double baseDepthRadius = clamp(
                Math.min(firstChamber.depthRadius(), secondChamber.depthRadius())
                        * (0.42 + 0.18 * best.support()),
                0.010,
                0.055);

        List<SkyIslandCavePassagePoint> points = new ArrayList<>(PASSAGE_SAMPLES);
        for (int sample = 0; sample < PASSAGE_SAMPLES; sample++) {
            double t = sample / (PASSAGE_SAMPLES - 1.0);
            SkyIslandSubsurfacePosition position = quadratic(
                    first.position(),
                    best.control(),
                    second.position(),
                    t);
            double middleExpansion = 0.84 + 0.16 * Math.sin(Math.PI * t);
            double horizontalRadius = baseHorizontalRadius * middleExpansion;
            double requestedDepthRadius = baseDepthRadius * middleExpansion;
            double allowance = 0.82 * Math.min(
                    position.depthFraction(),
                    1.0 - position.depthFraction());
            double depthRadius = Math.max(0.008, Math.min(requestedDepthRadius, allowance));
            points.add(new SkyIslandCavePassagePoint(
                    position,
                    horizontalRadius,
                    depthRadius));
        }

        return new SkyIslandCavePassageGeometry(
                link.linkId(),
                link.kind(),
                points,
                best.support());
    }

    private static EvaluatedCurve evaluateCurve(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan regionPlan,
            SkyIslandGeologyFieldSet geology,
            SkyIslandSemanticFieldSet semantic,
            RegionMembership membership,
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition control,
            SkyIslandSubsurfacePosition second,
            SkyIslandCaveConnectionKind kind,
            double curvature,
            int tieBreak) {
        if (control.depthFraction() <= 0.0 || control.depthFraction() >= 1.0
                || semantic.interiority().sample(control.surfacePosition()) <= 0.0) {
            return null;
        }

        double sum = 0.0;
        for (int sample = 0; sample < PASSAGE_SAMPLES; sample++) {
            double t = sample / (PASSAGE_SAMPLES - 1.0);
            SkyIslandSubsurfacePosition position = quadratic(first, control, second, t);
            if (position.depthFraction() <= 0.0
                    || position.depthFraction() >= 1.0
                    || semantic.interiority().sample(position.surfacePosition()) <= 0.0) {
                return null;
            }

            SkyIslandGeologySample local = geology.sample(position);
            int cellIndex = nearestCellIndex(
                    position,
                    descriptor.nominalRadius(),
                    regionPlan);
            double fracture = membership.fracture()[cellIndex];
            double aquifer = membership.aquifer()[cellIndex];
            double support = switch (kind) {
                case VOID_CONTINUITY ->
                        0.66 * local.voidFormationPotential()
                                + 0.18 * fracture
                                + 0.16 * aquifer;
                case FRACTURE_BRIDGE ->
                        0.48 * fracture
                                + 0.37 * local.voidFormationPotential()
                                + 0.15 * aquifer;
                case AQUIFER_BRIDGE ->
                        0.48 * aquifer
                                + 0.37 * local.voidFormationPotential()
                                + 0.15 * fracture;
                case MIXED_GEOLOGIC_BRIDGE ->
                        0.34 * fracture
                                + 0.34 * aquifer
                                + 0.32 * local.voidFormationPotential();
            };
            sum += support;
        }

        double meanSupport = clamp01(sum / PASSAGE_SAMPLES);
        double curvaturePreference = Math.min(1.0, curvature) * 0.012;
        double score = meanSupport + curvaturePreference;
        return new EvaluatedCurve(control, meanSupport, score, tieBreak);
    }

    private static SkyIslandCaveNode node(SkyIslandCaveSystem system, int nodeId) {
        return system.nodes().stream()
                .filter(node -> node.nodeId() == nodeId)
                .findFirst()
                .orElseThrow();
    }

    private static double linkSupport(SkyIslandCaveLink link) {
        return clamp01(Math.max(link.fractureSupport(), link.aquiferSupport()));
    }

    private static SkyIslandSubsurfacePosition midpoint(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second) {
        return new SkyIslandSubsurfacePosition(
                0.5 * (first.x() + second.x()),
                0.5 * (first.z() + second.z()),
                0.5 * (first.depthFraction() + second.depthFraction()));
    }

    private static SkyIslandSubsurfacePosition offset(
            SkyIslandSubsurfacePosition position,
            double dx,
            double dz,
            double depthOffset) {
        double depth = clamp(position.depthFraction() + depthOffset, 0.001, 0.999);
        return new SkyIslandSubsurfacePosition(
                position.x() + dx,
                position.z() + dz,
                depth);
    }

    private static SkyIslandSubsurfacePosition quadratic(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition control,
            SkyIslandSubsurfacePosition second,
            double t) {
        double oneMinus = 1.0 - t;
        double a = oneMinus * oneMinus;
        double b = 2.0 * oneMinus * t;
        double c = t * t;
        return new SkyIslandSubsurfacePosition(
                a * first.x() + b * control.x() + c * second.x(),
                a * first.z() + b * control.z() + c * second.z(),
                a * first.depthFraction()
                        + b * control.depthFraction()
                        + c * second.depthFraction());
    }

    private static int nearestCellIndex(
            SkyIslandSubsurfacePosition position,
            double radius,
            SkyIslandGeologicRegionPlan plan) {
        int ix = clampIndex(
                (int) Math.round((position.x() + radius) / plan.horizontalSpacing()),
                plan.gridSize());
        int iz = clampIndex(
                (int) Math.round((position.z() + radius) / plan.horizontalSpacing()),
                plan.gridSize());
        int id = clampIndex(
                (int) Math.round(position.depthFraction() / plan.depthSpacing()),
                plan.depthSamples());
        return (iz * plan.depthSamples() + id) * plan.gridSize() + ix;
    }

    private static int clampIndex(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private record ControlCandidate(
            SkyIslandSubsurfacePosition control,
            double curvature) {}

    private record EvaluatedCurve(
            SkyIslandSubsurfacePosition control,
            double support,
            double score,
            int tieBreak) {
        private static final Comparator<EvaluatedCurve> ORDER = Comparator
                .comparingDouble(EvaluatedCurve::score)
                .reversed()
                .thenComparingInt(EvaluatedCurve::tieBreak);
    }

    private record RegionMembership(
            double[] fracture,
            double[] aquifer) {
        private static RegionMembership from(SkyIslandGeologicRegionPlan plan) {
            int total = plan.gridSize() * plan.depthSamples() * plan.gridSize();
            double[] fracture = new double[total];
            double[] aquifer = new double[total];
            for (SkyIslandGeologicRegion region : plan.regions()) {
                double[] target = switch (region.kind()) {
                    case FRACTURE_CORRIDOR -> fracture;
                    case AQUIFER_BODY -> aquifer;
                    case VOID_PRONE_DOMAIN -> null;
                };
                if (target == null) {
                    continue;
                }
                for (SkyIslandGeologicRegionCell cell : region.cells()) {
                    target[cell.index()] = Math.max(target[cell.index()], cell.membership());
                }
            }
            return new RegionMembership(fracture, aquifer);
        }
    }
}
