package io.github.nidaba.skyforge.neoforge1211;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Minecraft-owned index of feature placement positions below the vanilla top-surface answer.
 *
 * <p>The index combines accepted Skyforge occupancy with the live post-carver chunk state. It is
 * intentionally discrete and backend-specific: the stored positions are Minecraft block positions
 * suitable for a supplemental {@code PlacedFeature} placement stage.
 */
final class MinecraftAdditionalSurfaceIndex {
    private static final int CHUNK_WIDTH = 16;
    private static final int COLUMN_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;

    private final net.minecraft.world.level.ChunkPos chunkPos;
    private final List<List<BlockPos>> positionsByColumn;

    private MinecraftAdditionalSurfaceIndex(
            net.minecraft.world.level.ChunkPos chunkPos,
            List<List<BlockPos>> positionsByColumn) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.positionsByColumn = List.copyOf(positionsByColumn);
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

        List<List<BlockPos>> byColumn = new ArrayList<>(COLUMN_COUNT);
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
                List<BlockPos> positions = new ArrayList<>();

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
                    if (isLiveLandSurface(chunk, cursor, worldX, worldY, worldZ)) {
                        positions.add(new BlockPos(worldX, placementY, worldZ));
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
                        if (isLiveLandSurface(chunk, cursor, worldX, worldY, worldZ)) {
                            positions.add(new BlockPos(worldX, placementY, worldZ));
                            break;
                        }
                    }
                }

                positions.sort(Comparator.comparingInt(BlockPos::getY));
                byColumn.set(columnIndex(localX, localZ), List.copyOf(positions));
            }
        }

        return new MinecraftAdditionalSurfaceIndex(chunk.getPos(), byColumn);
    }

    List<BlockPos> positions(int worldX, int worldZ) {
        int localX = worldX - chunkPos.getMinBlockX();
        int localZ = worldZ - chunkPos.getMinBlockZ();
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            return List.of();
        }
        return positionsByColumn.get(columnIndex(localX, localZ));
    }

    int totalPositions() {
        return positionsByColumn.stream().mapToInt(List::size).sum();
    }

    private static boolean isLiveLandSurface(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int supportY,
            int worldZ) {
        var support = chunk.getBlockState(cursor.set(worldX, supportY, worldZ));
        if (support.isAir() || !support.getFluidState().isEmpty()) {
            return false;
        }
        var placement = chunk.getBlockState(cursor.set(worldX, supportY + 1, worldZ));
        return placement.isAir();
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
}
