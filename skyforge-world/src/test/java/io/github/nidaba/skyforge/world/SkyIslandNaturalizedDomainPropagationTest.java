package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandNaturalizedDomainPropagationTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void currentSemanticOwnershipRejectsLegacyCircleOnlyPositions() {
        for (long key : new long[] {7L, 10L, 77L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);
            SkyIslandSemanticFieldSet current = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandSemanticFieldSet legacy = SkyIslandSemanticFieldSet.createLegacyCircular(descriptor);
            SkyIslandLocalPosition position = legacyOnlyPosition(descriptor, domain);

            assertEquals(0.0, current.interiority().sample(position), 1.0e-12);
            assertEquals(0.0, current.elevationTendency().sample(position), 1.0e-12);
            assertTrue(legacy.interiority().sample(position) > 0.0);

            SkyIslandHydrologySample hydrology = SkyIslandHydrologyField.create(descriptor).sample(position);
            assertEquals(0.0, hydrology.runoffPotential(), 1.0e-12);
            assertEquals(0.0, hydrology.retentionPotential(), 1.0e-12);
            assertEquals(0.0, hydrology.drainagePotential(), 1.0e-12);
            assertEquals(0.0, hydrology.outflowPotential(), 1.0e-12);
            assertEquals(
                    SkyIslandEcologyRegime.COLD_BARREN,
                    SkyIslandEcologyField.create(descriptor).sample(position).regime());
        }
    }

    @Test
    void watershedCellsBelongToCurrentNaturalizedDomain() {
        for (long key : new long[] {7L, 10L, 77L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

            assertTrue(watershed.cells().size() > 100);
            for (SkyIslandWatershedCell cell : watershed.cells()) {
                assertTrue(fields.interiority().sample(cell.position()) > 0.025 - 1.0e-12);
            }
        }
    }

    @Test
    void coherentTerrainFadesToZeroOutsideCurrentDomain() {
        for (long key : new long[] {7L, 10L, 512L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandNaturalizedDomainField domain = SkyIslandNaturalizedDomainField.create(descriptor);
            SkyIslandLocalPosition position = legacyOnlyPosition(descriptor, domain);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);

            assertEquals(0.0, coherent.continuousTerrain().sample(position), 1.0e-10);
        }
    }

    private static SkyIslandLocalPosition legacyOnlyPosition(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedDomainField domain) {
        double bestAngle = 0.0;
        double minimumBoundary = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 1440; i++) {
            double angle = 2.0 * Math.PI * i / 1440.0;
            double boundary = domain.boundaryRadius(angle);
            if (boundary < minimumBoundary) {
                minimumBoundary = boundary;
                bestAngle = angle;
            }
        }

        double radius = minimumBoundary
                + 0.45 * (descriptor.nominalRadius() - minimumBoundary);
        return new SkyIslandLocalPosition(
                radius * Math.cos(bestAngle),
                radius * Math.sin(bestAngle));
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
