package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

final class SkyforgeExactVolumeMaterializationOptimizationTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;

    @Test
    void exactMassifMaterializationMatchesLegacyCompositeAcrossCenterEdgeAndEmptyColumns() {
        var compiled = compile(MorphologyFamily.MASSIF, ROOT_SEED, 320.0, 192.0, 76.0, 100.0, 48.0, 28.0);
        var id = new SkyIslandWorldVolumeId(ROOT_SEED, "massif-equivalence", 0, 0, ROOT_SEED);
        var catalog = new SkyIslandWorldCatalog(
                ROOT_SEED,
                List.of(new SkyIslandWorldVolume(
                        id,
                        new WorldBounds(-256.0, 256.0, 100.0, 500.0, -256.0, 256.0),
                        compiled)));

        assertExactMatchesGeneric(catalog, id, 176, 248, List.of(
                new ChunkPos(0, 0),
                new ChunkPos(10, 0),
                new ChunkPos(15, 15)));
    }

    @Test
    void exactTablelandMaterializationMatchesLegacyCompositeAcrossCenterEdgeAndEmptyColumns() {
        long seed = ROOT_SEED ^ 0x5441424c454c414eL;
        var compiled = compile(MorphologyFamily.TABLELAND, seed, 280.0, 88.0, 18.0, 42.0, 14.0, 20.0);
        var id = new SkyIslandWorldVolumeId(ROOT_SEED, "tableland-equivalence", 0, 0, seed);
        var catalog = new SkyIslandWorldCatalog(
                ROOT_SEED,
                List.of(new SkyIslandWorldVolume(
                        id,
                        new WorldBounds(-128.0, 128.0, 196.0, 336.0, -128.0, 128.0),
                        compiled)));

        assertExactMatchesGeneric(catalog, id, 176, 192, List.of(
                new ChunkPos(0, 0),
                new ChunkPos(4, 0),
                new ChunkPos(7, 7)));
    }

    private static void assertExactMatchesGeneric(
            SkyIslandWorldCatalog catalog,
            SkyIslandWorldVolumeId id,
            int minimumY,
            int height,
            List<ChunkPos> chunks) {
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        for (ChunkPos chunkPos : chunks) {
            MinecraftChunkMaterialization generic = adapter.materialize(chunkPos, minimumY, height);
            MinecraftChunkMaterialization exact = adapter.materialize(id, chunkPos, minimumY, height);

            assertEquals(1, generic.candidateVolumeReferences());
            assertEquals(1, exact.candidateVolumeReferences());
            assertEquals(generic.solidBlockCount(), exact.solidBlockCount());
            assertArrayEquals(
                    generic.blockKeys(),
                    exact.blockKeys(),
                    "exact-volume specialization changed block-key output for " + chunkPos);
        }
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compile(
            MorphologyFamily family,
            long seed,
            double elevation,
            double horizontalScale,
            double verticalScale,
            double shoulderScale,
            double undersideScale,
            double detailScale) {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                elevation,
                horizontalScale,
                verticalScale,
                shoulderScale,
                undersideScale,
                Math.PI / 8.0,
                0.45,
                0.65,
                0.20,
                0.0,
                detailScale);
        var provider = SkyIslandMorphologyProviders.builtInId(family);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
