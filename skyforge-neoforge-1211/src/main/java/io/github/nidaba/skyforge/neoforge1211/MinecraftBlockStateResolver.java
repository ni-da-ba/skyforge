package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Resolves concrete Minecraft block registry keys to live block states. */
public final class MinecraftBlockStateResolver {
    private final ConcurrentMap<ResourceLocation, BlockState> resolvedStates = new ConcurrentHashMap<>();

    /**
     * Resolves one registered block key to the block's default state.
     *
     * <p>Unknown keys fail explicitly instead of falling through the defaulted block registry to
     * air. This is required for registry-drift diagnostics and future optional-mod compatibility.
     */
    public BlockState resolve(ResourceLocation blockKey) {
        Objects.requireNonNull(blockKey, "blockKey");
        return resolvedStates.computeIfAbsent(blockKey, this::resolveUncached);
    }

    private BlockState resolveUncached(ResourceLocation blockKey) {
        if (!BuiltInRegistries.BLOCK.containsKey(blockKey)) {
            throw new IllegalArgumentException("unknown Minecraft block registry key: " + blockKey);
        }
        Block block = BuiltInRegistries.BLOCK.get(blockKey);
        return block.defaultBlockState();
    }
}
