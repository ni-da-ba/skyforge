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
     * Transfers the native height profile onto one exact Skyforge volume's top-connected cap.
     *
     * <p>Each supported X/Z column keeps its original footprint and lower body. Native height
     * deviation from the source generator's baseline raises or cuts only the top cap, clamped to
     * the owning island's authored bounds. Added blocks carry explicit provider-write claims.
     */
    TerrainProfileAdaptation adaptTerrainProfile(
            MinecraftNativeSurfaceSnapshot snapshot,
            MinecraftChunkMaterialization materialization,
            SkyIslandWorldVolumeId volumeId,
            WorldBounds volumeBounds) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(materialization, "materialization");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(volumeBounds, "volumeBounds");
        if (!snapshot.chunkPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("native surface snapshot differs from materialization ownership");
        }

        ResourceLocation[] blockKeys = materialization.blockKeys();
        Map<Long, SkyIslandWorldVolumeId> providerWriteClaims = new HashMap<>();
        int maximumMaterializedY = materialization.minimumY() + materialization.height() - 1;
        int maximumAllowedY = Math.min(
                maximumMaterializedY,
                floorToInt(volumeBounds.maximumY()));
        int minimumAllowedY = Math.max(
                materialization.minimumY(),
                ceilToInt(volumeBounds.minimumY()));

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
                int minimumTopY = Math.max(capBottomY, minimumAllowedY);
                int sourceRelief = nativeSurface.orElseThrow().worldY() - snapshot.baselineY();
                long desiredY = (long) originalTopY + sourceRelief;
                int targetTopY = (int) Math.max(
                        minimumTopY,
                        Math.min((long) maximumAllowedY, desiredY));
                if (targetTopY < minimumTopY || targetTopY > maximumAllowedY) {
                    continue;
                }

                if (targetTopY > originalTopY) {
                    for (int worldY = originalTopY + 1; worldY <= targetTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        int index = linearIndex(localX, localY, localZ);
                        blockKeys[index] = SkyforgeMinecraftBlockPalette.STONE;
                        providerWriteClaims.put(
                                net.minecraft.core.BlockPos.asLong(worldX, worldY, worldZ),
                                volumeId);
                    }
                } else if (targetTopY < originalTopY) {
                    for (int worldY = targetTopY + 1; worldY <= originalTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        blockKeys[linearIndex(localX, localY, localZ)] = SkyforgeMinecraftBlockPalette.AIR;
                    }
                }

                int targetLocalY = targetTopY - materialization.minimumY();
                int targetIndex = linearIndex(localX, targetLocalY, localZ);
                ResourceLocation nativeBlockKey = nativeSurface.orElseThrow().blockKey();
                if (stableWhenAdapted(blockKeys, targetLocalY, localX, localZ, nativeBlockKey)) {
                    blockKeys[targetIndex] = nativeBlockKey;
                }
            }
        }

        // Keep Minecraft's surface material authority on every exposed Skyforge segment, including
        // columns whose authored profile was not vertically displaced by the source.
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                var nativeSurface = snapshot.surface(localX, localZ);
                if (nativeSurface.isPresent()) {
                    adaptExposedTops(
                            blockKeys,
                            materialization.minimumY(),
                            materialization.height(),
                            localX,
                            localZ,
                            nativeSurface.orElseThrow());
                }
            }
        }

        var adapted = new MinecraftChunkMaterialization(
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
            if (providerWriteClaims.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("provider write claims must name exact volume owners");
            }
        }
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
                    && stableWhenAdapted(
                            blockKeys,
                            localY,
                            localX,
                            localZ,
                            nativeSurface.blockKey())) {
                blockKeys[index] = nativeSurface.blockKey();
            }
        }
    }

    /**
     * Prevents concrete native representation from invalidating authoritative Skyforge occupancy.
     *
     * <p>A gravity-affected native surface block may be copied onto an elevated Skyforge top only
     * when the Skyforge materialization already contains a solid immediately below it. Otherwise a
     * one-voxel fringe can fall after realization and disappear before/after save, violating the
     * accepted ADR-0041 invariant that surface adaptation never removes a Skyforge solid.
     * Non-falling native materials remain eligible exactly as before.
     */
    private static boolean stableWhenAdapted(
            ResourceLocation[] blockKeys,
            int localY,
            int localX,
            int localZ,
            ResourceLocation nativeBlockKey) {
        var nativeBlock = BuiltInRegistries.BLOCK.get(nativeBlockKey);
        if (!(nativeBlock instanceof FallingBlock)) {
            return true;
        }
        if (localY == 0) {
            return false;
        }
        return !SkyforgeMinecraftBlockPalette.AIR.equals(
                blockKeys[linearIndex(localX, localY - 1, localZ)]);
    }

    private static int linearIndex(int localX, int localY, int localZ) {
        return localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY);
    }
}
