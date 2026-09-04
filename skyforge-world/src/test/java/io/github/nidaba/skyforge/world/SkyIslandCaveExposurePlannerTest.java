package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandCaveExposurePlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void exposurePlanIsDeterministicAndSparse() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCaveExposurePlan first = SkyIslandCaveExposurePlanner.plan(descriptor);
            SkyIslandCaveExposurePlan second = SkyIslandCaveExposurePlanner.plan(descriptor);

            assertEquals(first, second);
            assertTrue(first.intents().size() <= first.geometry().systems().size());
            assertEquals(
                    first.geometry().systems().size(),
                    first.exposedSystemCount() + first.sealedSystemCount());
        }
    }

    @Test
    void caveFreeControlsRemainExposureFree() {
        for (long key : new long[] {2332L, 2211L}) {
            SkyIslandCaveExposurePlan plan = SkyIslandCaveExposurePlanner.plan(descriptor(key));
            assertTrue(plan.geometry().systems().isEmpty());
            assertTrue(plan.intents().isEmpty());
            assertEquals(0, plan.exposedSystemCount());
            assertEquals(0, plan.sealedSystemCount());
        }
    }

    @Test
    void everyIntentOriginatesOnExistingAuth0025Geometry() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveExposurePlan plan = SkyIslandCaveExposurePlanner.plan(descriptor(key));
            Map<Integer, SkyIslandCaveSystemGeometry> systems = new HashMap<>();
            for (SkyIslandCaveSystemGeometry system : plan.geometry().systems()) {
                systems.put(system.systemId(), system);
            }

            for (SkyIslandCaveExposureIntent intent : plan.intents()) {
                SkyIslandCaveSystemGeometry system = systems.get(intent.systemId());
                assertTrue(system != null);
                assertTrue(intent.score() >= 0.49 - 1.0e-12);
                assertTrue(intent.semanticGap() >= 0.0 && intent.semanticGap() <= 1.0);

                if (intent.sourcePrimitiveKind()
                        == SkyIslandCaveVolumeSample.PrimitiveKind.CHAMBER) {
                    SkyIslandCaveChamberGeometry chamber = system.chambers().stream()
                            .filter(candidate -> candidate.nodeId() == intent.sourcePrimitiveId())
                            .findFirst()
                            .orElseThrow();
                    assertEquals(chamber.center().surfacePosition(), intent.caveAnchor().surfacePosition());
                    double expected = intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE
                            ? chamber.center().depthFraction() - chamber.depthRadius()
                            : chamber.center().depthFraction() + chamber.depthRadius();
                    assertEquals(clamp01(expected), intent.caveAnchor().depthFraction(), 1.0e-12);
                } else {
                    assertEquals(
                            SkyIslandCaveVolumeSample.PrimitiveKind.PASSAGE,
                            intent.sourcePrimitiveKind());
                    SkyIslandCavePassageGeometry passage = system.passages().stream()
                            .filter(candidate -> candidate.linkId() == intent.sourcePrimitiveId())
                            .findFirst()
                            .orElseThrow();
                    assertTrue(passage.points().stream().anyMatch(point ->
                            point.position().surfacePosition().equals(intent.caveAnchor().surfacePosition())
                                    && Math.abs(
                                                    clamp01(intent.side()
                                                                    == SkyIslandCaveExposureSide.UPPER_SURFACE
                                                            ? point.position().depthFraction()
                                                                    - point.depthRadius()
                                                            : point.position().depthFraction()
                                                                    + point.depthRadius())
                                                            - intent.caveAnchor().depthFraction())
                                            <= 1.0e-12));
                }
            }
        }
    }

    @Test
    void boundaryAnchorsUseOnlySemanticExteriorBoundaries() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveExposurePlan plan = SkyIslandCaveExposurePlanner.plan(descriptor(key));
            for (SkyIslandCaveExposureIntent intent : plan.intents()) {
                assertEquals(
                        intent.caveAnchor().surfacePosition(),
                        intent.boundaryAnchor().surfacePosition());
                if (intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE) {
                    assertEquals(0.0, intent.boundaryAnchor().depthFraction(), 0.0);
                    assertEquals(intent.caveAnchor().depthFraction(), intent.semanticGap(), 1.0e-12);
                } else {
                    assertEquals(1.0, intent.boundaryAnchor().depthFraction(), 0.0);
                    assertEquals(
                            1.0 - intent.caveAnchor().depthFraction(),
                            intent.semanticGap(),
                            1.0e-12);
                }
            }
        }
    }

    @Test
    void acceptedIntentDoesNotMutateExistingCaveGeometry() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveGeometryPlan geometry = SkyIslandCaveGeometryPlanner.plan(descriptor);
        SkyIslandCaveExposurePlan exposure = SkyIslandCaveExposurePlanner.plan(descriptor);

        assertEquals(geometry, exposure.geometry());
        assertFalse(geometry.systems().isEmpty());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
