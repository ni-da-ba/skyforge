package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandQualifiedFluvialRealizationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void rejectedPrimaryReachContributesExactlyZeroTerrainDelta() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);

        assertTrue(plan.acceptedQualifications().isEmpty());
        assertFalse(plan.rejectedQualifications().isEmpty());

        SkyIslandHydraulicReachGeometry rejected =
                plan.rejectedQualifications().getFirst().diagnostics().hydraulicReach();
        SkyIslandLocalPosition point =
                rejected.centerline().points().get(rejected.centerline().points().size() / 2);

        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandQualifiedFluvialSample sample =
                plan.terrainField().sampleDetailed(point);

        assertEquals(original.sample(point), sample.targetTerrainPotential(), EPSILON);
        assertEquals(0.0, sample.terrainDeltaPotential(), EPSILON);
        assertEquals(SkyIslandQualifiedFluvialZone.UNAFFECTED, sample.zone());
        assertTrue(sample.provenance().isEmpty());
    }

    @Test
    void acceptedControlProducesBoundedDownwardCrossSectionWithProvenance() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);

        assertFalse(plan.acceptedQualifications().isEmpty());
        SkyIslandHydraulicReachGeometry reach =
                plan.acceptedQualifications().getFirst().diagnostics().hydraulicReach();
        int midpoint = reach.centerline().points().size() / 2;
        SkyIslandLocalPosition point = reach.centerline().points().get(midpoint);
        SkyIslandQualifiedFluvialSample sample =
                plan.terrainField().sampleDetailed(point);

        assertTrue(sample.targetTerrainPotential() <= sample.originalTerrainPotential() + EPSILON);
        assertTrue(sample.terrainDeltaPotential() <= EPSILON);
        assertTrue(sample.zone() != SkyIslandQualifiedFluvialZone.UNAFFECTED);
        assertTrue(sample.provenance().isPresent());
        assertTrue(sample.waterSurfacePotential() >= sample.targetTerrainPotential() - EPSILON);
    }

    @Test
    void independentPlansProduceIdenticalSamples() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandQualifiedFluvialRealizationPlan first =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);
        SkyIslandQualifiedFluvialRealizationPlan second =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);

        assertEquals(
                first.acceptedQualifications().size(),
                second.acceptedQualifications().size());
        assertEquals(
                first.rejectedQualifications().size(),
                second.rejectedQualifications().size());

        List<SkyIslandLocalPosition> probes = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(12.5, -7.5),
                new SkyIslandLocalPosition(-25.0, 18.0));
        for (SkyIslandLocalPosition probe : probes) {
            assertEquals(
                    first.terrainField().sampleDetailed(probe),
                    second.terrainField().sampleDetailed(probe));
        }
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
