package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeSurfacePopulationPlanTest {
    private static final SkyIslandWorldVolumeId VOLUME_ID =
            new SkyIslandWorldVolumeId(55L, "surface-population-test", 0, 0, 77L);
    private static final SkyforgeExactVolumeBiomeResolver FOREST =
            (volumeId, x, y, z) -> Biomes.FOREST;

    @Test
    void firstSurfacePolicyAdmitsVegetationWithoutLeakingOtherWorldgenSystems() {
        assertEquals(
                List.of(GenerationStep.Decoration.VEGETAL_DECORATION),
                SkyforgeSurfacePopulationPhasePolicy.admittedPhases());
        assertTrue(SkyforgeSurfacePopulationPhasePolicy.isAdmitted(
                GenerationStep.Decoration.VEGETAL_DECORATION));

        assertFalse(SkyforgeSurfacePopulationPhasePolicy.isAdmitted(
                GenerationStep.Decoration.LAKES));
        assertFalse(SkyforgeSurfacePopulationPhasePolicy.isAdmitted(
                GenerationStep.Decoration.UNDERGROUND_ORES));
        assertFalse(SkyforgeSurfacePopulationPhasePolicy.isAdmitted(
                GenerationStep.Decoration.UNDERGROUND_DECORATION));
        assertFalse(SkyforgeSurfacePopulationPhasePolicy.isAdmitted(
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION));
    }

    @Test
    void surfaceEcologyPlanPreservesOneExactOwnerAndNativePhaseOrder() {
        var plan = SkyforgeNativeSurfacePopulationPlan.surfaceEcology(VOLUME_ID, FOREST, 24);

        assertEquals(VOLUME_ID, plan.volumeId());
        assertEquals(List.of(GenerationStep.Decoration.VEGETAL_DECORATION), plan.phases());
        assertEquals(24, plan.maximumAttachmentDepth());
        assertEquals(Biomes.FOREST, plan.biomeResolver().resolve(VOLUME_ID, 4, 120, 9));
        assertTrue(plan.biomeResolver().supportsSurface(VOLUME_ID, 4, 120, 9));
    }

    @Test
    void providerBiomeResolverRequiresTheLiveGenerationContext() {
        var resolver = SkyforgeProviderBiomeResolver.create();

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(VOLUME_ID, 4, 120, 9));
    }
    @Test
    void providerBiomeSelectorFindsTargetAcrossTheWholeSharedChunk() {
        var chunk = new net.minecraft.world.level.ChunkPos(0, 0);
        var search = SkyforgeSharedSurfaceBiomeSampleSelector.select(
                chunk,
                List.of(
                        new SkyforgeSharedSurfaceBiomeSampleSelector.Column(8, 8, 100, 200, Biomes.TAIGA, Biomes.TAIGA),
                        new SkyforgeSharedSurfaceBiomeSampleSelector.Column(12, 12, 102, 202, Biomes.FOREST, Biomes.TAIGA),
                        new SkyforgeSharedSurfaceBiomeSampleSelector.Column(15, 15, 101, 201, Biomes.FOREST, Biomes.FOREST)),
                2,
                Biomes.FOREST,
                Biomes.TAIGA);

        assertEquals(3, search.sharedColumns());
        assertEquals(2, search.lowerProviderBiomeMatches());
        assertEquals(2, search.providerBiomeMatches());
        assertEquals(1, search.providerBiomePairMatches());
        var selected = search.selected().orElseThrow();
        assertEquals(12, selected.x());
        assertEquals(12, selected.z());
        assertEquals(102, selected.lowerSurfaceY());
        assertEquals(202, selected.upperSurfaceY());
        assertEquals(Biomes.FOREST, selected.lowerProviderBiome());
        assertEquals(Biomes.TAIGA, selected.upperProviderBiome());
    }

    @Test
    void providerBiomeSelectorKeepsTheSharedTerrainThreshold() {
        var search = SkyforgeSharedSurfaceBiomeSampleSelector.select(
                new net.minecraft.world.level.ChunkPos(0, 0),
                List.of(new SkyforgeSharedSurfaceBiomeSampleSelector.Column(8, 8, 100, 200, Biomes.FOREST, Biomes.TAIGA)),
                2,
                Biomes.FOREST,
                Biomes.TAIGA);

        assertEquals(1, search.sharedColumns());
        assertEquals(1, search.lowerProviderBiomeMatches());
        assertEquals(1, search.providerBiomeMatches());
        assertEquals(1, search.providerBiomePairMatches());
        assertTrue(search.selected().isEmpty());
    }


    @Test
    void planRejectsNonSurfaceOrDuplicatePhaseAdmission() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeNativeSurfacePopulationPlan(
                        VOLUME_ID,
                        FOREST,
                        List.of(GenerationStep.Decoration.LAKES),
                        24));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeNativeSurfacePopulationPlan(
                        VOLUME_ID,
                        FOREST,
                        List.of(
                                GenerationStep.Decoration.VEGETAL_DECORATION,
                                GenerationStep.Decoration.VEGETAL_DECORATION),
                        24));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeNativeSurfacePopulationPlan.surfaceEcology(VOLUME_ID, FOREST, -1));
    }
}
