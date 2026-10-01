package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generates three-resolution collocation evidence for solved transition-free F2C reaches. */
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
        SkyIslandBoundedHydraulicProfilePlan coarsePlan =
                SkyIslandBoundedHydraulicProfilePlanner.plan(descriptor);

        List<SkyIslandBoundedHydraulicConvergenceDiagnostics> result = new ArrayList<>();
        for (SkyIslandBoundedHydraulicReachOutcome coarseOutcome : coarsePlan.outcomes()) {
            if (coarseOutcome.hydraulicReach().isEmpty()) {
                continue;
            }

            SkyIslandHydraulicReachSkeleton mediumSkeleton =
                    SkyIslandHydraulicCollocationRefiner.refineMidpoints(
                            descriptor, coarseOutcome.skeleton());
            SkyIslandBoundedHydraulicReachOutcome mediumOutcome =
                    requireSolved(
                            descriptor,
                            mediumSkeleton,
                            terrain,
                            policy,
                            "first midpoint refinement");

            SkyIslandHydraulicReachSkeleton fineSkeleton =
                    SkyIslandHydraulicCollocationRefiner.refineMidpoints(
                            descriptor, mediumSkeleton);
            SkyIslandBoundedHydraulicReachOutcome fineOutcome =
                    requireSolved(
                            descriptor,
                            fineSkeleton,
                            terrain,
                            policy,
                            "second midpoint refinement");

            SolvedEvidence coarse = evidence(coarseOutcome, descriptor, policy);
            SolvedEvidence medium = evidence(mediumOutcome, descriptor, policy);
            SolvedEvidence fine = evidence(fineOutcome, descriptor, policy);
            var semantic =
                    coarseOutcome.skeleton().geomorphicRoute().semanticReach();

            result.add(new SkyIslandBoundedHydraulicConvergenceDiagnostics(
                    semantic.startCellIndex(),
                    semantic.endCellIndex(),
                    coarse.geometry().samples().size(),
                    medium.geometry().samples().size(),
                    fine.geometry().samples().size(),
                    coarse.geometry().startWaterSurfacePotential() * descriptor.reliefBudget(),
                    medium.geometry().startWaterSurfacePotential() * descriptor.reliefBudget(),
                    fine.geometry().startWaterSurfacePotential() * descriptor.reliefBudget(),
                    coarse.geometry().endWaterSurfacePotential() * descriptor.reliefBudget(),
                    medium.geometry().endWaterSurfacePotential() * descriptor.reliefBudget(),
                    fine.geometry().endWaterSurfacePotential() * descriptor.reliefBudget(),
                    coarse.solve().objective() / coarse.geometry().pathLength(),
                    medium.solve().objective() / medium.geometry().pathLength(),
                    fine.solve().objective() / fine.geometry().pathLength(),
                    coarse.diagnostics().maximumLongitudinalGrade(),
                    medium.diagnostics().maximumLongitudinalGrade(),
                    fine.diagnostics().maximumLongitudinalGrade(),
                    coarse.diagnostics().excavationVolumeProxyWorldUnitsCubed(),
                    medium.diagnostics().excavationVolumeProxyWorldUnitsCubed(),
                    fine.diagnostics().excavationVolumeProxyWorldUnitsCubed(),
                    coarse.diagnostics().maximumCenterlineLoweringWorldUnits(),
                    medium.diagnostics().maximumCenterlineLoweringWorldUnits(),
                    fine.diagnostics().maximumCenterlineLoweringWorldUnits(),
                    coarse.headDependentPass(),
                    medium.headDependentPass(),
                    fine.headDependentPass()));
        }
        return List.copyOf(result);
    }

    private static SkyIslandBoundedHydraulicReachOutcome requireSolved(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            String refinementLabel) {
        SkyIslandBoundedHydraulicReachOutcome outcome =
                SkyIslandBoundedHydraulicProfilePlanner.solveOrdinaryReach(
                        descriptor, skeleton, terrain, policy);
        if (outcome.hydraulicReach().isEmpty()) {
            throw new IllegalStateException(
                    refinementLabel
                            + " invalidated an F2C solved reach: "
                            + outcome.status()
                            + " "
                            + outcome.diagnostic().orElse(""));
        }
        return outcome;
    }

    private static SolvedEvidence evidence(
            SkyIslandBoundedHydraulicReachOutcome outcome,
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicQualificationPolicy policy) {
        SkyIslandHydraulicReachGeometry geometry =
                outcome.hydraulicReach().orElseThrow();
        SkyIslandGeomorphicReachDiagnostics diagnostics =
                outcome.qualification().orElseThrow().diagnostics();
        SkyIslandGeomorphicProfileLimits limits =
                policy.limits(geometry.geomorphicRoute().semanticReach());
        return new SolvedEvidence(
                geometry,
                outcome.solverResult().orElseThrow(),
                diagnostics,
                headDependentD2Pass(diagnostics, limits));
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

    private record SolvedEvidence(
            SkyIslandHydraulicReachGeometry geometry,
            SkyIslandHydraulicQpResult solve,
            SkyIslandGeomorphicReachDiagnostics diagnostics,
            boolean headDependentPass) {}
}
