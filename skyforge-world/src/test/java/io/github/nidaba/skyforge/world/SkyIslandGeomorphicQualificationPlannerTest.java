package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicQualificationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void generousSyntheticCandidatePassesSafetyEnvelope() {
        SkyIslandGeomorphicReachDiagnostics diagnostics = diagnostics(
                0.03, 0.20, 0.40, 0.10, 0.10, 0.02, 0.40, 0.05, 0.20, 0.005);
        SkyIslandGeomorphicReachQualification result =
                SkyIslandGeomorphicQualificationPlanner.qualifyReach(
                        diagnostics, SkyIslandGeomorphicQualificationPolicy.safetyV1());
        assertTrue(result.accepted(), result.violations().toString());
    }

    @Test
    void quarryLikeCandidateFailsBeforeTerrainAuthoring() {
        SkyIslandGeomorphicReachDiagnostics diagnostics = diagnostics(
                0.35, 2.50, 4.00, 2.00, 0.80, 0.15, 2.50, 0.70, 1.50, 0.05);
        SkyIslandGeomorphicReachQualification result =
                SkyIslandGeomorphicQualificationPlanner.qualifyReach(
                        diagnostics, SkyIslandGeomorphicQualificationPolicy.safetyV1());

        assertFalse(result.accepted());
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.INCISION_TO_WIDTH));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.BANK_CONTAINMENT));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.DEPTH_TO_WIDTH));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.CURVATURE_TO_WIDTH));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.LONGITUDINAL_GRADE));
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.RAW_TERRAIN_ASCENT));
    }

    @Test
    void fixedCorpusQualificationIsDeterministicEvenWhenCandidatesFail() {
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.safetyV1();
        for (long key : new long[] {77L, 83L, 118L, 287L, 512L, 632L, 649L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeomorphicQualificationPlan first =
                    SkyIslandGeomorphicQualificationPlanner.qualify(descriptor, policy);
            SkyIslandGeomorphicQualificationPlan second =
                    SkyIslandGeomorphicQualificationPlanner.qualify(descriptor, policy);
            org.junit.jupiter.api.Assertions.assertEquals(first, second);
        }
    }

    private static SkyIslandGeomorphicReachDiagnostics diagnostics(
            double loweringPotential,
            double incisionToWidth,
            double lateralGrade,
            double containmentToDepth,
            double depthToWidth,
            double excavationBurden,
            double curvatureToWidth,
            double ridgeFraction,
            double longitudinalGrade,
            double rawUphillStep) {
        SkyIslandHydraulicReachGeometry reach = syntheticHydraulicReach();
        return new SkyIslandGeomorphicReachDiagnostics(
                reach,
                loweringPotential,
                loweringPotential * descriptor(287L).reliefBudget(),
                lateralGrade,
                containmentToDepth * 2.0,
                containmentToDepth,
                depthToWidth,
                incisionToWidth,
                lateralGrade * 0.5,
                excavationBurden,
                100.0,
                curvatureToWidth,
                ridgeFraction,
                longitudinalGrade,
                rawUphillStep);
    }

    private static SkyIslandHydraulicReachGeometry syntheticHydraulicReach() {
        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(5.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0));
        SkyIslandChannelSegment segment = new SkyIslandChannelSegment(
                1,
                2,
                points.getFirst(),
                points.getLast(),
                1,
                SkyIslandChannelRole.HEADWATER,
                0.30,
                0.30);
        SkyIslandChannelProfile profile = new SkyIslandChannelProfile(
                segment,
                SkyIslandChannelProfileKind.ALLUVIAL,
                0.10,
                0.20,
                0.30,
                0.25,
                0.20);
        SkyIslandSemanticChannelReach semantic =
                new SkyIslandSemanticChannelReach(1, 2, List.of(profile));
        SkyIslandGeomorphicCandidateRoute search = new SkyIslandGeomorphicCandidateRoute(
                points, 1.0, 10.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        SkyIslandGeomorphicReachRoute geomorphic =
                new SkyIslandGeomorphicReachRoute(semantic, search);
        SkyIslandContinuousChannelCenterline centerline =
                new SkyIslandContinuousChannelCenterline(search, points, 10.0, 0.0, 0.0);
        List<SkyIslandHydraulicGeometrySample> samples = List.of(
                sample(points.get(0), 0.0, 0.60, 0.58),
                sample(points.get(1), 0.5, 0.59, 0.57),
                sample(points.get(2), 1.0, 0.58, 0.56));
        return new SkyIslandHydraulicReachGeometry(
                geomorphic,
                centerline,
                samples,
                10.0,
                0.04,
                0.03,
                0.002,
                5.0,
                0.02);
    }

    private static SkyIslandHydraulicGeometrySample sample(
            SkyIslandLocalPosition position,
            double station,
            double surface,
            double bed) {
        return new SkyIslandHydraulicGeometrySample(
                position,
                station,
                0.30,
                5.0,
                surface - bed,
                0.60,
                surface,
                bed,
                Math.max(0.0, 0.60 - bed));
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
