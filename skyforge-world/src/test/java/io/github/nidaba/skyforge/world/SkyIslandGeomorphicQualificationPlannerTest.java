package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicQualificationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void qualificationIsDeterministicAndReportsFiniteMetricsAcrossCorpus() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeomorphicQualificationPlan first =
                    SkyIslandGeomorphicQualificationPlanner.qualify(descriptor);
            SkyIslandGeomorphicQualificationPlan second =
                    SkyIslandGeomorphicQualificationPlanner.qualify(descriptor);
            assertEquals(first, second);

            for (SkyIslandGeomorphicReachQualification reach : first.reaches()) {
                assertTrue(Double.isFinite(reach.maximumCenterlineLoweringPotential()));
                assertTrue(Double.isFinite(reach.maximumCenterlineLoweringWorldUnits()));
                assertTrue(Double.isFinite(reach.maximumLateralRecoveryGrade()));
                assertTrue(Double.isFinite(reach.normalizedExcavationBurden()));
                assertTrue(Double.isFinite(reach.maximumCurvatureWidthRatio()));
                assertTrue(Double.isFinite(reach.maximumLongitudinalGrade()));
                assertTrue(Double.isFinite(reach.excavationVolumeProxyWorldUnitsCubed()));
            }
        }
    }

    @Test
    void gentleSyntheticReachCanPassProvisionalEnvelope() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandHydraulicReachGeometry reach = syntheticReach(
                descriptor,
                0.60,
                List.of(0.5800, 0.5795, 0.5790),
                List.of(0.5600, 0.5595, 0.5590),
                5.0,
                0.0);
        SkyIslandGeomorphicReachQualification qualification =
                SkyIslandGeomorphicQualificationPlanner.qualifyReach(
                        descriptor,
                        reach,
                        ignored -> 0.60,
                        SkyIslandGeomorphicQualificationPolicy.provisional());

        assertTrue(qualification.accepted(), qualification.violations().toString());
    }

    @Test
    void quarryLikeSyntheticReachIsRejectedBeforeTerrainMutation() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandHydraulicReachGeometry reach = syntheticReach(
                descriptor,
                0.80,
                List.of(0.550, 0.545, 0.540),
                List.of(0.530, 0.525, 0.520),
                1.5,
                0.35);
        SkyIslandGeomorphicReachQualification qualification =
                SkyIslandGeomorphicQualificationPlanner.qualifyReach(
                        descriptor,
                        reach,
                        ignored -> 0.80,
                        SkyIslandGeomorphicQualificationPolicy.provisional());

        assertFalse(qualification.accepted());
        assertTrue(qualification.violations().contains(
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING));
        assertTrue(qualification.violations().contains(
                SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE));
        assertTrue(qualification.violations().contains(
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN));
        assertTrue(qualification.violations().contains(
                SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY));
    }

    private static SkyIslandHydraulicReachGeometry syntheticReach(
            SkyIslandDescriptor descriptor,
            double terrain,
            List<Double> surfaces,
            List<Double> beds,
            double halfWidth,
            double ridgeFraction) {
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
        SkyIslandGeomorphicCandidateRoute route = new SkyIslandGeomorphicCandidateRoute(
                points,
                1.0,
                10.0,
                0.0,
                0.0,
                ridgeFraction,
                0.0,
                0.0);
        SkyIslandGeomorphicReachRoute geomorphic =
                new SkyIslandGeomorphicReachRoute(semantic, route);

        List<SkyIslandHydraulicGeometrySample> samples = java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> new SkyIslandHydraulicGeometrySample(
                        points.get(i),
                        i / 2.0,
                        0.30,
                        halfWidth,
                        surfaces.get(i) - beds.get(i),
                        terrain,
                        surfaces.get(i),
                        beds.get(i),
                        Math.max(0.0, terrain - beds.get(i))))
                .toList();

        double maxLowering = samples.stream()
                .mapToDouble(SkyIslandHydraulicGeometrySample::requiredCenterlineLowering)
                .max()
                .orElseThrow();
        double meanLowering = samples.stream()
                .mapToDouble(SkyIslandHydraulicGeometrySample::requiredCenterlineLowering)
                .average()
                .orElseThrow();
        double maxSlope = Math.max(
                Math.abs(surfaces.get(1) - surfaces.get(0)) / 5.0,
                Math.abs(surfaces.get(2) - surfaces.get(1)) / 5.0);

        return new SkyIslandHydraulicReachGeometry(
                geomorphic,
                samples,
                10.0,
                maxLowering,
                meanLowering,
                maxSlope,
                halfWidth,
                samples.stream()
                        .mapToDouble(SkyIslandHydraulicGeometrySample::waterDepthPotential)
                        .max()
                        .orElseThrow());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
