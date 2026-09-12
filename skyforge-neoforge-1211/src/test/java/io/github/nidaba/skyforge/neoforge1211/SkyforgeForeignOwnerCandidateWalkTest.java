package io.github.nidaba.skyforge.neoforge1211;

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
import org.junit.jupiter.api.Test;

final class SkyforgeForeignOwnerCandidateWalkTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;

    @Test
    void directCandidateWalkMatchesHistoricalPointQueryOracleAcrossStackedAndDisjointVolumes() {
        long lowerSeed = ROOT_SEED ^ 0x4c4f574552L;
        long upperSeed = ROOT_SEED ^ 0x5550504552L;
        long distantSeed = ROOT_SEED ^ 0x44495354414e54L;
        SkyIslandWorldVolumeId lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "stacked", 0, 0, lowerSeed);
        SkyIslandWorldVolumeId upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "stacked", 0, 1, upperSeed);
        SkyIslandWorldVolumeId distantId = new SkyIslandWorldVolumeId(ROOT_SEED, "distant", 1, 0, distantSeed);
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalog(
                ROOT_SEED,
                List.of(
                        new SkyIslandWorldVolume(
                                lowerId,
                                new WorldBounds(-72.0, 72.0, 196.0, 288.0, -72.0, 72.0),
                                compiledTableland(lowerSeed, 236.0)),
                        new SkyIslandWorldVolume(
                                upperId,
                                new WorldBounds(-72.0, 72.0, 316.0, 408.0, -72.0, 72.0),
                                compiledTableland(upperSeed, 356.0)),
                        new SkyIslandWorldVolume(
                                distantId,
                                new WorldBounds(928.0, 1072.0, 196.0, 288.0, 928.0, 1072.0),
                                compiledTableland(distantSeed, 236.0))));
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        int[] xs = {-80, -32, 0, 32, 80, 1000};
        int[] ys = {180, 220, 236, 250, 300, 340, 356, 370, 420};
        int[] zs = {-80, 0, 32, 80, 1000};
        for (SkyIslandWorldVolumeId ownerId : List.of(lowerId, upperId, distantId)) {
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        boolean historicalOracle = adapter.claimingVolumeIds(x, y, z).stream()
                                .anyMatch(candidateId -> !candidateId.equals(ownerId));
                        assertEquals(
                                historicalOracle,
                                adapter.isSolidOwnedByOtherVolume(ownerId, x, y, z),
                                () -> "foreign-owner mismatch for " + ownerId.path()
                                        + " at (" + x + "," + y + "," + z + ")");
                    }
                }
            }
        }
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
