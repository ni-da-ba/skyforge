package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        MinecraftChunkMaterialization distant =
                adapter.materialize(new ChunkPos(100, 100), MINIMUM_Y, 32);

        assertEquals(0, distant.candidateVolumeReferences());
        assertEquals(0, distant.solidBlockCount());
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
}
