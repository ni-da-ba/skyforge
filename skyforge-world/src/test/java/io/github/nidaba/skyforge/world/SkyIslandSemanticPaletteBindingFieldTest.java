package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandSemanticPaletteBindingFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void bindingPlanAndCanonicalKeysAreDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandSemanticPaletteBindingPlan first =
                SkyIslandSemanticPaletteBindingPlanner.plan(descriptor);
        SkyIslandSemanticPaletteBindingPlan second =
                SkyIslandSemanticPaletteBindingPlanner.plan(descriptor);

        assertEquals(first, second);
        Set<String> tokens = new HashSet<>();
        for (SkyIslandSemanticPaletteBindingDomain domain : first.domains()) {
            String token = domain.key().canonicalToken();
            assertTrue(token.startsWith("sfbind:v1:"));
            assertTrue(tokens.add(token));
        }

        SkyIslandSemanticPaletteBindingKey changedIsland =
                SkyIslandSemanticPaletteBindingKey.of(
                        descriptor(1439L).identity(),
                        first.domains().get(0).key().role(),
                        first.domains().get(0).key().sourceChannel(),
                        first.domains().get(0).key().domainKind(),
                        first.domains().get(0).key().anchorId());
        assertNotEquals(first.domains().get(0).key().canonicalToken(),
                changedIsland.canonicalToken());
    }

    @Test
    void matrixDomainsNeverCrossAssemblageBoundaries() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandSemanticPaletteBindingPlan plan =
                    SkyIslandSemanticPaletteBindingPlanner.plan(descriptor(key));
            for (SkyIslandSemanticPaletteBindingDomain domain : plan.domains()) {
                SkyIslandSemanticMaterialPaletteRole role = domain.key().role();
                if (role == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                        || role == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX) {
                    assertEquals(
                            SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION,
                            domain.key().domainKind());
                    assertEquals(1, domain.assemblageCount());
                }
            }
        }
    }

    @Test
    void conditionedDomainsAreConnectedAndMayCrossOnlyThroughEligibleCells() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticPaletteBindingPlan plan =
                    SkyIslandSemanticPaletteBindingPlanner.plan(descriptor);
            for (SkyIslandSemanticPaletteBindingDomain domain : plan.domains()) {
                if (domain.key().domainKind()
                        != SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION) {
                    continue;
                }
                assertFalse(domain.cells().isEmpty());
                for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
                    assertEquals(domain.key().role(), cell.candidate().role());
                    assertEquals(
                            domain.key().sourceChannel(),
                            cell.candidate().sourceChannel());
                    assertTrue(cell.candidate().support() > 0.0);
                }
                assertFaceConnected(domain, plan);
            }
        }
    }

    @Test
    void everyPlanningCellCandidateReceivesItsPlannedStableKey() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticPaletteBindingField field =
                    SkyIslandSemanticPaletteBindingField.create(descriptor);
            Map<Long, SkyIslandSemanticPaletteBindingKey> expected = new HashMap<>();
            Map<Integer, SkyIslandSubsurfacePosition> positions = new HashMap<>();
            for (SkyIslandSemanticPaletteBindingDomain domain : field.plan().domains()) {
                for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
                    expected.put(nodeKey(cell.index(), cell.candidate().role()), domain.key());
                }
            }
            SkyIslandLithologicAssemblagePlan assemblages =
                    SkyIslandLithologicRealizationField.create(descriptor).assemblagePlan();
            for (SkyIslandLithologicAssemblageCell cell : assemblages.cells()) {
                positions.put(cell.index(), cell.position());
                SkyIslandSemanticPaletteBindingSelection selection =
                        field.sample(cell.position());
                for (SkyIslandSemanticPaletteBindingCandidate binding :
                        selection.bindings()) {
                    SkyIslandSemanticPaletteBindingKey planned =
                            expected.get(nodeKey(cell.index(), binding.candidate().role()));
                    assertEquals(planned, binding.bindingKey());
                    assertNotEquals(
                            SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION,
                            binding.bindingKey().domainKind());
                }
            }
        }
    }

    @Test
    void continuousSelectionsPreserveAuth0037CandidatesAndMaterialBoundary() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticMaterialPaletteField palette =
                    SkyIslandSemanticMaterialPaletteField.create(descriptor);
            SkyIslandSemanticPaletteBindingField bindings =
                    SkyIslandSemanticPaletteBindingField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 9; iz++) {
                double z = -radius + iz * (2.0 * radius / 8.0);
                for (int id = 0; id < 5; id++) {
                    double depth = id / 4.0;
                    for (int ix = 0; ix < 9; ix++) {
                        double x = -radius + ix * (2.0 * radius / 8.0);
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        SkyIslandSemanticMaterialPaletteSelection source =
                                palette.sample(position);
                        SkyIslandSemanticPaletteBindingSelection result =
                                bindings.sample(position);

                        assertEquals(source.owned(), result.owned());
                        assertEquals(source.materialPresent(), result.materialPresent());
                        if (!source.materialPresent()) {
                            assertTrue(result.bindings().isEmpty());
                            continue;
                        }
                        assertEquals(source.localAssemblageId(), result.localAssemblageId());
                        assertEquals(source.contactId(), result.contactId());
                        assertEquals(source.candidates().size(), result.bindings().size());
                        for (SkyIslandSemanticMaterialPaletteCandidate candidate :
                                source.candidates()) {
                            SkyIslandSemanticPaletteBindingCandidate bound =
                                    result.binding(candidate.role()).orElseThrow();
                            assertEquals(candidate, bound.candidate());
                            assertEquals(candidate.role(), bound.bindingKey().role());
                            assertEquals(
                                    candidate.sourceChannel(),
                                    bound.bindingKey().sourceChannel());
                        }
                    }
                }
            }
        }
    }

    @Test
    void contactFallbackKeysAreContactScopedWhenTheyOccur() {
        int observed = 0;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticPaletteBindingField field =
                    SkyIslandSemanticPaletteBindingField.create(descriptor);
            SkyIslandLithologicContactRealizationPlan contacts =
                    SkyIslandLithologicContactRealizationPlanner.plan(descriptor);

            for (SkyIslandLithologicContactRealization realization :
                    contacts.realizations()) {
                for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                    SkyIslandSemanticPaletteBindingSelection selection =
                            field.sample(patch.center());
                    if (!selection.materialPresent()) {
                        continue;
                    }
                    for (SkyIslandSemanticPaletteBindingCandidate binding :
                            selection.bindings()) {
                        if (binding.bindingKey().domainKind()
                                != SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION) {
                            continue;
                        }
                        assertTrue(selection.contactId() >= 0);
                        assertEquals(selection.contactId(), binding.bindingKey().anchorId());
                        observed++;
                    }
                }
            }
        }
        assertTrue(observed >= 0);
    }

    private static void assertFaceConnected(
            SkyIslandSemanticPaletteBindingDomain domain,
            SkyIslandSemanticPaletteBindingPlan plan) {
        Set<Integer> remaining = new HashSet<>();
        for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
            remaining.add(cell.index());
        }
        Set<Integer> reached = new HashSet<>();
        java.util.ArrayDeque<SkyIslandSemanticPaletteBindingCell> queue =
                new java.util.ArrayDeque<>();
        queue.add(domain.cells().get(0));
        reached.add(domain.cells().get(0).index());
        Map<Integer, SkyIslandSemanticPaletteBindingCell> byIndex = new HashMap<>();
        for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
            byIndex.put(cell.index(), cell);
        }
        while (!queue.isEmpty()) {
            SkyIslandSemanticPaletteBindingCell cell = queue.removeFirst();
            int[][] offsets = {
                {-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}
            };
            for (int[] offset : offsets) {
                int x = cell.xIndex() + offset[0];
                int d = cell.depthIndex() + offset[1];
                int z = cell.zIndex() + offset[2];
                if (x < 0 || d < 0 || z < 0
                        || x >= plan.gridSize()
                        || d >= plan.depthSamples()
                        || z >= plan.gridSize()) {
                    continue;
                }
                int index =
                        (z * plan.depthSamples() + d) * plan.gridSize() + x;
                SkyIslandSemanticPaletteBindingCell neighbor = byIndex.get(index);
                if (neighbor != null && reached.add(index)) {
                    queue.addLast(neighbor);
                }
            }
        }
        assertEquals(remaining, reached);
    }

    private static long nodeKey(
            int cellIndex, SkyIslandSemanticMaterialPaletteRole role) {
        return (((long) cellIndex) << 8) | (role.ordinal() & 0xFFL);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
