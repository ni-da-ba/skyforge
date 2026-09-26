package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicGeometrySkeletonPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-12;

    @Test
    void skeletonIsDeterministicAcrossFixedCorpus() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            assertEquals(
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor),
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor));
        }
    }

    @Test
    void legacyPlannerPreservesExactHeadIndependentGeometry() {
        for (long key : new long[] {118L, 287L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                    SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor);
            SkyIslandHydraulicChannelNetworkPlan legacy =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);

            assertEquals(skeleton.reaches().size(), legacy.reaches().size());
            for (int r = 0; r < skeleton.reaches().size(); r++) {
                SkyIslandHydraulicReachSkeleton source = skeleton.reaches().get(r);
                SkyIslandHydraulicReachGeometry realized = legacy.reaches().get(r);
                assertEquals(source.geomorphicRoute(), realized.geomorphicRoute());
                assertEquals(source.centerline(), realized.centerline());
                assertEquals(source.pathLength(), realized.pathLength(), EPSILON);
                assertEquals(
                        source.maximumBankfullHalfWidth(),
                        realized.maximumBankfullHalfWidth(),
                        EPSILON);
                assertEquals(
                        source.maximumWaterDepthPotential(),
                        realized.maximumWaterDepthPotential(),
                        EPSILON);
                assertEquals(source.samples().size(), realized.samples().size());

                for (int i = 0; i < source.samples().size(); i++) {
                    SkyIslandHydraulicGeometrySkeletonSample a = source.samples().get(i);
                    SkyIslandHydraulicGeometrySample b = realized.samples().get(i);
                    assertEquals(a.position(), b.position());
                    assertEquals(a.stationFraction(), b.stationFraction(), EPSILON);
                    assertEquals(a.relativeDischarge(), b.relativeDischarge(), EPSILON);
                    assertEquals(a.bankfullHalfWidth(), b.bankfullHalfWidth(), EPSILON);
                    assertEquals(a.waterDepthPotential(), b.waterDepthPotential(), EPSILON);
                    assertEquals(a.terrainElevation(), b.terrainElevation(), EPSILON);
                }
            }
        }
    }

    @Test
    void calibrationDelegatesRemainBackwardCompatible() {
        double radius = 100.0;
        for (int i = 0; i <= 100; i++) {
            double q = (double) i / 100.0;
            assertEquals(
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(radius, q),
                    SkyIslandHydraulicChannelNetworkPlanner.bankfullHalfWidth(radius, q),
                    EPSILON);
            assertEquals(
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(q),
                    SkyIslandHydraulicChannelNetworkPlanner.waterDepthPotential(q),
                    EPSILON);
        }
    }

    @Test
    void skeletonDischargeWidthAndDepthAreMonotoneWithinReach() {
        SkyIslandHydraulicGeometrySkeletonPlan plan =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor(287L));
        for (SkyIslandHydraulicReachSkeleton reach : plan.reaches()) {
            double previousDischarge = -1.0;
            double previousWidth = -1.0;
            double previousDepth = -1.0;
            for (SkyIslandHydraulicGeometrySkeletonSample sample : reach.samples()) {
                assertTrue(sample.relativeDischarge() + EPSILON >= previousDischarge);
                assertTrue(sample.bankfullHalfWidth() + EPSILON >= previousWidth);
                assertTrue(sample.waterDepthPotential() + EPSILON >= previousDepth);
                previousDischarge = sample.relativeDischarge();
                previousWidth = sample.bankfullHalfWidth();
                previousDepth = sample.waterDepthPotential();
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
