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

class SkyIslandLithologicContactRealizationFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void realizationPlanIsDeterministicAndPreservesEveryAuth0034Contact() {
        SkyIslandDescriptor descriptor = descriptor(2211L);
        SkyIslandLithologicContactRealizationPlan first =
                SkyIslandLithologicContactRealizationPlanner.plan(descriptor);
        SkyIslandLithologicContactRealizationPlan second =
                SkyIslandLithologicContactRealizationPlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(
                first.assemblagePlan().contacts().size(),
                first.realizations().size());
        assertTrue(first.patchCount() > 0);
        assertTrue(first.minimumHalfWidth()
                >= SkyIslandLithologicContactRealizationPlanner.MIN_HALF_WIDTH);
        assertTrue(first.maximumHalfWidth()
                <= SkyIslandLithologicContactRealizationPlanner.MAX_HALF_WIDTH);

        Set<Integer> contactIds = new HashSet<>();
        for (SkyIslandLithologicContact contact : first.assemblagePlan().contacts()) {
            contactIds.add(contact.contactId());
        }
        Set<Integer> realizedIds = new HashSet<>();
        for (SkyIslandLithologicContactRealization realization : first.realizations()) {
            realizedIds.add(realization.contact().contactId());
            assertFalse(realization.patches().isEmpty());
        }
        assertEquals(contactIds, realizedIds);
    }

    @Test
    void everyPatchCorrespondsToARealAuth0034FaceAdjacency() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicContactRealizationPlan plan =
                    SkyIslandLithologicContactRealizationPlanner.plan(descriptor(key));
            Set<String> adjacency = adjacencyKeys(plan.assemblagePlan());

            for (SkyIslandLithologicContactRealization realization : plan.realizations()) {
                for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                    String keyValue = patchKey(patch, plan);
                    assertTrue(adjacency.contains(keyValue), keyValue);
                }
            }
        }
    }

    @Test
    void continuousFieldNeverCreatesContactMaterialOutsideHostOrInsideAuthoredVoid() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
            SkyIslandLithologicContactRealizationField field =
                    SkyIslandLithologicContactRealizationField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < 17; iz++) {
                double z = -radius + iz * (2.0 * radius / 16.0);
                for (int id = 0; id < 9; id++) {
                    double depth = id / 8.0;
                    for (int ix = 0; ix < 17; ix++) {
                        double x = -radius + ix * (2.0 * radius / 16.0);
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        SkyIslandSubsurfaceMaterialSample host = material.sample(position);
                        SkyIslandLithologicContactRealizationSample sample =
                                field.sample(position);
                        assertEquals(host.owned(), sample.owned());
                        assertEquals(host.materialPresent(), sample.materialPresent());
                        if (!host.materialPresent()) {
                            assertEquals(0.0, sample.contactInfluence());
                            assertEquals(-1, sample.contactId());
                        }
                    }
                }
            }
        }
    }

    @Test
    void materialPresentPatchCentersProduceStrongContinuousInfluence() {
        boolean observed = false;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
            SkyIslandLithologicContactRealizationField field =
                    SkyIslandLithologicContactRealizationField.create(descriptor);

            for (SkyIslandLithologicContactRealization realization :
                    field.plan().realizations()) {
                for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                    if (!material.sample(patch.center()).materialPresent()) {
                        continue;
                    }
                    SkyIslandLithologicContactRealizationSample sample =
                            field.sample(patch.center());
                    assertTrue(sample.contactInfluence() > 0.45);
                    assertTrue(sample.firstAssemblageWeight() > 0.40);
                    assertTrue(sample.secondAssemblageWeight() > 0.40);
                    observed = true;
                    break;
                }
                if (observed) {
                    break;
                }
            }
            if (observed) {
                break;
            }
        }
        assertTrue(observed);
    }

    @Test
    void widthAndSharpnessRespondToSemanticAndLocalCauses() {
        Set<Long> widthBuckets = new HashSet<>();
        Set<Long> sharpnessBuckets = new HashSet<>();
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicContactRealizationPlan plan =
                    SkyIslandLithologicContactRealizationPlanner.plan(descriptor(key));
            for (SkyIslandLithologicContactRealization realization : plan.realizations()) {
                for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                    widthBuckets.add(Math.round(patch.normalizedHalfWidth() * 1000.0));
                    sharpnessBuckets.add(Math.round(patch.transitionSharpness() * 1000.0));
                }
            }
        }
        assertTrue(widthBuckets.size() > 4);
        assertTrue(sharpnessBuckets.size() > 4);
    }

    @Test
    void canonicalRepresentativesDoNotCollapseToOneContactRealizationSignature() {
        Map<Long, String> signatures = new HashMap<>();
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandLithologicContactRealizationPlan plan =
                    SkyIslandLithologicContactRealizationPlanner.plan(descriptor(key));
            signatures.put(
                    key,
                    plan.patchCount()
                            + ":"
                            + Math.round(plan.meanHalfWidth() * 10000.0)
                            + ":"
                            + Math.round(plan.maximumHalfWidth() * 10000.0));
        }
        assertTrue(new HashSet<>(signatures.values()).size() > 1);
        assertNotEquals(signatures.get(2332L), signatures.get(653L));
    }

    private static Set<String> adjacencyKeys(
            SkyIslandLithologicAssemblagePlan plan) {
        Map<Integer, SkyIslandLithologicAssemblageCell> cells = new HashMap<>();
        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            cells.put(cell.index(), cell);
        }
        Set<String> result = new HashSet<>();
        int grid = plan.gridSize();
        int depthSamples = plan.depthSamples();

        for (SkyIslandLithologicAssemblageCell cell : plan.cells()) {
            int[][] offsets = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int[] offset : offsets) {
                int x = cell.xIndex() + offset[0];
                int d = cell.depthIndex() + offset[1];
                int z = cell.zIndex() + offset[2];
                if (x >= grid || d >= depthSamples || z >= grid) {
                    continue;
                }
                int index = (z * depthSamples + d) * grid + x;
                SkyIslandLithologicAssemblageCell neighbor = cells.get(index);
                if (neighbor == null || neighbor.assemblageId() == cell.assemblageId()) {
                    continue;
                }
                SkyIslandLithologicContactAxis axis = offset[0] != 0
                        ? SkyIslandLithologicContactAxis.X
                        : offset[1] != 0
                                ? SkyIslandLithologicContactAxis.DEPTH
                                : SkyIslandLithologicContactAxis.Z;
                result.add(
                        cell.assemblageId()
                                + ":"
                                + neighbor.assemblageId()
                                + ":"
                                + axis
                                + ":"
                                + round(0.5 * (cell.position().x() + neighbor.position().x()))
                                + ":"
                                + round(0.5 * (cell.position().z() + neighbor.position().z()))
                                + ":"
                                + round(0.5
                                        * (cell.position().depthFraction()
                                                + neighbor.position().depthFraction())));
            }
        }
        return result;
    }

    private static String patchKey(
            SkyIslandLithologicContactPatch patch,
            SkyIslandLithologicContactRealizationPlan plan) {
        int negative = patch.firstAssemblageOnNegativeSide()
                ? patch.firstAssemblageId()
                : patch.secondAssemblageId();
        int positive = patch.firstAssemblageOnNegativeSide()
                ? patch.secondAssemblageId()
                : patch.firstAssemblageId();
        return negative
                + ":"
                + positive
                + ":"
                + patch.axis()
                + ":"
                + round(patch.center().x())
                + ":"
                + round(patch.center().z())
                + ":"
                + round(patch.center().depthFraction());
    }

    private static long round(double value) {
        return Math.round(value * 1_000_000.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
