package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandComponentFluvialTerrainCandidatePlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void ordinary77RealizesExactlyTheTwoQualifiedEdgeOutletComponents() {
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                        descriptor(8L, 81L, 77L));

        assertEquals(
                List.of(559, 1842),
                plan.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .sorted()
                        .toList());
        assertTrue(plan.deferredQualifiedComponents().isEmpty());

        assertEquals(
                List.of("709->559", "1742->1842"),
                plan.terrainField().acceptedReaches().stream()
                        .map(reach -> {
                            var semantic = reach.geomorphicRoute().semanticReach();
                            return semantic.startCellIndex() + "->" + semantic.endCellIndex();
                        })
                        .toList());
        assertEquals(2, plan.postRealizationQualifications().size());
        assertTrue(plan.postRealizationQualifications().stream()
                .allMatch(SkyIslandGeomorphicReachQualification::accepted));

        for (SkyIslandHydraulicReachGeometry reach :
                plan.terrainField().acceptedReaches()) {
            SkyIslandLocalPosition point =
                    reach.centerline().points().get(
                            reach.centerline().points().size() / 2);
            SkyIslandQualifiedFluvialSample sample =
                    plan.terrainField().sampleDetailed(point);
            assertTrue(sample.terrainDeltaPotential() <= EPSILON);
            assertTrue(sample.provenance().isPresent());
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            assertEquals(
                    semantic.startCellIndex(),
                    sample.provenance().orElseThrow().startCellIndex());
            assertEquals(
                    semantic.endCellIndex(),
                    sample.provenance().orElseThrow().endCellIndex());
        }
    }

    @Test
    void primary287ContributesExactlyZeroCandidateTerrain() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertTrue(plan.realizedComponents().isEmpty());
        assertTrue(plan.terrainField().acceptedReaches().isEmpty());
        assertTrue(plan.postRealizationQualifications().isEmpty());

        SkyIslandHydraulicReachSkeleton skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor)
                        .reaches()
                        .getFirst();
        SkyIslandLocalPosition point =
                skeleton.centerline().points().get(
                        skeleton.centerline().points().size() / 2);
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
    void lake609ReceivesNoCandidateTerrainAuthority() {
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                        descriptor(8L, 81L, 609L));

        assertTrue(plan.realizedComponents().isEmpty());
        assertTrue(plan.terrainField().acceptedReaches().isEmpty());
        assertTrue(plan.postRealizationQualifications().isEmpty());
        assertFalse(plan.assemblyPlan().terminalComponents().stream()
                .anyMatch(component ->
                        component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED));
    }

    @Test
    void independentCandidatePlansProduceIdenticalSelectionAndSamples() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan first =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandComponentFluvialTerrainCandidatePlan second =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertEquals(
                first.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .toList(),
                second.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .toList());
        assertEquals(
                first.postRealizationQualifications(),
                second.postRealizationQualifications());

        for (SkyIslandHydraulicReachGeometry reach :
                first.terrainField().acceptedReaches()) {
            SkyIslandLocalPosition probe =
                    reach.centerline().points().get(
                            reach.centerline().points().size() / 2);
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
