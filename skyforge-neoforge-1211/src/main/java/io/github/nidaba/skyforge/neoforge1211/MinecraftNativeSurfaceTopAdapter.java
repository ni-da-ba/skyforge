package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Arrays;
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
    static final int MAX_PROVIDER_SURFACE_RISE_BLOCKS = 64;
    static final int PROVIDER_RELIEF_CURVE_REFERENCE_BLOCKS = 160;
    static final String PROVIDER_RELIEF_MAPPING = "bounded_signed_linear_160";

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
     * <p>The provider's surface height is interpreted relative to its captured world-fixed
     * baseline, then passed through a bounded linear relief curve onto the existing Skyforge
     * surface. The result is clamped to the chunk build range and a bounded 64-block extension
     * above the authored top-volume bound. Only newly added positions receive provider claims; the
     * compiled island footprint, lower body, and underside stay authoritative.
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
        ResourceLocation dominantProviderSurfaceMaterial = snapshot.mostCommonSurfaceMaterial();
        ResourceLocation[] projectedSurfaceMaterials = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] projectedSurfaceTopYs = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        Arrays.fill(projectedSurfaceTopYs, Integer.MIN_VALUE);
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
                ResourceLocation authoredCapSurfaceMaterial =
                        blockKeys[linearIndex(localX, topLocalY, localZ)];
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
                // an absolute elevation datum. A bounded linear curve transfers signed source relief
                // into the extension envelope. Provider-owned rises remain bounded above the authored
                // top; Skyforge footprint, body, and underside remain untouched.
                long providerRelief = (long) nativeSurface.orElseThrow().worldY() - snapshot.baselineY();
                long projectedProviderRelief = projectProviderRelief(providerRelief);
                long desiredY = (long) originalTopY + projectedProviderRelief;
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
                    // The added island rise needs more than a one-voxel material cap. Copy a
                    // bounded native near-surface profile into newly claimed cells so exposed
                    // ledges retain the provider's grass/dirt/stone transition instead of becoming
                    // an artificial stone shell. Falling materials are excluded at every depth.
                    for (int depth = 1; depth < MinecraftNativeSurfaceSnapshot.SURFACE_STRATA_DEPTH; depth++) {
                        int worldY = targetTopY - depth;
                        if (worldY <= originalTopY) {
                            break;
                        }
                        var stratum = snapshot.surfaceStratum(localX, localZ, depth);
                        if (stratum.isPresent() && stableTerrainProfileMaterial(stratum.orElseThrow())) {
                            int localY = worldY - materialization.minimumY();
                            blockKeys[linearIndex(localX, localY, localZ)] = stratum.orElseThrow();
                        }
                    }
                } else if (targetTopY < originalTopY) {
                    for (int worldY = targetTopY + 1; worldY <= originalTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        blockKeys[linearIndex(localX, localY, localZ)] = SkyforgeMinecraftBlockPalette.AIR;
                    }
                }

                int targetIndex = linearIndex(localX, targetLocalY, localZ);
                ResourceLocation nativeBlockKey = nativeSurface.orElseThrow().blockKey();
                ResourceLocation selectedSurfaceMaterial = stableTerrainProfileMaterial(nativeBlockKey)
                        ? nativeBlockKey
                        : authoredCapSurfaceMaterial;
                if (SkyforgeMinecraftBlockPalette.STONE.equals(nativeBlockKey)
                        && dominantProviderSurfaceMaterial != null
                        && !SkyforgeMinecraftBlockPalette.STONE.equals(dominantProviderSurfaceMaterial)) {
                    selectedSurfaceMaterial = stableTerrainProfileMaterial(dominantProviderSurfaceMaterial)
                            ? dominantProviderSurfaceMaterial
                            : authoredCapSurfaceMaterial;
                }
                if (stableTerrainProfileMaterial(selectedSurfaceMaterial)) {
                    blockKeys[targetIndex] = selectedSurfaceMaterial;
                }
                int surfaceIndex = localX + CHUNK_WIDTH * localZ;
                projectedSurfaceMaterials[surfaceIndex] = blockKeys[targetIndex];
                projectedSurfaceTopYs[surfaceIndex] = targetTopY;
            }
        }

        // Provider uplift is initially solid-filled so it cannot open gaps in the authored island.
        // Re-skin only those newly claimed blocks whose horizontal faces meet air. This carries the
        // selected column surface across steep provider-created walls without repainting hidden
        // interior material or changing any occupancy.
        for (var claimedCell : providerWriteClaims.keySet()) {
            net.minecraft.core.BlockPos position = net.minecraft.core.BlockPos.of(claimedCell);
            int localX = position.getX() - materialization.chunkPos().getMinBlockX();
            int localZ = position.getZ() - materialization.chunkPos().getMinBlockZ();
            if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
                continue;
            }
            int localY = position.getY() - materialization.minimumY();
            if (localY < 0 || localY >= materialization.height()
                    || !hasHorizontalAirExposure(blockKeys, localX, localY, localZ)) {
                continue;
            }
            int surfaceIndex = localX + CHUNK_WIDTH * localZ;
            int projectedTopY = projectedSurfaceTopYs[surfaceIndex];
            if (projectedTopY == Integer.MIN_VALUE
                    || projectedTopY - position.getY() < MinecraftNativeSurfaceSnapshot.SURFACE_STRATA_DEPTH) {
                continue;
            }
            ResourceLocation sideSurfaceMaterial = projectedSurfaceMaterials[surfaceIndex];
            if (stableTerrainProfileMaterial(sideSurfaceMaterial)) {
                blockKeys[linearIndex(localX, localY, localZ)] = sideSurfaceMaterial;
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

    /**
     * Transfers signed source relief through a bounded quadratic curve. Positive relief raises
     * peaks and negative relief cuts valleys while the exact Skyforge top-volume envelope remains
     * the final height authority.
     */
    static long projectProviderRelief(long providerRelief) {
        long magnitude = Math.min(
                providerRelief == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(providerRelief),
                PROVIDER_RELIEF_CURVE_REFERENCE_BLOCKS);
        long numerator = (long) MAX_PROVIDER_SURFACE_RISE_BLOCKS * magnitude;
        long projectedMagnitude =
                (numerator + PROVIDER_RELIEF_CURVE_REFERENCE_BLOCKS / 2)
                        / PROVIDER_RELIEF_CURVE_REFERENCE_BLOCKS;
        return providerRelief < 0 ? -projectedMagnitude : projectedMagnitude;
    }

    private static boolean hasHorizontalAirExposure(
            ResourceLocation[] blockKeys,
            int localX,
            int localY,
            int localZ) {
        return (localX > 0
                        && SkyforgeMinecraftBlockPalette.AIR.equals(
                                blockKeys[linearIndex(localX - 1, localY, localZ)]))
                || (localX + 1 < CHUNK_WIDTH
                        && SkyforgeMinecraftBlockPalette.AIR.equals(
                                blockKeys[linearIndex(localX + 1, localY, localZ)]))
                || (localZ > 0
                        && SkyforgeMinecraftBlockPalette.AIR.equals(
                                blockKeys[linearIndex(localX, localY, localZ - 1)]))
                || (localZ + 1 < CHUNK_WIDTH
                        && SkyforgeMinecraftBlockPalette.AIR.equals(
                                blockKeys[linearIndex(localX, localY, localZ + 1)]));
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
     * Provider profile materials may replace the authored cap only when they preserve a stable,
     * coherent island surface.
     *
     * <p>Falling blocks can settle through hollow seams. A minority provider-stone region follows
     * the chunk's dominant stable provider surface material, preventing an isolated gray patch
     * while retaining stone where it is the provider's dominant terrain surface.
     */
    private static boolean stableTerrainProfileMaterial(ResourceLocation materialKey) {
        return !SkyforgeMinecraftBlockPalette.AIR.equals(materialKey)
                && !(BuiltInRegistries.BLOCK.get(materialKey) instanceof FallingBlock);
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
