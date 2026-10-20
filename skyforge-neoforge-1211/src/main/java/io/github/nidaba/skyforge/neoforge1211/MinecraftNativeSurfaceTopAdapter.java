package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Minecraft-owned surface representation adapter for Skyforge terrain.
 *
 * <p>Production generation supplies a snapshot captured after native surface construction and before
 * biome decoration. This keeps Minecraft's surface result as representation authority without
 * allowing later vegetation, structures or other placed features to become island terrain material.
 */
public final class MinecraftNativeSurfaceTopAdapter {
    private static final int CHUNK_WIDTH = 16;
    static final int MAX_PROVIDER_SURFACE_RISE_BLOCKS = 32;

    /**
     * Compatibility overload for isolated tests and direct adapter usage.
     *
     * <p>The normal generator path captures the snapshot before native decoration and calls the
     * snapshot overload instead.
     */
    public MinecraftChunkMaterialization adapt(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(chunk, "chunk");
        return adapt(MinecraftNativeSurfaceSnapshot.capture(chunk), materialization);
    }

    /**
     * Projects native provider relief onto the exposed top cap of composite Skyforge terrain.
     *
     * <p>The provider's surface height is interpreted relative to its captured baseline (normally
     * sea level), then transferred at full strength onto the existing Skyforge surface. The result
     * is clamped to the chunk build range and a bounded 32-block extension above the authored
     * top-volume bound. Only newly added positions receive provider claims; the compiled island
     * footprint, lower body, and underside stay authoritative.
     */
    TerrainProfileAdaptation adaptTerrainProfile(
            MinecraftNativeSurfaceSnapshot snapshot,
            MinecraftChunkMaterialization materialization,
            SkyforgeNeoForge1211ChunkAdapter adapter) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(materialization, "materialization");
        Objects.requireNonNull(adapter, "adapter");
        if (!snapshot.chunkPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("native surface snapshot differs from materialization ownership");
        }

        ResourceLocation[] blockKeys = materialization.blockKeys();
        Map<Long, SkyIslandWorldVolumeId> providerWriteClaims = new HashMap<>();
        int maximumMaterializedY = materialization.minimumY() + materialization.height() - 1;
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = materialization.chunkPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                var nativeSurface = snapshot.surface(localX, localZ);
                if (nativeSurface.isEmpty()) {
                    continue;
                }
                int worldX = materialization.chunkPos().getMinBlockX() + localX;
                int topLocalY = highestSolidLocalY(blockKeys, materialization.height(), localX, localZ);
                if (topLocalY < 0) {
                    continue;
                }
                int originalTopY = materialization.minimumY() + topLocalY;
                var owners = adapter.claimingVolumeIds(worldX, originalTopY, worldZ);
                if (owners.size() != 1) {
                    continue;
                }
                SkyIslandWorldVolumeId ownerId = owners.getFirst();
                WorldBounds volumeBounds = adapter.volumeBounds(ownerId).orElseThrow(() ->
                        new IllegalStateException("exact top owner has no authored volume bounds"));
                if (!volumeBounds.contains(worldX, originalTopY, worldZ)) {
                    continue;
                }

                int capBottomLocalY = topLocalY;
                while (capBottomLocalY > 0
                        && !SkyforgeMinecraftBlockPalette.AIR.equals(
                                blockKeys[linearIndex(localX, capBottomLocalY - 1, localZ)])) {
                    capBottomLocalY--;
                }
                int capBottomY = materialization.minimumY() + capBottomLocalY;
                int maximumAllowedY = Math.min(
                        maximumMaterializedY,
                        Math.addExact(floorToInt(volumeBounds.maximumY()), MAX_PROVIDER_SURFACE_RISE_BLOCKS));
                int minimumAllowedY = Math.max(
                        materialization.minimumY(),
                        Math.max(capBottomY, ceilToInt(volumeBounds.minimumY())));
                if (minimumAllowedY > maximumAllowedY) {
                    continue;
                }

                // Provider terrain is a local shape signal on a floating island, not
                // an absolute elevation datum. Transfer its configured relief at full strength.
                // The development profile path permits a bounded rise above the authored top
                // volume so positive provider relief is not flattened at its original ceiling;
                // Skyforge footprint, body, and underside remain untouched.
                long providerRelief = (long) nativeSurface.orElseThrow().worldY() - snapshot.baselineY();
                long desiredY = (long) originalTopY + providerRelief;
                int targetTopY = (int) Math.max(
                        (long) minimumAllowedY,
                        Math.min((long) maximumAllowedY, desiredY));
                int targetLocalY = targetTopY - materialization.minimumY();
                if (targetTopY > originalTopY) {
                    for (int worldY = originalTopY + 1; worldY <= targetTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        blockKeys[linearIndex(localX, localY, localZ)] = SkyforgeMinecraftBlockPalette.STONE;
                        providerWriteClaims.put(
                                net.minecraft.core.BlockPos.asLong(worldX, worldY, worldZ),
                                ownerId);
                    }
                } else if (targetTopY < originalTopY) {
                    for (int worldY = targetTopY + 1; worldY <= originalTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        blockKeys[linearIndex(localX, localY, localZ)] = SkyforgeMinecraftBlockPalette.AIR;
                    }
                }

