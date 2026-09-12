package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeCreateUndergroundResourceAuthorityTest {
    @Test
    void createUndergroundResourcesAreRejectedInsideExactVolumePopulation() {
        assertFalse(SkyforgeCreateUndergroundResourceAuthority.admits(
                ResourceLocation.fromNamespaceAndPath("create", "zinc_ore"),
                GenerationStep.Decoration.UNDERGROUND_ORES));
        assertFalse(SkyforgeCreateUndergroundResourceAuthority.admits(
                ResourceLocation.fromNamespaceAndPath("create", "striated_ores_overworld"),
                GenerationStep.Decoration.UNDERGROUND_ORES));
        assertFalse(SkyforgeCreateUndergroundResourceAuthority.admits(
                ResourceLocation.fromNamespaceAndPath("create", "future_underground_resource"),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    }

    @Test
    void vanillaOresAndNonOreCreateFeaturesRemainAdmitted() {
        assertTrue(SkyforgeCreateUndergroundResourceAuthority.admits(
                ResourceLocation.withDefaultNamespace("ore_iron_middle"),
                GenerationStep.Decoration.UNDERGROUND_ORES));
        assertTrue(SkyforgeCreateUndergroundResourceAuthority.admits(
                ResourceLocation.fromNamespaceAndPath("create", "some_surface_feature"),
                GenerationStep.Decoration.VEGETAL_DECORATION));
    }
}
