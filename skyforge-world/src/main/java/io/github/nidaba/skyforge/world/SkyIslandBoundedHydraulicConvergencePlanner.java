package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generates midpoint-refinement evidence for solved transition-free F2C reaches. */
public final class SkyIslandBoundedHydraulicConvergencePlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandBoundedHydraulicConvergencePlanner() {}

    public static List<SkyIslandBoundedHydraulicConvergenceDiagnostics> measure(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandBoundedHydraulicProfilePlan nativePlan =
                SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor);

        List<SkyIslandBoundedHydraulicConvergenceDiagnostics> result = new ArrayList<>();
        for (SkyIslandBoundedHydraulicReachOutcome nativeOutcome : nativePlan.outcomes()) {
            if (nativeOutcome.hydraulicReach().isEmpty()) {
                continue;
            }

            SkyIslandHydraulicReachSkeleton refinedSkeleton =
                    SkyIslandHydraulicCollocationRefiner.refineMidpoints(
                            descriptor, nativeOutcome.skeleton());
            SkyIslandBoundedHydraulicReachOutcome refinedOutcome =
                    SkyIslandBoundedHydraulicProfilePlanner.solveOrdinaryReach(
                            descriptor, refinedSkeleton, terrain, policy);
            if (refinedOutcome.hydraulicReach().isEmpty()) {
                throw new IllegalStateException(
                        "midpoint refinement invalidated an F2C solved reach: "
                                + refinedOutcome.status()
                                + " "
                                + refinedOutcome.diagnostic().orElse(""));
            }

            SkyIslandHydraulicReachGeometry nativeReach =
                    nativeOutcome.hydraulicReach().orElseThrow();
            SkyIslandHydraulicReachGeometry refinedReach =
                    refinedOutcome.hydraulicReach().orElseThrow();
            SkyIslandGeomorphicReachDiagnostics nativeDiagnostics =
                    nativeOutcome.qualification().orElseThrow().diagnostics();
            SkyIslandGeomorphicReachDiagnostics refinedDiagnostics =
                    refinedOutcome.qualification().orElseThrow().diagnostics();
            SkyIslandGeomorphicProfileLimits limits =
                    policy.limits(nativeReach.geomorphicRoute().semanticReach());
            double relief = descriptor.reliefBudget();
            SkyIslandHydraulicQpResult nativeSolve =
                    nativeOutcome.solverResult().orElseThrow();
            SkyIslandHydraulicQpResult refinedSolve =
                    refinedOutcome.solverResult().orElseThrow();
            var semantic = nativeReach.geomorphicRoute().semanticReach();

            result.add(new SkyIslandBoundedHydraulicConvergenceDiagnostics(
                    semantic.startCellIndex(),
                    semantic.endCellIndex(),
                    nativeReach.samples().size(),
                    refinedReach.samples().size(),
                    nativeReach.startWaterSurfacePotential() * relief,
                    refinedReach.startWaterSurfacePotential() * relief,
                    nativeReach.endWaterSurfacePotential() * relief,
                    refinedReach.endWaterSurfacePotential() * relief,
                    nativeSolve.objective() / nativeReach.pathLength(),
                    refinedSolve.objective() / refinedReach.pathLength(),
                    nativeDiagnostics.maximumLongitudinalGrade(),
                    refinedDiagnostics.maximumLongitudinalGrade(),
                    nativeDiagnostics.excavationVolumeProxyWorldUnitsCubed(),
                    refinedDiagnostics.excavationVolumeProxyWorldUnitsCubed(),
                    nativeDiagnostics.maximumCenterlineLoweringWorldUnits(),
                    refinedDiagnostics.maximumCenterlineLoweringWorldUnits(),
                    headDependentD2Pass(nativeDiagnostics, limits),
                    headDependentD2Pass(refinedDiagnostics, limits)));
        }
        return List.copyOf(result);
    }

    private static boolean headDependentD2Pass(
            SkyIslandGeomorphicReachDiagnostics diagnostics,
            SkyIslandGeomorphicProfileLimits limits) {
        return diagnostics.maximumCenterlineLoweringPotential()
                        <= limits.maximumCenterlineLoweringPotential() + EPSILON
                && diagnostics.maximumLateralRecoveryGrade()
                        <= limits.maximumLateralRecoveryGrade() + EPSILON
                && diagnostics.maximumBankContainmentDeficitWorldUnits()
                        <= limits.maximumBankContainmentDeficitWorldUnits() + EPSILON
                && diagnostics.maximumReliefToValleyWidthRatio()
                        <= limits.maximumReliefToValleyWidthRatio() + EPSILON
                && diagnostics.normalizedExcavationBurden()
                        <= limits.maximumNormalizedExcavationBurden() + EPSILON
                && diagnostics.maximumLongitudinalGrade()
                        <= limits.maximumLongitudinalGrade() + EPSILON;
    }
}
