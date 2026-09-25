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
    void qualificationIsDeterministicAndReportsExplicitFailureCauses() {
        for (long key : new long[] {287L, 632L, 649L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeomorphicQualificationPlan first =
                    SkyIslandGeomorphicQualificationPlanner.plan(descriptor);
            SkyIslandGeomorphicQualificationPlan second =
                    SkyIslandGeomorphicQualificationPlanner.plan(descriptor);

            assertEquals(first, second);
            assertEquals(
                    first.hydraulicPlan().reaches().size(),
                    first.reaches().size());
            for (SkyIslandGeomorphicReachQualification reach : first.reaches()) {
                assertTrue(Double.isFinite(reach.maximumCenterlineLoweringWorld()));
                assertTrue(Double.isFinite(reach.maximumIncisionToFullWidthRatio()));
                assertTrue(Double.isFinite(reach.maximumWaterDepthToFullWidthRatio()));
                assertTrue(Double.isFinite(reach.impliedMaximumLateralGrade()));
                assertTrue(Double.isFinite(reach.maximumLongitudinalWaterSurfaceGrade()));
                assertTrue(Double.isFinite(reach.minimumCurvatureRadiusToFullWidthRatio()));
                assertTrue(Double.isFinite(reach.normalizedExcavationVolume()));
            }
        }
    }

    @Test
    void pathologicalNarrowDeepTrenchIsRejectedBeforeTerrainMutation() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandChannelSegment segment = new SkyIslandChannelSegment(
                1,
                2,
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(20.0, 0.0),
                1,
                SkyIslandChannelRole.HEADWATER,
                0.25,
                0.25);
        SkyIslandChannelProfile profile = new SkyIslandChannelProfile(
                segment,
                SkyIslandChannelProfileKind.ALLUVIAL,
                0.2,
                0.2,
                0.2,
                0.2,
                0.2);
        SkyIslandSemanticChannelReach semantic =
                new SkyIslandSemanticChannelReach(1, 2, List.of(profile));

        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0),
                new SkyIslandLocalPosition(20.0, 0.0));
        SkyIslandGeomorphicCandidateRoute candidate =
                new SkyIslandGeomorphicCandidateRoute(
                        points,
                        1.0,
                        20.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0);
        SkyIslandGeomorphicReachRoute geomorphic =
                new SkyIslandGeomorphicReachRoute(semantic, candidate);

        List<SkyIslandHydraulicGeometrySample> samples = List.of(
                sample(points.get(0), 0.0, 0.70, 0.40),
                sample(points.get(1), 0.5, 0.69, 0.39),
                sample(points.get(2), 1.0, 0.68, 0.38));
        SkyIslandHydraulicReachGeometry hydraulic =
                new SkyIslandHydraulicReachGeometry(
                        geomorphic,
                        samples,
                        20.0,
                        0.30,
                        0.30,
                        0.001,
                        1.5,
                        0.02);
        SkyIslandContinuousChannelCenterline centerline =
                new SkyIslandContinuousChannelCenterline(
                        candidate,
                        points,
                        20.0,
                        0.0,
                        0.0);

        SkyIslandGeomorphicReachQualification result =
                SkyIslandGeomorphicQualificationPlanner.qualifyReach(
                        descriptor, hydraulic, centerline);

        assertFalse(result.accepted());
        assertTrue(result.failures().contains(
                SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_CENTERLINE_LOWERING));
        assertTrue(result.failures().contains(
                SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_INCISION_TO_WIDTH));
        assertTrue(result.failures().contains(
                SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_LATERAL_GRADE));
    }

    private static SkyIslandHydraulicGeometrySample sample(
            SkyIslandLocalPosition position,
            double fraction,
            double surface,
            double bed) {
        return new SkyIslandHydraulicGeometrySample(
                position,
                fraction,
                0.25,
                1.5,
                0.02,
                0.70,
                surface,
                bed,
                0.30);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
