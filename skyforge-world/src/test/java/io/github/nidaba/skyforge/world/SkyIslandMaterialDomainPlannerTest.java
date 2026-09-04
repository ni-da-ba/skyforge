package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandMaterialDomainPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void materialDomainPlanIsDeterministicAndUsesStableGrid() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialDomainPlan first =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);
            SkyIslandMaterialDomainPlan second =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);

            assertEquals(first, second);
            assertEquals(SkyIslandMaterialDomainPlanner.GRID_SIZE, first.gridSize());
            assertEquals(SkyIslandMaterialDomainPlanner.DEPTH_SAMPLES, first.depthSamples());
            assertTrue(first.mineralCarrierCount() >= 1 && first.mineralCarrierCount() <= 3);
            assertTrue(first.fabricCarrierCount() >= 1 && first.fabricCarrierCount() <= 3);
        }
    }

    @Test
    void everyDomainCellIsOwnedHostMaterialAndNeverAuthoredCaveVoid() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);

            for (SkyIslandMaterialDomain domain : plan.domains()) {
                assertTrue(domain.cellCount()
                        >= SkyIslandMaterialDomainPlanner.MIN_DOMAIN_CELLS);
                for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                    SkyIslandSubsurfaceMaterialSample sample =
                            material.sample(cell.position());
                    assertTrue(sample.owned());
                    assertTrue(sample.materialPresent());
                    assertTrue(cell.membership() > 0.0 && cell.membership() <= 1.0);
                }
            }
        }
    }

    @Test
    void connectedDomainsAreFaceConnectedOnPlanningLattice() {
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor(key));

            for (SkyIslandMaterialDomain domain : plan.domains()) {
                Set<Integer> cells = new HashSet<>();
                for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                    cells.add(cell.index());
                }

                Set<Integer> reached = new HashSet<>();
                Set<Integer> frontier = new HashSet<>();
                frontier.add(domain.cells().getFirst().index());
                while (!frontier.isEmpty()) {
                    int current = frontier.iterator().next();
                    frontier.remove(current);
                    if (!reached.add(current)) {
                        continue;
                    }
                    int ix = current % plan.gridSize();
                    int id = (current / plan.gridSize()) % plan.depthSamples();
                    int iz = current / (plan.gridSize() * plan.depthSamples());
                    for (int[] offset : new int[][] {
                        {-1, 0, 0}, {1, 0, 0},
                        {0, -1, 0}, {0, 1, 0},
                        {0, 0, -1}, {0, 0, 1}
                    }) {
                        int nx = ix + offset[0];
                        int nd = id + offset[1];
                        int nz = iz + offset[2];
                        if (nx < 0 || nd < 0 || nz < 0
                                || nx >= plan.gridSize()
                                || nd >= plan.depthSamples()
                                || nz >= plan.gridSize()) {
                            continue;
                        }
                        int neighbor =
                                (nz * plan.depthSamples() + nd) * plan.gridSize() + nx;
                        if (cells.contains(neighbor) && !reached.contains(neighbor)) {
                            frontier.add(neighbor);
                        }
                    }
                }
                assertEquals(cells.size(), reached.size());
            }
        }
    }

    @Test
    void mineralizedDomainsRequireNontrivialContinuousMineralizationSupport() {
        boolean observed = false;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);
            SkyIslandSubsurfaceMaterialFieldSet material =
                    SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);

            for (SkyIslandMaterialDomain domain : plan.domains()) {
                if (domain.kind() != SkyIslandMaterialDomainKind.MINERALIZED_BODY) {
                    continue;
                }
                observed = true;
                for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                    SkyIslandSubsurfaceMaterialSample sample =
                            material.sample(cell.position());
                    assertTrue(sample.mineralizationTendency() > 0.20);
                }
            }
        }
        assertTrue(observed);
    }

    @Test
    void domainsDoNotOverwhelmEntireHostMaterialVolume() {
        for (long key : new long[] {2332L, 653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor);
            int active = activePlanningCells(descriptor);

            for (SkyIslandMaterialDomainKind kind : SkyIslandMaterialDomainKind.values()) {
                assertTrue(plan.cellCount(kind) < active);
            }
        }
    }

    @Test
    void materialDomainFamiliesRespondToDifferentCommonCausesAcrossCorpus() {
        SkyIslandMaterialDomainPlan competent =
                SkyIslandMaterialDomainPlanner.plan(descriptor(2332L));
        SkyIslandMaterialDomainPlan weak =
                SkyIslandMaterialDomainPlanner.plan(descriptor(653L));
        SkyIslandMaterialDomainPlan permeable =
                SkyIslandMaterialDomainPlanner.plan(descriptor(1051L));
        SkyIslandMaterialDomainPlan eroded =
                SkyIslandMaterialDomainPlanner.plan(descriptor(1439L));

        assertNotEquals(
                competent.cellCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN),
                weak.cellCount(SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN));
        assertNotEquals(
                permeable.cellCount(SkyIslandMaterialDomainKind.SATURATED_BODY),
                competent.cellCount(SkyIslandMaterialDomainKind.SATURATED_BODY));
        assertNotEquals(
                eroded.cellCount(SkyIslandMaterialDomainKind.ALTERED_ZONE),
                competent.cellCount(SkyIslandMaterialDomainKind.ALTERED_ZONE));
    }

    @Test
    void domainKindsMayOverlapWithoutBecomingExclusiveMaterialLabels() {
        boolean overlapObserved = false;
        for (long key : new long[] {653L, 1051L, 2211L, 1439L, 3670L}) {
            SkyIslandMaterialDomainPlan plan =
                    SkyIslandMaterialDomainPlanner.plan(descriptor(key));
            Set<Integer> seen = new HashSet<>();
            for (SkyIslandMaterialDomain domain : plan.domains()) {
                for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                    if (!seen.add(cell.index())) {
                        overlapObserved = true;
                    }
                }
            }
        }
        assertTrue(overlapObserved);
    }

    private static int activePlanningCells(SkyIslandDescriptor descriptor) {
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double horizontalSpacing =
                2.0 * radius / (SkyIslandMaterialDomainPlanner.GRID_SIZE - 1.0);
        double depthSpacing =
                1.0 / (SkyIslandMaterialDomainPlanner.DEPTH_SAMPLES - 1.0);
        int active = 0;

        for (int iz = 0; iz < SkyIslandMaterialDomainPlanner.GRID_SIZE; iz++) {
            double z = -radius + iz * horizontalSpacing;
            for (int id = 0; id < SkyIslandMaterialDomainPlanner.DEPTH_SAMPLES; id++) {
                double depth = id * depthSpacing;
                for (int ix = 0; ix < SkyIslandMaterialDomainPlanner.GRID_SIZE; ix++) {
                    double x = -radius + ix * horizontalSpacing;
                    if (material.sample(new SkyIslandSubsurfacePosition(x, z, depth))
                            .materialPresent()) {
                        active++;
                    }
                }
            }
        }
        return active;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
