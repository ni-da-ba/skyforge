package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousHydrologicTerrainFieldTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void acceptedCoarseAnchorsAreReproducedExactly() {
        for (long key : new long[] {77L, 83L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydrologicTerrainSurfacePlan coarse =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
            SkyIslandContinuousHydrologicTerrainField continuous =
                    SkyIslandContinuousHydrologicTerrainField.create(descriptor);

            assertEquals(coarse.gridSize(), continuous.gridSize());
            assertEquals(coarse.spacing(), continuous.spacing());
            for (SkyIslandHydrologicTerrainSurfaceCell cell : coarse.cells()) {
                assertEquals(
                        cell.adjustedElevationPotential(),
                        continuous.sample(cell.position()),
                        EPSILON);
                assertEquals(cell.netAdjustment(), continuous.adjustment(cell.position()), EPSILON);
            }
        }
    }

    @Test
    void denseSamplesRemainNormalizedAndCannotOvershootCoarseAdjustmentExtrema() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydrologicTerrainSurfacePlan coarse =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
            SkyIslandContinuousHydrologicTerrainField continuous =
                    SkyIslandContinuousHydrologicTerrainField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int z = 0; z < 97; z++) {
                for (int x = 0; x < 97; x++) {
                    SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                            -radius + 2.0 * radius * x / 96.0,
                            -radius + 2.0 * radius * z / 96.0);
                    double elevation = continuous.sample(position);
                    double adjustment = continuous.adjustment(position);
                    assertTrue(Double.isFinite(elevation));
                    assertTrue(elevation >= 0.0 && elevation <= 1.0);
                    assertTrue(adjustment >= -coarse.maxLowering() - EPSILON);
                    assertTrue(adjustment <= coarse.maxRaising() + EPSILON);
                }
            }
        }
    }

    @Test
    void fieldLeavesPositionsOutsideIslandExtentUntouched() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandContinuousHydrologicTerrainField continuous =
                SkyIslandContinuousHydrologicTerrainField.create(descriptor);
        double radius = descriptor.nominalRadius();
        for (SkyIslandLocalPosition position : new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(radius * 1.10, 0.0),
            new SkyIslandLocalPosition(-radius * 1.10, 0.0),
            new SkyIslandLocalPosition(0.0, radius * 1.10),
            new SkyIslandLocalPosition(0.0, -radius * 1.10)
        }) {
            assertEquals(0.0, continuous.adjustment(position), EPSILON);
            assertEquals(continuous.baseElevation(position), continuous.sample(position), EPSILON);
        }
    }

    @Test
    void interpolationIsContinuousAcrossAnAcceptedAnchor() {
        SkyIslandDescriptor descriptor = descriptor(512L);
        SkyIslandHydrologicTerrainSurfacePlan coarse =
                SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);
        SkyIslandContinuousHydrologicTerrainField continuous =
                SkyIslandContinuousHydrologicTerrainField.create(descriptor);

        SkyIslandHydrologicTerrainSurfaceCell changed = coarse.cells().stream()
                .filter(SkyIslandHydrologicTerrainSurfaceCell::changed)
                .findFirst()
                .orElseThrow();
        double epsilonDistance = coarse.spacing() * 1.0e-4;
        SkyIslandLocalPosition left = new SkyIslandLocalPosition(
                changed.position().x() - epsilonDistance, changed.position().z());
        SkyIslandLocalPosition right = new SkyIslandLocalPosition(
                changed.position().x() + epsilonDistance, changed.position().z());

        double anchor = continuous.adjustment(changed.position());
        assertTrue(Math.abs(continuous.adjustment(left) - anchor) < 1.0e-7);
        assertTrue(Math.abs(continuous.adjustment(right) - anchor) < 1.0e-7);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
