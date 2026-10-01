package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialFamilyPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void planIsDeterministicAndCoversExactlyTheActiveHostVolume() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandMaterialFamilyPlan first = SkyIslandMaterialFamilyPlanner.plan(descriptor);
        SkyIslandMaterialFamilyPlan second = SkyIslandMaterialFamilyPlanner.plan(descriptor);
        SkyIslandMaterialDomainPlan domains = SkyIslandMaterialDomainPlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(domains.activeHostCells(), first.activeHostCells());
        assertEquals(first.activeHostCells(), first.cells().size());
        assertFalse(first.cells().isEmpty());

        Set<Integer> indices = new HashSet<>();
        for (SkyIslandMaterialFamilyCell cell : first.cells()) {
            assertTrue(indices.add(cell.index()));
            assertTrue(cell.strongestHostFabric() > 0.0);
        }
    }

    @Test
    void everyFamilyCellRemainsRealAuth0031HostMaterial() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
            SkyIslandMaterialFamilyPlan plan =
                    SkyIslandMaterialFamilyPlanner.plan(descriptor);

            for (SkyIslandMaterialFamilyCell cell : plan.cells()) {
                SkyIslandSubsurfaceMaterialSample sample = material.sample(cell.position());
                assertTrue(sample.owned());
                assertTrue(sample.materialPresent());
            }
        }
    }

    @Test
    void conditionedFamiliesRemainGatedByTheirAuth0032Domains() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialDomainPlan domains = SkyIslandMaterialDomainPlanner.plan(descriptor);
            SkyIslandMaterialFamilyPlan families = SkyIslandMaterialFamilyPlanner.plan(descriptor);

            EnumMap<SkyIslandMaterialDomainKind, Set<Integer>> participating =
                    new EnumMap<>(SkyIslandMaterialDomainKind.class);
            for (SkyIslandMaterialDomainKind kind : SkyIslandMaterialDomainKind.values()) {
                participating.put(kind, new HashSet<>());
            }
            for (SkyIslandMaterialDomain domain : domains.domains()) {
                for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                    participating.get(domain.kind()).add(cell.index());
                }
            }

            for (SkyIslandMaterialFamilyCell cell : families.cells()) {
                if (cell.layeredFabricRichHost() > 0.0) {
                    assertTrue(participating
                            .get(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN)
                            .contains(cell.index()));
                }
                if (cell.stronglyAlteredHost() > 0.0) {
                    assertTrue(participating
                            .get(SkyIslandMaterialDomainKind.ALTERED_ZONE)
                            .contains(cell.index()));
                }
                if (cell.waterConditionedHost() > 0.0) {
                    assertTrue(participating
                            .get(SkyIslandMaterialDomainKind.SATURATED_BODY)
                            .contains(cell.index()));
                }
                if (cell.mineralBearingStructuralHost() > 0.0) {
                    assertTrue(participating
                            .get(SkyIslandMaterialDomainKind.MINERALIZED_BODY)
                            .contains(cell.index()));
                }
            }
        }
    }

    @Test
    void canonicalRepresentativesRetainDifferentSemanticMaterialCharacters() {
        SkyIslandMaterialFamilyPlan competent =
                SkyIslandMaterialFamilyPlanner.plan(descriptor(2332L));
        SkyIslandMaterialFamilyPlan weak =
                SkyIslandMaterialFamilyPlanner.plan(descriptor(653L));
        SkyIslandMaterialFamilyPlan permeable =
                SkyIslandMaterialFamilyPlanner.plan(descriptor(1051L));
        SkyIslandMaterialFamilyPlan hydrologic =
                SkyIslandMaterialFamilyPlanner.plan(descriptor(2211L));
        SkyIslandMaterialFamilyPlan eroded =
                SkyIslandMaterialFamilyPlanner.plan(descriptor(1439L));

        assertTrue(
                competent.cellCountAbove(
                                SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST, 0.0)
                        > weak.cellCountAbove(
                                SkyIslandMaterialFamilyKind.LAYERED_FABRIC_RICH_HOST, 0.0));
        assertTrue(
                eroded.cellCountAbove(
                                SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST, 0.0)
                        > competent.cellCountAbove(
                                SkyIslandMaterialFamilyKind.STRONGLY_ALTERED_HOST, 0.0));
        assertTrue(
                permeable.cellCountAbove(
                                SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST, 0.0)
                        > competent.cellCountAbove(
                                SkyIslandMaterialFamilyKind.WATER_CONDITIONED_HOST, 0.0));
        assertTrue(
                hydrologic.cellCountAbove(
                                SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST, 0.0)
                        > competent.cellCountAbove(
                                SkyIslandMaterialFamilyKind.MINERAL_BEARING_STRUCTURAL_HOST, 0.0));

        assertNotEquals(
                competent.meanMembership(SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST),
                weak.meanMembership(SkyIslandMaterialFamilyKind.COHERENT_MASSIVE_HOST));
    }

    @Test
    void materialFamiliesRemainComposableRatherThanExclusiveLabels() {
        boolean overlapObserved = false;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandMaterialFamilyPlan plan =
                    SkyIslandMaterialFamilyPlanner.plan(descriptor(key));
            for (SkyIslandMaterialFamilyCell cell : plan.cells()) {
                int expressed = 0;
                for (SkyIslandMaterialFamilyKind kind : SkyIslandMaterialFamilyKind.values()) {
                    if (cell.membership(kind) > 0.0) {
                        expressed++;
                    }
                }
                if (expressed >= 2) {
                    overlapObserved = true;
                    break;
                }
            }
        }
        assertTrue(overlapObserved);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
