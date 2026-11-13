package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
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
    // A 64-block fade consumed most of the provider relief in the treatment's 32-block
    // interior band. Keep a two-chunk transition while allowing the selected
    // island interior to retain the relief measured by the A/B acceptance.
    static final int PROVIDER_RELIEF_EDGE_BLEND_RADIUS_BLOCKS = 32;
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
     * <p>The provider's surface height is interpreted relative to a deterministic median sampled
     * from the island anchor chunk, then passed through a bounded linear relief curve onto the existing Skyforge
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
        ResourceLocation dominantProviderSurfaceMaterial = dominantStableProviderSurfaceMaterial(snapshot);
        ResourceLocation[] projectedSurfaceMaterials = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] projectedSurfaceTopYs = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] originalSurfaceTopYs = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] projectedSurfaceLayerCounts = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] diagnosticFilteredProviderY = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] diagnosticProviderDatumY = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] diagnosticAppliedReliefY = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        int[] diagnosticEdgeInfluencePermille = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        ResourceLocation[] diagnosticAuthoredMaterials = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH];
        ResourceLocation[] diagnosticProviderMaterials = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH];
        ResourceLocation[] diagnosticRealizedMaterials = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH];
        Arrays.fill(projectedSurfaceTopYs, Integer.MIN_VALUE);
        Arrays.fill(originalSurfaceTopYs, Integer.MIN_VALUE);
        Arrays.fill(diagnosticFilteredProviderY, Integer.MIN_VALUE);
        Arrays.fill(diagnosticProviderDatumY, Integer.MIN_VALUE);
        Arrays.fill(diagnosticAppliedReliefY, Integer.MIN_VALUE);
        Arrays.fill(diagnosticEdgeInfluencePermille, Integer.MIN_VALUE);
        Map<Long, SkyIslandWorldVolumeId> providerWriteClaims = new HashMap<>();
        Map<SkyIslandWorldVolumeId, ProviderReliefFootprint> providerReliefFootprintsForChunk =
                new HashMap<>();
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
                int surfaceIndex = localX + CHUNK_WIDTH * localZ;
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
                // an absolute elevation datum. The exact Skyforge footprint owns the blend mask:
                // full provider relief is reached only inside the island, while relief fades to zero
                // over a 64-block edge band. This joins the native profile to the authored cap rather
                // than ending a full-height provider hill at the island silhouette.
                ProviderReliefFootprint footprint = providerReliefFootprintsForChunk.get(ownerId);
                if (footprint == null) {
                    long footprintStart = SkyforgeRuntimePerformanceMetrics.start();
                    footprint = ProviderReliefFootprint.capture(
                            adapter, ownerId, volumeBounds, materialization.chunkPos());
                    SkyforgeRuntimePerformanceMetrics.recordSince(
                            "terrain.providerSnapshot.reliefFootprint", footprintStart);
                    providerReliefFootprintsForChunk.put(ownerId, footprint);
                }
                double edgeDistance = footprint.interiorDistance(worldX, worldZ);
                double providerInfluence = providerReliefInfluenceAtFootprintEdge(
                        edgeDistance,
                        PROVIDER_RELIEF_EDGE_BLEND_RADIUS_BLOCKS);
                int filteredProviderY = snapshot.smoothedProviderSurfaceY(localX, localZ);
                int providerDatumY = snapshot.providerReliefDatumY(ownerId);
                long providerRelief = (long) filteredProviderY - providerDatumY;
                long projectedProviderRelief = Math.round(projectProviderRelief(providerRelief) * providerInfluence);
                long desiredY = (long) originalTopY + projectedProviderRelief;
                int targetTopY = (int) Math.max(
                        (long) minimumAllowedY,
                        Math.min((long) maximumAllowedY, desiredY));
                diagnosticFilteredProviderY[surfaceIndex] = filteredProviderY;
                diagnosticProviderDatumY[surfaceIndex] = providerDatumY;
                diagnosticAppliedReliefY[surfaceIndex] = (int) projectedProviderRelief;
                diagnosticEdgeInfluencePermille[surfaceIndex] =
                        (int) Math.round(providerInfluence * 1000.0d);
                diagnosticAuthoredMaterials[surfaceIndex] = authoredCapSurfaceMaterial;
                ResourceLocation nativeBlockKey = nativeSurface.orElseThrow().blockKey();
                ResourceLocation providerSurfaceMaterial;
                if (dominantProviderSurfaceMaterial != null
                        && ((SkyforgeMinecraftBlockPalette.STONE.equals(nativeBlockKey)
                                        && !SkyforgeMinecraftBlockPalette.STONE.equals(dominantProviderSurfaceMaterial))
                                || !stableTerrainProfileMaterial(nativeBlockKey))) {
                    // A minority stone cap or a falling provider surface (for example gravel) must
                    // inherit the stable regional surface. Falling blocks cannot be projected onto
                    // an island top, and falling back to a local authored stone cap recreates the
                    // isolated gray patch that the provider adaptation is meant to remove.
                    providerSurfaceMaterial = dominantProviderSurfaceMaterial;
                } else if (stableTerrainProfileMaterial(nativeBlockKey)) {
                    providerSurfaceMaterial = nativeBlockKey;
                } else if (stableTerrainProfileMaterial(authoredCapSurfaceMaterial)) {
                    providerSurfaceMaterial = authoredCapSurfaceMaterial;
                } else {
                    providerSurfaceMaterial = SkyforgeMinecraftBlockPalette.STONE;
                }
                int providerLayerCount = providerSurfaceLayerCountAtFootprintEdge(
                        edgeDistance,
                        PROVIDER_RELIEF_EDGE_BLEND_RADIUS_BLOCKS,
                        MinecraftNativeSurfaceSnapshot.SURFACE_STRATA_DEPTH);
                boolean providerTopMaterialSubstituted = !nativeBlockKey.equals(providerSurfaceMaterial);
                // Use the same footprint influence for shape and representation: the authored cap
                // owns the rim, then progressively more provider strata enter toward the interior.
                ResourceLocation selectedSurfaceMaterial = providerLayerCount == 0
                                && stableTerrainProfileMaterial(authoredCapSurfaceMaterial)
                        ? authoredCapSurfaceMaterial
                        : providerSurfaceMaterial;
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
                    for (int depth = 1; depth < providerLayerCount; depth++) {
                        int worldY = targetTopY - depth;
                        if (worldY <= originalTopY) {
                            break;
                        }
                        var stratum = snapshot.surfaceStratum(localX, localZ, depth);
                        if (stratum.isPresent()) {
                            ResourceLocation stratumMaterial = stratum.orElseThrow();
                            if (!stableTerrainProfileMaterial(stratumMaterial)
                                    || (SkyforgeMinecraftBlockPalette.STONE.equals(nativeBlockKey)
                                            && dominantProviderSurfaceMaterial != null
                                            && !SkyforgeMinecraftBlockPalette.STONE.equals(
                                                    dominantProviderSurfaceMaterial)
                                            && SkyforgeMinecraftBlockPalette.STONE.equals(stratumMaterial))) {
                                // Do not leave stone filler where an unsupported provider layer was
                                // rejected; that fallback recreates the same feature-shaped patch.
                                stratumMaterial = selectedSurfaceMaterial;
                            }
                            if (stableTerrainProfileMaterial(stratumMaterial)) {
                                int localY = worldY - materialization.minimumY();
                                blockKeys[linearIndex(localX, localY, localZ)] = stratumMaterial;
                            }
                        } else if (providerTopMaterialSubstituted) {
                            int localY = worldY - materialization.minimumY();
                            blockKeys[linearIndex(localX, localY, localZ)] = selectedSurfaceMaterial;
                        }
                    }
                } else if (targetTopY < originalTopY) {
                    for (int worldY = targetTopY + 1; worldY <= originalTopY; worldY++) {
                        int localY = worldY - materialization.minimumY();
                        blockKeys[linearIndex(localX, localY, localZ)] = SkyforgeMinecraftBlockPalette.AIR;
                    }
                    // Lowered columns expose the old Skyforge cap interior. Replacing only the
                    // new top leaves a broad gray stone patch around the provider-cut basin.
                    // Apply the same bounded provider strata to the newly exposed top layer.
                    for (int depth = 1; depth < providerLayerCount; depth++) {
                        int worldY = targetTopY - depth;
                        if (worldY < minimumAllowedY) {
                            break;
                        }
                        var stratum = snapshot.surfaceStratum(localX, localZ, depth);
                        if (stratum.isPresent()) {
                            ResourceLocation stratumMaterial = stratum.orElseThrow();
                            if (!stableTerrainProfileMaterial(stratumMaterial)
                                    || (SkyforgeMinecraftBlockPalette.STONE.equals(nativeBlockKey)
                                            && dominantProviderSurfaceMaterial != null
                                            && !SkyforgeMinecraftBlockPalette.STONE.equals(
                                                    dominantProviderSurfaceMaterial)
                                            && SkyforgeMinecraftBlockPalette.STONE.equals(stratumMaterial))) {
                                stratumMaterial = selectedSurfaceMaterial;
                            }
                            if (stableTerrainProfileMaterial(stratumMaterial)) {
                                int localY = worldY - materialization.minimumY();
                                blockKeys[linearIndex(localX, localY, localZ)] = stratumMaterial;
                            }
                        } else if (providerTopMaterialSubstituted) {
                            int localY = worldY - materialization.minimumY();
                            blockKeys[linearIndex(localX, localY, localZ)] = selectedSurfaceMaterial;
                        }
                    }
                }

                int targetIndex = linearIndex(localX, targetLocalY, localZ);
                if (stableTerrainProfileMaterial(selectedSurfaceMaterial)) {
                    blockKeys[targetIndex] = selectedSurfaceMaterial;
                }
                diagnosticProviderMaterials[surfaceIndex] = providerSurfaceMaterial;
                projectedSurfaceMaterials[surfaceIndex] = blockKeys[targetIndex];
                projectedSurfaceLayerCounts[surfaceIndex] = providerLayerCount;
                projectedSurfaceTopYs[surfaceIndex] = targetTopY;
                originalSurfaceTopYs[surfaceIndex] = originalTopY;
            }
        }

        // Newly raised cells deeper than the captured near-surface strata remain structural
        // stone. Repainting the entire exposed rise with its top block creates tall, repeating
        // grass/dirt walls; the cap and its first four native layers already form the visible skin.

        // A provider cut can expose original Skyforge cap voxels on a neighboring, unlowered
        // column. Re-skin only faces that were blocked before projection and became exposed
        // because an adjacent column's projected top moved below this voxel. This leaves natural
        // island cliffs and unrelated original faces untouched.
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int surfaceIndex = localX + CHUNK_WIDTH * localZ;
                int projectedTopY = projectedSurfaceTopYs[surfaceIndex];
                ResourceLocation selectedSurfaceMaterial = projectedSurfaceMaterials[surfaceIndex];
                if (projectedTopY == Integer.MIN_VALUE
                        || !stableTerrainProfileMaterial(selectedSurfaceMaterial)) {
                    continue;
                }

                int sideSkinReferenceY = Math.min(projectedTopY, originalSurfaceTopYs[surfaceIndex]);
                int lowestSkinnedY = Math.max(
                        materialization.minimumY(),
                        sideSkinReferenceY - MAX_PROVIDER_SURFACE_RISE_BLOCKS);
                for (int worldY = projectedTopY; worldY >= lowestSkinnedY; worldY--) {
                    int localY = worldY - materialization.minimumY();
                    int index = linearIndex(localX, localY, localZ);
                    if (SkyforgeMinecraftBlockPalette.AIR.equals(blockKeys[index])
                            || !hasHorizontalAirExposure(blockKeys, localX, localY, localZ)
                            || !exposedByProviderCut(
                                    originalSurfaceTopYs,
                                    projectedSurfaceTopYs,
                                    localX,
                                    localZ,
                                    worldY)) {
                        continue;
                    }

                    // Reskin only the captured native near-surface layers at the join.
                    // Below that short transition, retain the island's authored block instead
                    // of extending one provider surface block down a tall exposed wall.
                    int depth = sideSkinReferenceY - worldY;
                    if (depth < 0 || depth >= projectedSurfaceLayerCounts[surfaceIndex]) {
                        continue;
                    }
                    ResourceLocation sideSurfaceMaterial = selectedSurfaceMaterial;
                    var stratum = snapshot.surfaceStratum(localX, localZ, depth);
                    var providerSurface = snapshot.surface(localX, localZ).orElseThrow();
                    if (stratum.isPresent()) {
                        sideSurfaceMaterial = stratum.orElseThrow();
                        if (!stableTerrainProfileMaterial(sideSurfaceMaterial)
                                || (SkyforgeMinecraftBlockPalette.STONE.equals(providerSurface.blockKey())
                                        && dominantProviderSurfaceMaterial != null
                                        && !SkyforgeMinecraftBlockPalette.STONE.equals(
                                                dominantProviderSurfaceMaterial)
                                        && SkyforgeMinecraftBlockPalette.STONE.equals(sideSurfaceMaterial))) {
                            sideSurfaceMaterial = selectedSurfaceMaterial;
                        }
                    } else if (!providerSurface.blockKey().equals(selectedSurfaceMaterial)) {
                        sideSurfaceMaterial = selectedSurfaceMaterial;
                    }
                    if (stableTerrainProfileMaterial(sideSurfaceMaterial)) {
                        blockKeys[index] = sideSurfaceMaterial;
                    }
                }
            }
        }

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int surfaceIndex = localX + CHUNK_WIDTH * localZ;
                int targetTopY = projectedSurfaceTopYs[surfaceIndex];
                if (targetTopY == Integer.MIN_VALUE) continue;
                int localY = targetTopY - materialization.minimumY();
                diagnosticRealizedMaterials[surfaceIndex] =
                        blockKeys[linearIndex(localX, localY, localZ)];
            }
        }
        SkyforgeTectonicTerrainAcceptance.recordSurfaceCompositionEvidence(
                materialization.chunkPos(),
                originalSurfaceTopYs,
                diagnosticFilteredProviderY,
                diagnosticProviderDatumY,
                projectedSurfaceTopYs,
                diagnosticAppliedReliefY,
                diagnosticEdgeInfluencePermille,
                diagnosticAuthoredMaterials,
                diagnosticProviderMaterials,
                diagnosticRealizedMaterials);
        MinecraftChunkMaterialization adapted = new MinecraftChunkMaterialization(
                materialization.chunkPos(),
                materialization.minimumY(),
                materialization.height(),
                blockKeys,
                materialization.candidateVolumeReferences());
        return new TerrainProfileAdaptation(adapted, providerWriteClaims);
    }

    static double providerReliefInfluenceAtFootprintEdge(double interiorDistance, int blendRadius) {
        if (interiorDistance < 0 || blendRadius <= 0) {
            throw new IllegalArgumentException("provider relief blend distance/radius is invalid");
        }
        double position = Math.min(1.0, interiorDistance / (double) blendRadius);
        return position * position * (3.0 - 2.0 * position);
    }

    static long blendProviderReliefAtFootprintEdge(
            long projectedRelief,
            double interiorDistance,
            int blendRadius) {
        return Math.round(projectedRelief
                * providerReliefInfluenceAtFootprintEdge(interiorDistance, blendRadius));
    }

    static int providerSurfaceLayerCountAtFootprintEdge(
            double interiorDistance,
            int blendRadius,
            int maximumLayerCount) {
        if (maximumLayerCount <= 0) {
            throw new IllegalArgumentException("provider surface layer count must be positive");
        }
        return (int) Math.round(
                maximumLayerCount * providerReliefInfluenceAtFootprintEdge(interiorDistance, blendRadius));
    }

    /**
     * Exact horizontal distance-to-edge field for one Skyforge island. It is built once per
     * runtime-bound volume so the broad transition band does not repeat support queries per chunk.
     */
    private static final class ProviderReliefFootprint {
        private final int minimumX;
        private final int minimumZ;
        private final int width;
        private final int height;
        private final double[] interiorDistances;

        private ProviderReliefFootprint(
                int minimumX,
                int minimumZ,
                int width,
                int height,
                double[] interiorDistances) {
            this.minimumX = minimumX;
            this.minimumZ = minimumZ;
            this.width = width;
            this.height = height;
            this.interiorDistances = interiorDistances;
        }

        static ProviderReliefFootprint capture(
                SkyforgeNeoForge1211ChunkAdapter adapter,
                SkyIslandWorldVolumeId volumeId,
                WorldBounds bounds,
                ChunkPos targetChunk) {
            int radius = PROVIDER_RELIEF_EDGE_BLEND_RADIUS_BLOCKS;
            int supportMinimumX = ceilToInt(bounds.minimumX());
            int supportMaximumX = floorToInt(bounds.maximumX());
            int supportMinimumZ = ceilToInt(bounds.minimumZ());
            int supportMaximumZ = floorToInt(bounds.maximumZ());
            // The influence saturates at radius, so a target chunk only needs an exact support
            // window extending that far in each direction. Scanning the entire island bounds
            // before its first visible chunk made startup cost grow with province-scale area.
            int minimumX = Math.subtractExact(targetChunk.getMinBlockX(), radius);
            int minimumZ = Math.subtractExact(targetChunk.getMinBlockZ(), radius);
            int width = CHUNK_WIDTH + radius * 2;
            int height = CHUNK_WIDTH + radius * 2;
            int cellCount = Math.multiplyExact(width, height);
            boolean[] unsupported = new boolean[cellCount];
            int supportWidth = supportMaximumX - supportMinimumX + 1;
            int supportHeight = supportMaximumZ - supportMinimumZ + 1;
            for (int gridZ = 0; gridZ < height; gridZ++) {
                int worldZ = minimumZ + gridZ;
                int supportZ = worldZ - supportMinimumZ;
                for (int gridX = 0; gridX < width; gridX++) {
                    int worldX = minimumX + gridX;
                    int supportX = worldX - supportMinimumX;
                    boolean insideBounds = supportX >= 0
                            && supportX < supportWidth
                            && supportZ >= 0
                            && supportZ < supportHeight;
                    boolean supported = insideBounds
                            && adapter.integerSolidRange(volumeId, worldX, worldZ).isPresent();
                    unsupported[gridX + width * gridZ] = !supported;
                }
            }

            // The exact squared Euclidean distance transform avoids the square contours produced
            // by an unweighted 8-neighbor flood fill. Values saturate at the transition radius.
            double[] distances = euclideanDistanceToUnsupported(unsupported, width, height, radius);
            return new ProviderReliefFootprint(minimumX, minimumZ, width, height, distances);
        }

        double interiorDistance(int worldX, int worldZ) {
            int gridX = worldX - minimumX;
            int gridZ = worldZ - minimumZ;
            if (gridX < 0 || gridX >= width || gridZ < 0 || gridZ >= height) {
                return 0.0d;
            }
            return interiorDistances[gridX + width * gridZ];
        }
    }

    /**
     * Computes distances to unsupported cells using a separable exact Euclidean distance transform.
     * The bounded footprint window makes this linear-time transform small and deterministic.
     */
    static double[] euclideanDistanceToUnsupported(
            boolean[] unsupported,
            int width,
            int height,
            int maximumDistance) {
        Objects.requireNonNull(unsupported, "unsupported");
        if (width <= 0 || height <= 0 || maximumDistance <= 0
                || unsupported.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("distance-transform dimensions or radius are invalid");
        }

        final int infinity = 1_000_000_000;
        int[] horizontal = new int[unsupported.length];
        int[] input = new int[Math.max(width, height)];
        int[] output = new int[input.length];
        int[] envelope = new int[input.length];
        double[] boundaries = new double[input.length + 1];
        for (int z = 0; z < height; z++) {
            int row = z * width;
            for (int x = 0; x < width; x++) {
                input[x] = unsupported[row + x] ? 0 : infinity;
            }
            squaredDistanceTransform1d(input, output, width, envelope, boundaries);
            System.arraycopy(output, 0, horizontal, row, width);
        }

        double[] distances = new double[unsupported.length];
        int radiusSquared = Math.multiplyExact(maximumDistance, maximumDistance);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                input[z] = horizontal[x + z * width];
            }
            squaredDistanceTransform1d(input, output, height, envelope, boundaries);
            for (int z = 0; z < height; z++) {
                int index = x + z * width;
                int squaredDistance = Math.min(radiusSquared, output[z]);
                distances[index] = Math.sqrt(squaredDistance);
            }
        }
        return distances;
    }

    private static void squaredDistanceTransform1d(
            int[] input,
            int[] output,
            int length,
            int[] envelope,
            double[] boundaries) {
        int last = 0;
        envelope[0] = 0;
        boundaries[0] = Double.NEGATIVE_INFINITY;
        boundaries[1] = Double.POSITIVE_INFINITY;

        for (int q = 1; q < length; q++) {
            double intersection = parabolaIntersection(input, q, envelope[last]);
            while (intersection <= boundaries[last]) {
                last--;
                intersection = parabolaIntersection(input, q, envelope[last]);
            }
            last++;
            envelope[last] = q;
            boundaries[last] = intersection;
            boundaries[last + 1] = Double.POSITIVE_INFINITY;
        }

        last = 0;
        for (int q = 0; q < length; q++) {
            while (boundaries[last + 1] < q) {
                last++;
            }
            int delta = q - envelope[last];
            output[q] = Math.addExact(input[envelope[last]], Math.multiplyExact(delta, delta));
        }
    }

    private static double parabolaIntersection(int[] values, int q, int previous) {
        long qSquared = (long) q * q;
        long previousSquared = (long) previous * previous;
        return (values[q] + (double) qSquared - values[previous] - (double) previousSquared)
                / (2.0d * (q - previous));
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

    private static boolean exposedByProviderCut(
            int[] originalSurfaceTopYs,
            int[] projectedSurfaceTopYs,
            int localX,
            int localZ,
            int worldY) {
        return cutExposesNeighbor(originalSurfaceTopYs, projectedSurfaceTopYs, localX - 1, localZ, worldY)
                || cutExposesNeighbor(originalSurfaceTopYs, projectedSurfaceTopYs, localX + 1, localZ, worldY)
                || cutExposesNeighbor(originalSurfaceTopYs, projectedSurfaceTopYs, localX, localZ - 1, worldY)
                || cutExposesNeighbor(originalSurfaceTopYs, projectedSurfaceTopYs, localX, localZ + 1, worldY);
    }

    private static boolean cutExposesNeighbor(
            int[] originalSurfaceTopYs,
            int[] projectedSurfaceTopYs,
            int localX,
            int localZ,
            int worldY) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            return false;
        }
        int surfaceIndex = localX + CHUNK_WIDTH * localZ;
        int originalTopY = originalSurfaceTopYs[surfaceIndex];
        int projectedTopY = projectedSurfaceTopYs[surfaceIndex];
        return originalTopY >= worldY && projectedTopY < worldY;
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

    private static ResourceLocation dominantStableProviderSurfaceMaterial(
            MinecraftNativeSurfaceSnapshot snapshot) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                snapshot.surface(localX, localZ)
                        .map(MinecraftNativeSurfaceSnapshot.NativeSurface::blockKey)
                        .filter(MinecraftNativeSurfaceTopAdapter::stableTerrainProfileMaterial)
                        .ifPresent(material -> counts.merge(material, 1, Integer::sum));
            }
        }

        ResourceLocation dominant = null;
        int dominantCount = 0;
        for (var entry : counts.entrySet()) {
            int count = entry.getValue();
            if (count > dominantCount
                    || (count == dominantCount
                            && (dominant == null
                                    || entry.getKey().toString().compareTo(dominant.toString()) < 0))) {
                dominant = entry.getKey();
                dominantCount = count;
            }
        }
        return dominant;
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
