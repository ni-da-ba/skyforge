package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandCaveExposureGeometryPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void exposureGeometryIsDeterministicAndPreservesIntentCardinality() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCaveExposureGeometryPlan first =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor);
            SkyIslandCaveExposureGeometryPlan second =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor);

            assertEquals(first, second);
            assertEquals(first.exposurePlan().intents().size(), first.connectionCount());
            assertTrue(first.connectionCount() <= first.exposurePlan().geometry().systems().size());
        }
    }

    @Test
    void acceptedCorpusSidesRemainStableAndSealedSystemsStayGeometryFree() {
        SkyIslandCaveExposureGeometryPlan weak =
                SkyIslandCaveExposureGeometryPlanner.plan(descriptor(653L));
        assertEquals(1, weak.connectionCount());
        assertEquals(SkyIslandCaveExposureSide.UNDERSIDE, weak.connections().getFirst().side());

        SkyIslandCaveExposureGeometryPlan spine =
                SkyIslandCaveExposureGeometryPlanner.plan(descriptor(3670L));
        assertEquals(1, spine.connectionCount());
        assertEquals(SkyIslandCaveExposureSide.UPPER_SURFACE, spine.connections().getFirst().side());

        for (long key : new long[] {2332L, 2211L, 1051L, 1439L}) {
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor(key));
            assertTrue(plan.connections().isEmpty());
        }
    }

    @Test
    void connectionsStayInsideNaturalizedOwnershipAndApproachBoundaryMonotonically() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor);

            for (SkyIslandCaveExposureConnectionGeometry connection : plan.connections()) {
                double previousDepth =
                        connection.points().getFirst().position().depthFraction();
                double previousHorizontalRadius =
                        connection.points().getFirst().horizontalRadius();
                double previousDepthRadius =
                        connection.points().getFirst().depthRadius();

                for (SkyIslandCavePassagePoint point : connection.points()) {
                    assertTrue(semantic.interiority().sample(point.position().surfacePosition()) > 0.0);
                    assertTrue(point.position().depthFraction() >= 0.0);
                    assertTrue(point.position().depthFraction() <= 1.0);
                    assertTrue(point.horizontalRadius() <= previousHorizontalRadius + 1.0e-12);
                    assertTrue(point.depthRadius() <= previousDepthRadius + 1.0e-12);

                    if (connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE) {
                        assertTrue(point.position().depthFraction() <= previousDepth + 1.0e-12);
                    } else {
                        assertTrue(point.position().depthFraction() >= previousDepth - 1.0e-12);
                    }
                    previousDepth = point.position().depthFraction();
                    previousHorizontalRadius = point.horizontalRadius();
                    previousDepthRadius = point.depthRadius();
                }

                double expectedBoundary =
                        connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? 0.0 : 1.0;
                assertEquals(
                        expectedBoundary,
                        connection.mouthPoint().position().depthFraction(),
                        0.0);
            }
        }
    }

    @Test
    void steeringNeverAcceptsGeologicallyWorseRouteThanStraightProjection() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor(key));
            for (SkyIslandCaveExposureConnectionGeometry connection : plan.connections()) {
                assertTrue(connection.steeringSupport()
                        >= connection.straightSupport() - 1.0e-12);
            }
        }
    }

    @Test
    void geometryBeginsAtAcceptedCaveAnchorAndKeepsOneOpeningPerSystem() {
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor(key));
            long distinctSystems = plan.connections().stream()
                    .map(SkyIslandCaveExposureConnectionGeometry::systemId)
                    .distinct()
                    .count();
            assertEquals(plan.connections().size(), distinctSystems);

            for (SkyIslandCaveExposureConnectionGeometry connection : plan.connections()) {
                assertEquals(
                        connection.intent().caveAnchor(),
                        connection.caveSidePoint().position());
                assertEquals(connection.intent().systemId(), connection.systemId());
                assertEquals(connection.intent().side(), connection.side());
            }
        }
    }

    @Test
    void atLeastOneAcceptedExposureUsesMeaningfulGeometricSteering() {
        boolean steered = false;
        for (long key : new long[] {653L, 3670L}) {
            SkyIslandCaveExposureGeometryPlan plan =
                    SkyIslandCaveExposureGeometryPlanner.plan(descriptor(key));
            for (SkyIslandCaveExposureConnectionGeometry connection : plan.connections()) {
                steered |= connection.normalizedMouthOffset() > 0.005
                        || connection.normalizedMaxDeviation() > 0.005;
            }
        }
        assertTrue(steered);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
