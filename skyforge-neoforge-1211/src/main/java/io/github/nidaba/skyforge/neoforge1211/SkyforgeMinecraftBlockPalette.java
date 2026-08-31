package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContext;
import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal Minecraft-facing representation policy for the first adapter proof.
 *
 * <p>The returned values are concrete vanilla block registry keys. They deliberately remain
 * backend-owned: no Minecraft key is introduced into {@code skyforge-world}.
 */
public final class SkyforgeMinecraftBlockPalette {
    public static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("air");
    public static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("dirt");
    public static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");
    public static final ResourceLocation DEEPSLATE = ResourceLocation.withDefaultNamespace("deepslate");

    /** Projects one accepted Skyforge terrain semantic to a concrete vanilla block registry key. */
    public ResourceLocation blockKey(SkyIslandTerrainSampleContext context) {
        Objects.requireNonNull(context, "context");
        return switch (context.semantic()) {
            case AIR -> AIR;
            case SURFACE_MANTLE -> DIRT;
            case EDGE_SHELL, UNDERSIDE_SHELL, SHALLOW_INTERIOR -> STONE;
            case DEEP_MASS -> DEEPSLATE;
        };
    }

    /** Verifies that this representation policy preserves Skyforge's authoritative occupancy. */
    public boolean preservesOccupancy(
            SkyIslandTerrainSemantic semantic,
            ResourceLocation blockKey) {
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(blockKey, "blockKey");
        return semantic.isSolid() != AIR.equals(blockKey);
    }
}
