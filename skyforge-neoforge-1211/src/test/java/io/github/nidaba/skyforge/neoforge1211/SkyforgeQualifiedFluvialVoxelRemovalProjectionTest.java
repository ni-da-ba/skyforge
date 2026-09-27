package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SkyforgeQualifiedFluvialVoxelRemovalProjectionTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;

    @Test
    void ordinary77ProjectsExactlyTheAcceptedF4CRemovalCountWithoutOverlap() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        var candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        var association = productionAssociation(descriptor, 910_077L);
        var quantization =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        association, candidate);

        var removals =
                SkyforgeQualifiedFluvialVoxelRemovalProjection.plan(quantization);

        assertFalse(removals.isEmpty());
        assertEquals(
                quantization.totalRemovedSolidBlocks(),
                removals.stream()
                        .mapToInt(removal -> removal.positions().size())
                        .sum());

        Set<Long> positions = new HashSet<>();
        for (var removal : removals) {
            assertEquals(association.realizedVolumeId(), removal.volumeId());
            assertTrue(removal.terminalCellIndex() >= 0);
            assertFalse(removal.positions().isEmpty());
            for (BlockPos position : removal.positions()) {
                assertTrue(positions.add(position.asLong()));
            }
        }
        assertEquals(quantization.totalRemovedSolidBlocks(), positions.size());
    }

    @Test
    void projectedPositionsAreExactlyAboveEachQualifiedF4CTargetTop() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        var candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        var association = productionAssociation(descriptor, 920_077L);
        var quantization =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        association, candidate);
        var removals =
                SkyforgeQualifiedFluvialVoxelRemovalProjection.plan(quantization);

        Set<Long> projected = new HashSet<>();
        removals.forEach(removal ->
                removal.positions().forEach(position ->
                        projected.add(position.asLong())));

        for (var component : quantization.components()) {
            if (component.columns().isEmpty()) {
                continue;
            }
            for (var column : component.columns()) {
                for (int y = column.targetMaximumSolidY() + 1;
                        y <= column.originalSupport().maximumSolidY();
                        y++) {
                    assertTrue(projected.contains(
                            new BlockPos(column.worldX(), y, column.worldZ()).asLong()));
                }
                assertFalse(projected.contains(
                        new BlockPos(
                                        column.worldX(),
                                        column.targetMaximumSolidY(),
                                        column.worldZ())
                                .asLong()));
            }
        }
    }

    @Test
    void rejectedOrDeferredControlsProjectNoMinecraftRemovalPositions() {
        for (long key : List.of(287L, 632L, 609L)) {
            SkyIslandDescriptor descriptor = descriptor(8L, 81L, key);
            var candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
            var quantization =
                    SkyIslandFluvialVoxelQuantizationPlanner.plan(
                            productionAssociation(descriptor, 910_000L + key),
                            candidate);
            assertTrue(
                    SkyforgeQualifiedFluvialVoxelRemovalProjection.plan(quantization)
                            .isEmpty());
        }
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        1200.0,
                        -900.0,
                        256.0,
                        radius,
                        72.0,
                        104.0,
                        Math.min(32.0, radius),
                        0.43,
                        0.62,
                        0.57,
                        0.18,
                        descriptor.morphologyFamily(),
                        0.22,
                        38.0,
                        0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "f4d-test",
                                0,
                                0,
                                geometrySeed),
                        new WorldBounds(
                                1200.0 - radius,
                                1200.0 + radius,
                                64.0,
                                448.0,
                                -900.0 - radius,
                                -900.0 + radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
