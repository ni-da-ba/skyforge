package io.github.nidaba.skyforge.neoforge1211;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Minecraft-owned index of feature placement positions below the vanilla top-surface answer.
 *
 * <p>SF-IMP-0038 established reachability. SF-IMP-0039 retains that dry-land view while attaching
 * Minecraft-owned suitability classifications to each lower live surface. No biome or climate
 * semantics leave the adapter.
 */
final class MinecraftAdditionalSurfaceIndex {
    private static final int CHUNK_WIDTH = 16;
    private static final int COLUMN_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final int OPEN_HEADROOM_BLOCKS = 8;
    private static final int OPEN_SUPPORT_THICKNESS_BLOCKS = 3;

    private final net.minecraft.world.level.ChunkPos chunkPos;
    private final List<List<SurfaceCandidate>> candidatesByColumn;

    private MinecraftAdditionalSurfaceIndex(
            net.minecraft.world.level.ChunkPos chunkPos,
            List<List<SurfaceCandidate>> candidatesByColumn) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.candidatesByColumn = List.copyOf(candidatesByColumn);
    }

    static MinecraftAdditionalSurfaceIndex from(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(materialization, "materialization");
        if (!chunk.getPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("chunk position differs from materialization ownership");
        }
        if (chunk.getMinBuildHeight() != materialization.minimumY()
                || chunk.getHeight() != materialization.height()) {
            throw new IllegalArgumentException("materialization must cover the full live chunk height");
        }

        List<List<SurfaceCandidate>> byColumn = new ArrayList<>(COLUMN_COUNT);
        for (int i = 0; i < COLUMN_COUNT; i++) {
            byColumn.add(List.of());
        }

        int minimumY = materialization.minimumY();
        int maximumY = minimumY + materialization.height() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int vanillaTopPlacementY = chunk.getHeight(
                        Heightmap.Types.WORLD_SURFACE_WG,
                        localX,
                        localZ);

                int lowestSkyforgeSolidY = Integer.MAX_VALUE;
                List<SurfaceCandidate> candidates = new ArrayList<>();

                for (int worldY = minimumY; worldY < maximumY; worldY++) {
                    if (!isSkyforgeSolid(materialization, localX, worldY, localZ)) {
                        continue;
                    }
                    lowestSkyforgeSolidY = Math.min(lowestSkyforgeSolidY, worldY);
                    if (isSkyforgeSolid(materialization, localX, worldY + 1, localZ)) {
                        continue;
                    }

                    int placementY = worldY + 1;
                    if (placementY >= vanillaTopPlacementY) {
                        continue;
                    }
                    Set<MinecraftSurfaceSuitability> suitability = classifySurface(
                            chunk,
                            cursor,
                            worldX,
                            worldY,
                            worldZ);
                    if (!suitability.isEmpty()) {
                        candidates.add(new SurfaceCandidate(
                                new BlockPos(worldX, placementY, worldZ), suitability));
                    }
                }

                // Only supplement native ground in columns that actually contain Skyforge. Without
                // a floating solid, vanilla's ordinary heightmap placement already owns the column.
                if (lowestSkyforgeSolidY != Integer.MAX_VALUE) {
                    for (int worldY = lowestSkyforgeSolidY - 1; worldY >= minimumY; worldY--) {
                        if (isSkyforgeSolid(materialization, localX, worldY, localZ)) {
                            continue;
                        }
                        int placementY = worldY + 1;
                        if (placementY >= vanillaTopPlacementY) {
                            continue;
                        }
                        Set<MinecraftSurfaceSuitability> suitability = classifySurface(
                                chunk,
                                cursor,
                                worldX,
                                worldY,
                                worldZ);
                        if (!suitability.isEmpty()) {
                            candidates.add(new SurfaceCandidate(
                                    new BlockPos(worldX, placementY, worldZ), suitability));
                            break;
                        }
                    }
                }

                candidates.sort(Comparator.comparingInt(candidate -> candidate.position().getY()));
                byColumn.set(columnIndex(localX, localZ), List.copyOf(candidates));
            }
        }

        return new MinecraftAdditionalSurfaceIndex(chunk.getPos(), byColumn);
    }

    /** Accepted SF-IMP-0038 view: reachable lower dry-land positions only. */
    List<BlockPos> positions(int worldX, int worldZ) {
        return positions(worldX, worldZ, MinecraftSurfaceSuitability.DRY_LAND);
    }

    List<BlockPos> positions(
            int worldX,
            int worldZ,
            MinecraftSurfaceSuitability suitability) {
        Objects.requireNonNull(suitability, "suitability");
        int localX = worldX - chunkPos.getMinBlockX();
        int localZ = worldZ - chunkPos.getMinBlockZ();
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            return List.of();
        }
        return candidatesByColumn.get(columnIndex(localX, localZ)).stream()
                .filter(candidate -> candidate.suitabilities().contains(suitability))
                .map(SurfaceCandidate::position)
                .toList();
    }

    /** Accepted SF-IMP-0038 dry-land count. */
    int totalPositions() {
        return totalPositions(MinecraftSurfaceSuitability.DRY_LAND);
    }

    int totalPositions(MinecraftSurfaceSuitability suitability) {
        Objects.requireNonNull(suitability, "suitability");
        return candidatesByColumn.stream()
                .flatMap(List::stream)
                .mapToInt(candidate -> candidate.suitabilities().contains(suitability) ? 1 : 0)
                .sum();
    }

    private static Set<MinecraftSurfaceSuitability> classifySurface(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int supportY,
            int worldZ) {
        var support = chunk.getBlockState(cursor.set(worldX, supportY, worldZ));
        if (support.isAir() || !support.getFluidState().isEmpty()) {
            return Set.of();
        }

        int placementY = supportY + 1;
        var placement = chunk.getBlockState(cursor.set(worldX, placementY, worldZ));
        EnumSet<MinecraftSurfaceSuitability> suitability =
                EnumSet.noneOf(MinecraftSurfaceSuitability.class);

        if (placement.isAir()) {
            suitability.add(MinecraftSurfaceSuitability.DRY_LAND);
            if (hasVerticalAirClearance(chunk, cursor, worldX, placementY, worldZ)
                    && hasSupportThickness(chunk, cursor, worldX, supportY, worldZ)) {
                suitability.add(MinecraftSurfaceSuitability.DRY_OPEN);
            }
        } else if (placement.is(Blocks.WATER)) {
            suitability.add(MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR);
        }

        return Set.copyOf(suitability);
    }

    private static boolean hasVerticalAirClearance(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int placementY,
            int worldZ) {
        int maximumExclusiveY = chunk.getMinBuildHeight() + chunk.getHeight();
        if (placementY + OPEN_HEADROOM_BLOCKS > maximumExclusiveY) {
            return false;
        }
        for (int offset = 0; offset < OPEN_HEADROOM_BLOCKS; offset++) {
            if (!chunk.getBlockState(cursor.set(worldX, placementY + offset, worldZ)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSupportThickness(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int supportY,
            int worldZ) {
        if (supportY - (OPEN_SUPPORT_THICKNESS_BLOCKS - 1) < chunk.getMinBuildHeight()) {
            return false;
        }
        for (int offset = 0; offset < OPEN_SUPPORT_THICKNESS_BLOCKS; offset++) {
            var state = chunk.getBlockState(cursor.set(worldX, supportY - offset, worldZ));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSkyforgeSolid(
            MinecraftChunkMaterialization materialization,
            int localX,
            int worldY,
            int localZ) {
        ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
        return !SkyforgeMinecraftBlockPalette.AIR.equals(key);
    }

    private static int columnIndex(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }

    private record SurfaceCandidate(
            BlockPos position,
            Set<MinecraftSurfaceSuitability> suitabilities) {
        private SurfaceCandidate {
            Objects.requireNonNull(position, "position");
            suitabilities = Set.copyOf(Objects.requireNonNull(suitabilities, "suitabilities"));
        }
    }
}
