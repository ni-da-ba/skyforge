package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Minecraft-owned surface representation adapter for post-surface Skyforge terrain.
 *
 * <p>The adapter snapshots the already-built native Minecraft surface in each chunk column and
 * copies that native top material onto every exposed top of the Skyforge materialization in the
 * same column. It does not infer climate or biome semantics in Skyforge and it does not rerun the
 * vanilla surface system. Minecraft's own surface result remains the representation authority.
 */
public final class MinecraftNativeSurfaceTopAdapter {
    private static final int CHUNK_WIDTH = 16;

    /**
     * Returns a materialization whose exposed Skyforge tops inherit the pre-existing native top
     * block for their Minecraft column.
     *
     * <p>Fluids and air are skipped while discovering the native surface. A native surface at or
     * above a Skyforge top is not copied onto that top, which avoids treating terrain intersections
     * as floating exposed surfaces. All non-top Skyforge blocks remain exactly as projected by the
     * accepted Skyforge palette.
     */
    public MinecraftChunkMaterialization adapt(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(materialization, "materialization");
        if (!chunk.getPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("chunk position differs from materialization ownership");
        }

        ResourceLocation[] blockKeys = materialization.blockKeys();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                NativeSurface nativeSurface = findNativeSurface(chunk, cursor, worldX, worldZ);
                if (nativeSurface == null) {
                    continue;
                }
                adaptExposedTops(
                        blockKeys,
                        materialization.minimumY(),
                        materialization.height(),
                        localX,
                        localZ,
                        nativeSurface);
            }
        }

        return new MinecraftChunkMaterialization(
                materialization.chunkPos(),
                materialization.minimumY(),
                materialization.height(),
                blockKeys,
                materialization.candidateVolumeReferences());
    }

    private static NativeSurface findNativeSurface(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int worldZ) {
        int maximumY = chunk.getMinBuildHeight() + chunk.getHeight() - 1;
        for (int worldY = maximumY; worldY >= chunk.getMinBuildHeight(); worldY--) {
            BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                continue;
            }
            return new NativeSurface(worldY, key);
        }
        return null;
    }

    private static void adaptExposedTops(
            ResourceLocation[] blockKeys,
            int minimumY,
            int height,
            int localX,
            int localZ,
            NativeSurface nativeSurface) {
        for (int localY = height - 1; localY >= 0; localY--) {
            int index = linearIndex(localX, localY, localZ);
            if (SkyforgeMinecraftBlockPalette.AIR.equals(blockKeys[index])) {
                continue;
            }

            boolean exposedAbove = localY == height - 1
                    || SkyforgeMinecraftBlockPalette.AIR.equals(
                            blockKeys[linearIndex(localX, localY + 1, localZ)]);
            if (!exposedAbove) {
                continue;
            }

            int worldY = minimumY + localY;
            if (nativeSurface.worldY() < worldY) {
                blockKeys[index] = nativeSurface.blockKey();
            }
        }
    }

    private static int linearIndex(int localX, int localY, int localZ) {
        return localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY);
    }

    private record NativeSurface(int worldY, ResourceLocation blockKey) {}
}