                int targetIndex = linearIndex(localX, targetLocalY, localZ);
                ResourceLocation nativeBlockKey = nativeSurface.orElseThrow().blockKey();
                ResourceLocation supportBlockKey = targetLocalY > 0
                        ? blockKeys[linearIndex(localX, targetLocalY - 1, localZ)]
                        : SkyforgeMinecraftBlockPalette.AIR;
                if (stableTerrainProfileMaterial(nativeBlockKey, supportBlockKey)) {
                    blockKeys[targetIndex] = nativeBlockKey;
                }
            }
        }

        // The profile loop above already projects the native surface onto each column's
        // selected cap. Reapplying provider material to every exposed Skyforge segment here
        // can put gravity blocks on unsupported ledges above or beside the projected cap.
        MinecraftChunkMaterialization adapted = new MinecraftChunkMaterialization(
                materialization.chunkPos(),
                materialization.minimumY(),
                materialization.height(),
                blockKeys,
                materialization.candidateVolumeReferences());
        return new TerrainProfileAdaptation(adapted, providerWriteClaims);
    }

    private static int highestSolidLocalY(
            ResourceLocation[] blockKeys,
            int height,
            int localX,
            int localZ) {
        for (int localY = height - 1; localY >= 0; localY--) {
            if (!SkyforgeMinecraftBlockPalette.AIR.equals(blockKeys[linearIndex(localX, localY, localZ)])) {
                return localY;
            }
        }
        return -1;
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("volume maximum Y exceeds Minecraft integer coordinates");
        }
        return (int) floored;
    }

    private static int ceilToInt(double value) {
        double ceiled = Math.ceil(value);
        if (ceiled < Integer.MIN_VALUE || ceiled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("volume minimum Y exceeds Minecraft integer coordinates");
        }
        return (int) ceiled;
    }

    record TerrainProfileAdaptation(
            MinecraftChunkMaterialization materialization,
            Map<Long, SkyIslandWorldVolumeId> providerWriteClaims) {
        TerrainProfileAdaptation {
            Objects.requireNonNull(materialization, "materialization");
            providerWriteClaims = Map.copyOf(
                    Objects.requireNonNull(providerWriteClaims, "providerWriteClaims"));
        }
    }

    /**
     * Admits gravity-affected provider materials only where the projected cap has direct,
     * non-gravity structural support. Unsupported seam edges retain Skyforge's stable material.
     */
    private static boolean stableTerrainProfileMaterial(
            ResourceLocation nativeBlockKey,
            ResourceLocation supportBlockKey) {
        if (!(BuiltInRegistries.BLOCK.get(nativeBlockKey) instanceof FallingBlock)) {
            return true;
        }
        if (SkyforgeMinecraftBlockPalette.AIR.equals(supportBlockKey)) {
            return false;
        }
        return !(BuiltInRegistries.BLOCK.get(supportBlockKey) instanceof FallingBlock);
    }

    /** Returns a materialization whose exposed Skyforge tops inherit the captured native surface. */
    MinecraftChunkMaterialization adapt(
            MinecraftNativeSurfaceSnapshot snapshot,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(materialization, "materialization");
        if (!snapshot.chunkPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("native surface snapshot differs from materialization ownership");
        }

        ResourceLocation[] blockKeys = materialization.blockKeys();
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                var nativeSurface = snapshot.surface(localX, localZ);
                if (nativeSurface.isEmpty()) {
                    continue;
                }
                adaptExposedTops(
                        blockKeys,
                        materialization.minimumY(),
                        materialization.height(),
                        localX,
                        localZ,
                        nativeSurface.orElseThrow());
            }
        }

        return new MinecraftChunkMaterialization(
                materialization.chunkPos(),
                materialization.minimumY(),
                materialization.height(),
                blockKeys,
                materialization.candidateVolumeReferences());
    }

    private static void adaptExposedTops(
            ResourceLocation[] blockKeys,
            int minimumY,
            int height,
            int localX,
            int localZ,
            MinecraftNativeSurfaceSnapshot.NativeSurface nativeSurface) {
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
            if (nativeSurface.worldY() < worldY
                    && stableWhenAdapted(nativeSurface.blockKey())) {
                blockKeys[index] = nativeSurface.blockKey();
            }
        }
    }

    /**
     * Prevents concrete native representation from invalidating authoritative Skyforge occupancy.
     *
     * <p>Gravity-affected native surface blocks are never copied onto floating Skyforge tops.
     * Even when a sampled column has a local support block, the provider material can form a
     * detached edge beside a hollow seam and fall after neighboring columns are realized. Keeping
     * Skyforge's stable structural top at those columns prevents the native surface adapter from
     * destabilizing island occupancy. Non-falling native materials remain eligible.
     */
    private static boolean stableWhenAdapted(ResourceLocation nativeBlockKey) {
        return !(BuiltInRegistries.BLOCK.get(nativeBlockKey) instanceof FallingBlock);
    }

    private static int linearIndex(int localX, int localY, int localZ) {
        return localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY);
    }
}
