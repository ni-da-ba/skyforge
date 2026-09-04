package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandLithologicAssemblagePlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void planIsDeterministicAndCoversActiveHostExactlyOnce() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandLithologicAssemblagePlan first =
                SkyIslandLithologicAssemblagePlanner.plan(descriptor);
        SkyIslandLithologicAssemblagePlan second =
                SkyIslandLithologicAssemblagePlanner.plan(descriptor);
        SkyIslandMaterialFamilyPlan families =
                SkyIslandMaterialFamilyPlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(families.activeHostCells(), first.activeHostCells());
        assertEquals(first.activeHostCells(), first.cells().size());
        assertFalse(first.assemblages().isEmpty());

        Set<Integer> seen = new HashSet<>();
        for (SkyIslandLithologicAssemblageCell cell : first.cells()) {
            assertTrue(seen.add(cell.index()));
        }
    }

    @Test
    void everyAssemblageIsFaceConnectedAndRetainsAuth0033State() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialFamilyPlan families =
                    SkyIslandMaterialFamilyPlanner.plan(descriptor);
            Map<Integer, SkyIslandMaterialFamilyCell> familyByIndex = new HashMap<>();
            for (SkyIslandMaterialFamilyCell cell : families.cells()) {
                familyByIndex.put(cell.index(), cell);
            }

            SkyIslandLithologicAssemblagePlan plan =
                    SkyIslandLithologicAssemblagePlanner.plan(descriptor);
            for (SkyIslandLithologicAssemblage assemblage : plan.assemblages()) {
                assertConnected(assemblage);
                for (SkyIslandLithologicAssemblageCell cell : assemblage.cells()) {
                    assertEquals(familyByIndex.get(cell.index()), cell.familyCharacter());
                    assertTrue(
                            SkyIslandLithologicAssemblagePlanner.semanticSupport(
                                            cell.familyCharacter(), assemblage.kind())
                                    > 0.0);
                }
            }
        }
    }

    @Test
    void specializedAssemblagesRemainGroundedInTheirAuth0033Families() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicAssemblagePlan plan =
                    SkyIslandLithologicAssemblagePlanner.plan(descriptor(key));
            for (SkyIslandLithologicAssemblage assemblage : plan.assemblages()) {
                for (SkyIslandLithologicAssemblageCell cell : assemblage.cells()) {
                    switch (assemblage.kind()) {
                        case MASSIVE_HOST_UNIT ->
                                assertTrue(cell.familyCharacter().coherentMassiveHost() > 0.0);
                        case FABRIC_RICH_HOST_UNIT ->
                                assertTrue(cell.familyCharacter().layeredFabricRichHost() >= 0.48);
                        case ALTERED_HOST_UNIT ->
                                assertTrue(cell.familyCharacter().stronglyAlteredHost() >= 0.50);
                        case WATER_CONDITIONED_HOST_UNIT ->
                                assertTrue(cell.familyCharacter().waterConditionedHost() >= 0.50);
                        case MINERAL_BEARING_STRUCTURAL_UNIT ->
                                assertTrue(
                                        cell.familyCharacter().mineralBearingStructuralHost()
                                                >= 0.48);
                    }
                }
            }
        }
    }

    @Test
    void contactsAreUniqueOrderedAndReferenceActualAdjacentAssemblages() {
        boolean contactObserved = false;
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicAssemblagePlan plan =
                    SkyIslandLithologicAssemblagePlanner.plan(descriptor(key));
            Set<Long> adjacentPairs = adjacentAssemblagePairs(plan);
            Set<Long> contactPairs = new HashSet<>();

            for (SkyIslandLithologicContact contact : plan.contacts()) {
                assertTrue(contact.firstAssemblageId() < contact.secondAssemblageId());
                long pair = pair(contact.firstAssemblageId(), contact.secondAssemblageId());
                assertTrue(contactPairs.add(pair));
                assertTrue(adjacentPairs.contains(pair));
                assertTrue(contact.faceCount() > 0);
                contactObserved = true;
            }
            assertEquals(adjacentPairs, contactPairs);
        }
        assertTrue(contactObserved);
    }

    @Test
    void canonicalCorpusDoesNotCollapseToOneAssemblageSignature() {
        Set<String> signatures = new HashSet<>();
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicAssemblagePlan plan =
                    SkyIslandLithologicAssemblagePlanner.plan(descriptor(key));
            StringBuilder signature = new StringBuilder();
            for (SkyIslandLithologicAssemblageKind kind :
                    SkyIslandLithologicAssemblageKind.values()) {
                signature.append(kind).append('=')
                        .append(plan.assemblageCount(kind)).append(';');
            }
            signatures.add(signature.toString());
        }
        assertTrue(signatures.size() > 1);
    }

    private static Set<Long> adjacentAssemblagePairs(
            SkyIslandLithologicAssemblagePlan plan) {
        Map<Integer, SkyIslandLithologicAssemblageCell> byIndex = new HashMap<>();
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            byIndex.put(cell.index(), cell);
        }

        Set<Long> result = new HashSet<>();
        int grid = plan.gridSize();
        int depthSamples = plan.depthSamples();
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            int[][] offsets = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int[] offset : offsets) {
                int x = cell.xIndex() + offset[0];
                int d = cell.depthIndex() + offset[1];
                int z = cell.zIndex() + offset[2];
                if (x < 0 || d < 0 || z < 0 || x >= grid || d >= depthSamples || z >= grid) {
                    continue;
                }
                int index = (z * depthSamples + d) * grid + x;
                SkyIslandLithologicAssemblageCell neighbor = byIndex.get(index);
                if (neighbor != null && neighbor.assemblageId() != cell.assemblageId()) {
                    result.add(pair(cell.assemblageId(), neighbor.assemblageId()));
                }
            }
        }
        return result;
    }

    private static long pair(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private static void assertConnected(SkyIslandLithologicAssemblage assemblage) {
        Map<Long, SkyIslandLithologicAssemblageCell> byCoordinate = new HashMap<>();
        for (SkyIslandLithologicAssemblageCell cell : assemblage.cells()) {
            byCoordinate.put(coordinateKey(
                    cell.xIndex(), cell.depthIndex(), cell.zIndex()), cell);
        }

        Set<Integer> visited = new HashSet<>();
        ArrayDeque<SkyIslandLithologicAssemblageCell> queue = new ArrayDeque<>();
        queue.add(assemblage.cells().getFirst());

        int[][] offsets = {
            {-1, 0, 0}, {1, 0, 0},
            {0, -1, 0}, {0, 1, 0},
            {0, 0, -1}, {0, 0, 1}
        };
        while (!queue.isEmpty()) {
            SkyIslandLithologicAssemblageCell cell = queue.removeFirst();
            if (!visited.add(cell.index())) {
                continue;
            }
            for (int[] offset : offsets) {
                SkyIslandLithologicAssemblageCell neighbor = byCoordinate.get(coordinateKey(
                        cell.xIndex() + offset[0],
                        cell.depthIndex() + offset[1],
                        cell.zIndex() + offset[2]));
                if (neighbor != null && !visited.contains(neighbor.index())) {
                    queue.addLast(neighbor);
                }
            }
        }
        assertEquals(byCoordinate.size(), visited.size());
    }

    private static long coordinateKey(int x, int depth, int z) {
        return ((long) z << 32) ^ ((long) depth << 16) ^ (x & 0xFFFFL);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
