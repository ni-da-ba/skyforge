package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeHydrologyDressingStageTest {
    @Test
    void builtInAquaticFeatureTypesReceiveHydrologyDressingAuthority() {
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.SEAGRASS));
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.KELP));
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.SEA_PICKLE));
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.SUBSTRATE_DISK,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.DISK));
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.NONE,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.TREE));
        assertEquals(
                SkyforgeNativeHydrologyDressingStage.Role.NONE,
                SkyforgeNativeHydrologyDressingStage.classifyFeature(Feature.UNDERWATER_MAGMA));
    }

    @Test
    void aquaticRoleOnlyReplacesWaterWithWaterBearingVegetation() {
        assertTrue(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                Blocks.WATER.defaultBlockState(),
                Blocks.SEAGRASS.defaultBlockState()));
        assertFalse(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                Blocks.WATER.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState()));
        assertFalse(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.AQUATIC_VEGETATION,
                Blocks.CLAY.defaultBlockState(),
                Blocks.SEAGRASS.defaultBlockState()));
    }

    @Test
    void substrateDiskRoleAllowsSedimentButNotGeothermalOrSurfaceGrass() {
        assertTrue(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.SUBSTRATE_DISK,
                Blocks.CLAY.defaultBlockState(),
                Blocks.SAND.defaultBlockState()));
        assertTrue(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.SUBSTRATE_DISK,
                Blocks.STONE.defaultBlockState(),
                Blocks.GRAVEL.defaultBlockState()));
        assertFalse(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.SUBSTRATE_DISK,
                Blocks.CLAY.defaultBlockState(),
                Blocks.MAGMA_BLOCK.defaultBlockState()));
        assertFalse(SkyforgeNativeHydrologyDressingStage.allowsReplacement(
                SkyforgeNativeHydrologyDressingStage.Role.SUBSTRATE_DISK,
                Blocks.CLAY.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState()));
    }
}
