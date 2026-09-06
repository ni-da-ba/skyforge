package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211ChunkAdapterTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final int MINIMUM_Y = 176;
    private static final int HEIGHT = 248;

    @Test
    void adjacentChunksCrossingAnIslandSeamAreOrderIndependentAndContinuous() {
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        ChunkPos westPos = new ChunkPos(-1, 0);
        ChunkPos eastPos = new ChunkPos(0, 0);

        MinecraftChunkMaterialization westFirst = adapter.materialize(westPos, MINIMUM_Y, HEIGHT);
        MinecraftChunkMaterialization eastSecond = adapter.materialize(eastPos, MINIMUM_Y, HEIGHT);

        MinecraftChunkMaterialization eastFirst = adapter.materialize(eastPos, MINIMUM_Y, HEIGHT);
        MinecraftChunkMaterialization westSecond = adapter.materialize(westPos, MINIMUM_Y, HEIGHT);

        assertArrayEquals(westFirst.blockKeys(), westSecond.blockKeys());
        assertArrayEquals(eastSecond.blockKeys(), eastFirst.blockKeys());
        assertEquals(1, westFirst.candidateVolumeReferences());
        assertEquals(1, eastSecond.candidateVolumeReferences());
        assertTrue(westFirst.solidBlockCount() > 0);
        assertTrue(eastSecond.solidBlockCount() > 0);

        boolean continuousAcrossBoundary = false;
        for (int y = MINIMUM_Y; y < MINIMUM_Y + HEIGHT && !continuousAcrossBoundary; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                boolean westSolid = !SkyforgeMinecraftBlockPalette.AIR.equals(
                        westFirst.blockKeyAt(15, y, localZ));
                boolean eastSolid = !SkyforgeMinecraftBlockPalette.AIR.equals(
                        eastSecond.blockKeyAt(0, y, localZ));
                if (westSolid && eastSolid) {
                    continuousAcrossBoundary = true;
                    break;
                }
            }
        }
        assertTrue(continuousAcrossBoundary, "centered island should remain solid across x=-1/0 chunk seam");

        assertTrue(contains(westFirst, SkyforgeMinecraftBlockPalette.DIRT)
                || contains(eastSecond, SkyforgeMinecraftBlockPalette.DIRT));
        assertTrue(contains(westFirst, SkyforgeMinecraftBlockPalette.DEEPSLATE)
                || contains(eastSecond, SkyforgeMinecraftBlockPalette.DEEPSLATE));
    }

    @Test
    void emptyDistantChunkAvoidsIslandEvaluationAndRemainsAir() {
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        ChunkPos distantPos = new ChunkPos(100, 100);
        assertFalse(adapter.hasCandidateVolume(distantPos, MINIMUM_Y, 32));
        assertTrue(adapter.hasCandidateVolume(new ChunkPos(0, 0), MINIMUM_Y, HEIGHT));

        MinecraftChunkMaterialization distant =
                adapter.materialize(distantPos, MINIMUM_Y, 32);

        assertEquals(0, distant.candidateVolumeReferences());
        assertEquals(0, distant.solidBlockCount());
    }

    @Test
    void exactOwnershipQueriesPreserveKnownVolumeAndSingleVolumeForeignIsolation() {
        SkyIslandWorldCatalog catalog = catalog();
        SkyIslandWorldVolumeId volumeId = catalog.volumes().getFirst().id();
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        assertTrue(adapter.isSolidOwnedBy(volumeId, 0, 320, 0));
        assertFalse(adapter.isSolidOwnedByOtherVolume(volumeId, 0, 320, 0));

        SkyIslandWorldVolumeId unknown = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "unknown",
                0,
                0,
                ROOT_SEED ^ 0x554e4b4e4f574eL);
        assertFalse(adapter.isSolidOwnedBy(unknown, 0, 320, 0));
    }

    @Test
    void exactVolumeFirstFreeHeightMatchesHistoricalFullSpanOwnershipScan() {
        SkyIslandWorldCatalog catalog = catalog();
        SkyIslandWorldVolumeId volumeId = catalog.volumes().getFirst().id();
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        assertEquals(
                manualFirstFreeHeight(adapter, volumeId, 0, 0, -64, 640),
                adapter.firstFreeHeight(volumeId, 0, 0, -64, 640));
        assertEquals(
                manualFirstFreeHeight(adapter, volumeId, 40, -24, 250, 120),
                adapter.firstFreeHeight(volumeId, 40, -24, 250, 120));
        assertEquals(
                manualFirstFreeHeight(adapter, volumeId, 255, 255, -64, 640),
                adapter.firstFreeHeight(volumeId, 255, 255, -64, 640));
    }

    @Test
    void exactVolumeFirstFreeHeightReturnsEmptyForDisjointRequestedSpan() {
        SkyIslandWorldCatalog catalog = catalog();
        SkyIslandWorldVolumeId volumeId = catalog.volumes().getFirst().id();
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        assertEquals(
                OptionalInt.empty(),
                adapter.firstFreeHeight(volumeId, 0, 0, -64, 64));
        assertEquals(
                OptionalInt.empty(),
                adapter.firstFreeHeight(volumeId, 0, 0, 600, 32));
    }

    @Test
    void exactVolumeFirstFreeHeightStillRejectsUnknownVolume() {
        SkyIslandWorldCatalog catalog = catalog();
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        SkyIslandWorldVolumeId unknown = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "unknown-height-query",
                0,
                0,
                ROOT_SEED ^ 0x484549474854L);

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.firstFreeHeight(unknown, 0, 0, -64, 640));
    }

    @Test
    void exactVolumeRematerializationExcludesOtherStackedVolumeAtSameXZ() {
        long lowerSeed = ROOT_SEED ^ 0x4c4f574552L;
        long upperSeed = ROOT_SEED ^ 0x5550504552L;
        SkyIslandWorldVolumeId lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "stacked", 0, 0, lowerSeed);
        SkyIslandWorldVolumeId upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "stacked", 0, 1, upperSeed);
        SkyIslandWorldCatalog stacked = new SkyIslandWorldCatalog(
                ROOT_SEED,
                List.of(
                        new SkyIslandWorldVolume(
                                lowerId,
                                new WorldBounds(-72.0, 72.0, 196.0, 288.0, -72.0, 72.0),
                                compiledTableland(lowerSeed, 236.0)),
                        new SkyIslandWorldVolume(
                                upperId,
                                new WorldBounds(-72.0, 72.0, 316.0, 408.0, -72.0, 72.0),
                                compiledTableland(upperSeed, 356.0))));
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                stacked,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        ChunkPos chunkPos = new ChunkPos(0, 0);

        MinecraftChunkMaterialization composite = adapter.materialize(chunkPos, 176, 256);
        MinecraftChunkMaterialization lower = adapter.materialize(lowerId, chunkPos, 176, 256);
        MinecraftChunkMaterialization upper = adapter.materialize(upperId, chunkPos, 176, 256);

        assertEquals(2, composite.candidateVolumeReferences());
        assertEquals(1, lower.candidateVolumeReferences());
        assertEquals(1, upper.candidateVolumeReferences());
        assertTrue(lower.solidBlockCount() > 0);
        assertTrue(upper.solidBlockCount() > 0);
        assertTrue(adapter.isSolidOwnedBy(lowerId, 0, 236, 0));
        assertTrue(adapter.isSolidOwnedBy(upperId, 0, 356, 0));
        assertFalse(
                adapter.isSolidOwnedByOtherVolume(lowerId, 0, 236, 0),
                "upper volume must not claim the lower suspension sample");
        assertTrue(
                adapter.isSolidOwnedByOtherVolume(lowerId, 0, 356, 0),
                "upper volume must be visible as a foreign owner at its suspension sample");
        assertEquals(
                lower.solidBlockCount() + upper.solidBlockCount(),
                composite.solidBlockCount(),
                "non-overlapping stacked exact volumes should sum to the composite occupancy");
        assertArrayEquals(
                lower.blockKeys(),
                adapter.materialize(lowerId, chunkPos, 176, 256).blockKeys(),
                "deferred exact-volume rematerialization must be deterministic");
    }

    private static OptionalInt manualFirstFreeHeight(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ,
            int minimumY,
            int height) {
        int maximumYExclusive = Math.addExact(minimumY, height);
        for (int worldY = maximumYExclusive - 1; worldY >= minimumY; worldY--) {
            if (adapter.isSolidOwnedBy(volumeId, worldX, worldY, worldZ)) {
                return OptionalInt.of(worldY + 1);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean contains(
            MinecraftChunkMaterialization materialization,
            net.minecraft.resources.ResourceLocation target) {
        for (var key : materialization.blockKeys()) {
            if (target.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static SkyIslandWorldCatalog catalog() {
        var compiled = compiledMassif();
        var id = new SkyIslandWorldVolumeId(ROOT_SEED, "anchor", 0, 0, ROOT_SEED);
        var worldVolume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(-256.0, 256.0, 100.0, 500.0, -256.0, 256.0),
                compiled);
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(worldVolume));
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledMassif() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledTableland(
            long seed,
            double elevation) {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                elevation,
                56.0,
                12.0,
                28.0,
                10.0,
                0.0,
                0.15,
                0.70,
                0.0,
                0.0,
                18.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.TABLELAND);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
