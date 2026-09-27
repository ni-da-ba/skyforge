package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * F4F centerline-only, depth-preserving world-head refinement.
 *
 * <p>The solve is deliberately one-sided. It may raise the direct F4E water head and the paired
 * terrain target together, thereby reducing previously authorized excavation, but it may never
 * lower either surface below F4B. Physical bank containment and the remaining pre-hydrologic carve
 * margin bound every sample from above.
 */
public final class SkyIslandWorldWaterHeadRefinementPlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandWorldWaterHeadRefinementPlanner() {}

    public static SkyIslandWorldWaterHeadRefinementPlan plan(
            SkyIslandWorldWaterProjectionQualificationPlan directQualification) {
        Objects.requireNonNull(directQualification, "directQualification");

        List<SkyIslandWorldWaterHeadRefinementComponent> outcomes = new ArrayList<>();
        for (SkyIslandWorldWaterComponentQualification component :
                directQualification.refinementRequiredComponents()) {
            outcomes.add(refineComponent(directQualification, component));
        }
        outcomes.sort(Comparator.comparingInt(
                SkyIslandWorldWaterHeadRefinementComponent::terminalCellIndex));
        return new SkyIslandWorldWaterHeadRefinementPlan(
                directQualification, outcomes);
    }

    private static SkyIslandWorldWaterHeadRefinementComponent refineComponent(
            SkyIslandWorldWaterProjectionQualificationPlan directQualification,
            SkyIslandWorldWaterComponentQualification component) {
        if (component.reaches().size() != 1
                || component.component().reaches().size() != 1) {
            return new SkyIslandWorldWaterHeadRefinementComponent(
                    component.component(),
                    SkyIslandWorldWaterHeadRefinementStatus.UNSUPPORTED_TOPOLOGY,
                    List.of(),
                    List.of("F4F first evidence supports one realized ordinary reach per terminal component"));
        }

        SkyIslandWorldWaterReachQualification directReach =
                component.reaches().getFirst();
        SkyIslandHydraulicReachGeometry geometry =
                directReach.diagnostics().reach();
        SkyIslandWorldWaterHeadRefinementReach refined =
                refineReach(directQualification, geometry);
        if (refined.status() == SkyIslandWorldWaterHeadRefinementStatus.SOLVED) {
            return new SkyIslandWorldWaterHeadRefinementComponent(
                    component.component(),
                    SkyIslandWorldWaterHeadRefinementStatus.SOLVED,
                    List.of(refined),
                    List.of());
        }
        return new SkyIslandWorldWaterHeadRefinementComponent(
                component.component(),
                refined.status(),
                List.of(refined),
                List.of(refined.diagnostic().orElse("F4F world-head refinement failed")));
    }

    private static SkyIslandWorldWaterHeadRefinementReach refineReach(
            SkyIslandWorldWaterProjectionQualificationPlan directQualification,
            SkyIslandHydraulicReachGeometry reach) {
        SkyIslandFluvialVoxelQuantizationPlan voxelPlan =
                directQualification.terrainVoxelPlan();
        SkyIslandAuthoredRealizationAssociation association =
                voxelPlan.association();
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                voxelPlan.candidatePlan();
        SkyIslandDescriptor descriptor = association.authoredDescriptor();
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        SkyIslandGeomorphicProfileLimits limits = policy.limits(semantic);

        SkyIslandComponentFluvialWorldWaterProjection waterProjection =
                new SkyIslandComponentFluvialWorldWaterProjection(
                        association, candidate);
        SkyIslandComponentFluvialWorldSurfaceProjection terrainProjection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidate);

        var physical =
                association.realizedVolume().compiledVolume().descriptor();
        List<SkyIslandLocalPosition> points = reach.centerline().points();
        List<SkyIslandHydraulicGeometrySample> geometrySamples = reach.samples();
        int n = points.size();

        double[] target = new double[n];
        double[] lower = new double[n];
        double[] upper = new double[n];
        double[] weight = quadratureWeights(points);
        double[] directTerrain = new double[n];
        double[] depth = new double[n];
        double[] maximumRaise = new double[n];
        double[] bankUpper = new double[n];

        for (int i = 0; i < n; i++) {
            SkyIslandLocalPosition local = points.get(i);
            double worldX = physical.centerX() + local.x();
            double worldZ = physical.centerZ() + local.z();

            SkyIslandProjectedFluvialWaterSample water =
                    waterProjection.sampleWorld(worldX, worldZ);
            if (!water.wet()) {
                return failure(
                        reach,
                        SkyIslandWorldWaterHeadRefinementStatus.INFEASIBLE,
                        "F4F realized centerline sample lost F4E water authority");
            }
            SkyIslandProjectedFluvialTerrainSample center =
                    water.terrainProjection();

            Vector tangent = tangent(points, i);
            double normalX = -tangent.z();
            double normalZ = tangent.x();
            double bankfull =
                    geometrySamples.get(i).bankfullHalfWidth();
            SkyIslandProjectedFluvialTerrainSample leftBank =
                    terrainProjection.sampleWorld(
                            worldX + normalX * bankfull,
                            worldZ + normalZ * bankfull);
            SkyIslandProjectedFluvialTerrainSample rightBank =
                    terrainProjection.sampleWorld(
                            worldX - normalX * bankfull,
                            worldZ - normalZ * bankfull);

            double directHead = water.waterSurfaceWorldY().orElseThrow();
            double availableRaise = Math.max(0.0, -center.terrainDeltaWorldUnits());
            double containmentUpper =
                    Math.min(
                                    leftBank.targetUpperSurfaceWorldY(),
                                    rightBank.targetUpperSurfaceWorldY())
                            + limits.maximumBankContainmentDeficitWorldUnits();
            double noTerrainRaiseUpper = directHead + availableRaise;
            double admissibleUpper = Math.min(containmentUpper, noTerrainRaiseUpper);

            if (directHead > admissibleUpper + EPSILON) {
                String binding =
                        containmentUpper <= noTerrainRaiseUpper
                                ? "BANK_CONTAINMENT"
                                : "PREHYDROLOGIC_SURFACE";
                return failure(
                        reach,
                        SkyIslandWorldWaterHeadRefinementStatus.INFEASIBLE,
                        String.format(
                                Locale.ROOT,
                                "direct F4E head %.9f exceeds F4F upward-only physical bound %.9f"
                                        + " at sample %d [binding=%s, bankUpper=%.9f,"
                                        + " noRaiseUpper=%.9f, availableRaise=%.9f]",
                                directHead,
                                admissibleUpper,
                                i,
                                binding,
                                containmentUpper,
                                noTerrainRaiseUpper,
                                availableRaise));
            }
            if (admissibleUpper < directHead) {
                admissibleUpper = directHead;
            }

            target[i] = directHead;
            lower[i] = directHead;
            upper[i] = admissibleUpper;
            directTerrain[i] = center.targetUpperSurfaceWorldY();
            depth[i] = water.waterDepthWorldUnits();
            maximumRaise[i] = availableRaise;
            bankUpper[i] = containmentUpper;
        }

        List<SkyIslandHydraulicDifferenceConstraint> differences =
                differenceConstraints(points, limits.maximumLongitudinalGrade());
        SkyIslandHydraulicQpResult solve =
                SkyIslandHydraulicBoundedQpSolver.solve(
                        new SkyIslandHydraulicBoundedQpProblem(
                                target, weight, lower, upper, differences));
        if (solve.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return failure(
                    reach,
                    SkyIslandWorldWaterHeadRefinementStatus.INFEASIBLE,
                    solve.diagnostic().orElse("F4F bounded head refinement is infeasible"),
                    solve);
        }
        if (solve.status() != SkyIslandHydraulicQpStatus.SOLVED) {
            return failure(
                    reach,
                    SkyIslandWorldWaterHeadRefinementStatus.NUMERICAL_FAILURE,
                    solve.diagnostic().orElse("F4F bounded head refinement numerical failure"),
                    solve);
        }

        double[] solution = solve.solution();
        List<SkyIslandWorldWaterHeadRefinementSample> samples =
                new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double raise = solution[i] - target[i];
            if (raise < 0.0 && raise > -EPSILON) {
                raise = 0.0;
            }
            samples.add(new SkyIslandWorldWaterHeadRefinementSample(
                    points.get(i),
                    target[i],
                    solution[i],
                    depth[i],
                    directTerrain[i],
                    directTerrain[i] + raise,
                    raise,
                    maximumRaise[i],
                    bankUpper[i]));
        }

        SkyIslandWorldWaterHeadRefinementReach result =
                new SkyIslandWorldWaterHeadRefinementReach(
                        reach,
                        SkyIslandWorldWaterHeadRefinementStatus.SOLVED,
                        samples,
                        Optional.of(solve),
                        Optional.empty());
        if (result.uphillSegments(EPSILON) != 0) {
            return failure(
                    reach,
                    SkyIslandWorldWaterHeadRefinementStatus.NUMERICAL_FAILURE,
                    "F4F solved profile retained an uphill segment",
                    solve);
        }
        return result;
    }

    private static List<SkyIslandHydraulicDifferenceConstraint> differenceConstraints(
            List<SkyIslandLocalPosition> points,
            double maximumGrade) {
        List<SkyIslandHydraulicDifferenceConstraint> result =
                new ArrayList<>(Math.max(0, points.size() - 1));
        for (int i = 0; i + 1 < points.size(); i++) {
            double ds = distance(points.get(i), points.get(i + 1));
            if (!(ds > 0.0)) {
                throw new IllegalStateException("F4F centerline distance must increase");
            }
            result.add(new SkyIslandHydraulicDifferenceConstraint(
                    "f4f:grade:" + i,
                    i,
                    i + 1,
                    0.0,
                    maximumGrade * ds));
        }
        return List.copyOf(result);
    }

    private static double[] quadratureWeights(List<SkyIslandLocalPosition> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("F4F reach requires at least two centerline samples");
        }
        double[] result = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            if (i == 0) {
                result[i] = 0.5 * distance(points.get(0), points.get(1));
            } else if (i == points.size() - 1) {
                result[i] = 0.5 * distance(points.get(i - 1), points.get(i));
            } else {
                result[i] =
                        0.5 * (distance(points.get(i - 1), points.get(i))
                                + distance(points.get(i), points.get(i + 1)));
            }
            if (!(result[i] > 0.0)) {
                throw new IllegalStateException("F4F quadrature weight must be positive");
            }
        }
        return result;
    }

    private static Vector tangent(List<SkyIslandLocalPosition> points, int index) {
        SkyIslandLocalPosition a =
                index == 0 ? points.get(0) : points.get(index - 1);
        SkyIslandLocalPosition b =
                index == points.size() - 1
                        ? points.get(index)
                        : points.get(index + 1);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        if (!(length > 0.0)) {
            throw new IllegalStateException("F4F centerline tangent must be non-zero");
        }
        return new Vector(dx / length, dz / length);
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private static SkyIslandWorldWaterHeadRefinementReach failure(
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandWorldWaterHeadRefinementStatus status,
            String diagnostic) {
        return failure(reach, status, diagnostic, null);
    }

    private static SkyIslandWorldWaterHeadRefinementReach failure(
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandWorldWaterHeadRefinementStatus status,
            String diagnostic,
            SkyIslandHydraulicQpResult solve) {
        return new SkyIslandWorldWaterHeadRefinementReach(
                reach,
                status,
                List.of(),
                Optional.ofNullable(solve),
                Optional.of(diagnostic));
    }

    private record Vector(double x, double z) {}
}
