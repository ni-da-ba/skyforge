package io.github.nidaba.skyforge.neoforge1211;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * C21 A/B proof that Create's generic Overworld resource modifiers are separable from retained
 * Zinc/striated assets.
 *
 * <p>The suppressed mode is validation-only. Production exact-volume filtering remains owned by
 * the implementation population path; this fixture does not install a packaged global override.
 */
final class SkyforgeWaveC21CreateResourceAuthorityAcceptance {
    static final String MODE_PROPERTY = "skyforge.dev.waveC21CreateResourceAuthority";
    private static final ResourceLocation ZINC_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("create", "zinc_ore");
    private static final ResourceLocation STRIATED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("create", "striated_ores_overworld");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC21CreateResourceAuthorityAcceptance.class.getName());

    private SkyforgeWaveC21CreateResourceAuthorityAcceptance() {}

    static void installFromSystemProperty() {
        String raw = System.getProperty(MODE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Mode mode = Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> onServerStarted(event, mode));
    }

    private static void onServerStarted(ServerStartedEvent event, Mode mode) {
        requireLoaded("create");
        MinecraftServer server = event.getServer();

        String zincClass = modifierClass(server, ZINC_MODIFIER);
        String striatedClass = modifierClass(server, STRIATED_MODIFIER);

        requireBlock("create", "zinc_ore");
        requireBlock("create", "deepslate_zinc_ore");
        requireBlock("create", "crimsite");

        boolean zincAddFeatures = zincClass.contains("AddFeaturesBiomeModifier");
        boolean striatedAddFeatures = striatedClass.contains("AddFeaturesBiomeModifier");

        switch (mode) {
            case BASELINE -> {
                if (!zincAddFeatures || !striatedAddFeatures) {
                    fail("baseline expected Create add-feature modifiers but observed zinc="
                            + zincClass + " striated=" + striatedClass);
                }
            }
            case SUPPRESSED -> {
                if (zincAddFeatures || striatedAddFeatures) {
                    fail("suppressed control still exposed Create add-feature modifiers: zinc="
                            + zincClass + " striated=" + striatedClass);
                }
                if (!zincClass.equals(striatedClass)) {
                    fail("suppressed control should decode both neoforge:none overrides to one no-op type: zinc="
                            + zincClass + " striated=" + striatedClass);
                }
            }
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C21 PASS mode="
                        + mode
                        + " zincModifierClass="
                        + zincClass
                        + " striatedModifierClass="
                        + striatedClass
                        + " zincOre=true"
                        + " deepslateZincOre=true"
                        + " crimsite=true"
                        + " productionGlobalOverride=false");
    }

    private static String modifierClass(MinecraftServer server, ResourceLocation location) {
        var lookup = server.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        ResourceKey<BiomeModifier> key =
                ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, location);
        return lookup.get(key)
                .orElseThrow(() -> new IllegalStateException("missing biome modifier " + location))
                .value()
                .getClass()
                .getName();
    }

    private static Block requireBlock(String namespace, String path) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Block block = BuiltInRegistries.BLOCK.get(key);
        if (block == Blocks.AIR) {
            throw new IllegalStateException("missing retained Create block asset " + key);
        }
        return block;
    }

    private static void requireLoaded(String modId) {
        if (!ModList.get().isLoaded(Objects.requireNonNull(modId, "modId"))) {
            throw new IllegalStateException("required C21 mod not loaded: " + modId);
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C21 FAIL " + reason);
        throw new IllegalStateException("Wave C21 resource-authority acceptance failed: " + reason);
    }

    private enum Mode {
        BASELINE,
        SUPPRESSED
    }
}
