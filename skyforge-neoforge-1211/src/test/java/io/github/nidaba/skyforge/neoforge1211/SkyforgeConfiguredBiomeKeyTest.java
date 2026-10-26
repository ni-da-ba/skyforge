package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

final class SkyforgeConfiguredBiomeKeyTest {
    @Test
    void parsesNamespacedModdedBiomeWithoutCompileTimeModDependency() {
        assertEquals(
                ResourceKey.create(
                        Registries.BIOME,
                        ResourceLocation.parse("biomesoplenty:maple_woods")),
                SkyforgeNeoForge1211BiomePopulationDevRuntime.configuredBiomeKey(
                        " biomesoplenty:maple_woods "));
    }

    @Test
    void rejectsMissingOrMalformedConfiguredBiomeKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeNeoForge1211BiomePopulationDevRuntime.configuredBiomeKey("  "));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeNeoForge1211BiomePopulationDevRuntime.configuredBiomeKey("not a resource"));
    }
}
