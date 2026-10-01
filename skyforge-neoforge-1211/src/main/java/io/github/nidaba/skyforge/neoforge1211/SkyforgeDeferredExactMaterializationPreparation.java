package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * In-memory resumable preparation of one deferred exact-volume chunk projection.
 *
 * <p>Each advance delegates a bounded contiguous run of columns to the authoritative exact-volume
 * adapter while retaining one AIR-initialized projection buffer and the next local-Z -> local-X
 * column cursor. No mutable Minecraft world state is read or written here. The preparation is
 * intentionally not persisted: after restart it is safe to rebuild from column zero before the
 * persisted writer cursor resumes.
 */
final class SkyforgeDeferredExactMaterializationPreparation {
    private static final int CHUNK_AREA = 16 * 16;

    private final SkyIslandWorldVolumeId volumeId;
    private final ChunkPos chunkPos;
    private final int minimumY;
    private final int height;
    private final ResourceLocation[] blockKeys;
    private int nextColumn;
    private long classifiedVoxels;
    private long provenAirSkippedVoxels;
    private long cumulativeWorkNanos;

    SkyforgeDeferredExactMaterializationPreparation(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int minimumY,
            int height) {
        this.volumeId = Objects.requireNonNull(volumeId, "volumeId");
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        this.minimumY = minimumY;
        this.height = height;
        this.blockKeys = new ResourceLocation[Math.multiplyExact(CHUNK_AREA, height)];
        Arrays.fill(blockKeys, SkyforgeMinecraftBlockPalette.AIR);
    }

    Advance advance(ExactColumnMaterializer materializer, int maximumColumns) {
        Objects.requireNonNull(materializer, "materializer");
        if (maximumColumns <= 0) {
            throw new IllegalArgumentException("maximumColumns must be positive");
        }
        if (complete()) {
            throw new IllegalStateException("deferred materialization preparation is already complete");
        }

        int firstColumn = nextColumn;
        var columnAdvance = Objects.requireNonNull(
                materializer.materialize(
                        volumeId,
                        chunkPos,
                        minimumY,
                        height,
                        firstColumn,
                        maximumColumns,
                        blockKeys),
                "materialized column advance");
        verifyAdvance(columnAdvance, firstColumn, maximumColumns);

        nextColumn = columnAdvance.nextColumn();
        classifiedVoxels = Math.addExact(classifiedVoxels, columnAdvance.classifiedVoxels());
        provenAirSkippedVoxels = Math.addExact(
                provenAirSkippedVoxels,
                columnAdvance.provenAirSkippedVoxels());

        MinecraftChunkMaterialization completed = null;
        if (complete()) {
            int voxelCount = Math.multiplyExact(CHUNK_AREA, height);
            if (Math.addExact(classifiedVoxels, provenAirSkippedVoxels) != voxelCount) {
                throw new IllegalStateException(
                        "bounded exact-volume materialization accounting does not cover the requested chunk interval");
            }
            SkyforgeRuntimePerformanceMetrics.recordSample(
                    "terrain.exactMaterialization.classifiedVoxels",
                    classifiedVoxels);
            SkyforgeRuntimePerformanceMetrics.recordSample(
                    "terrain.exactMaterialization.provenAirSkippedVoxels",
                    provenAirSkippedVoxels);
            completed = new MinecraftChunkMaterialization(chunkPos, minimumY, height, blockKeys, 1);
        }
        return new Advance(
                columnAdvance.preparedColumns(),
                nextColumn,
                Optional.ofNullable(completed));
    }

    void recordWorkNanos(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("elapsedNanos must be nonnegative");
        }
        cumulativeWorkNanos = Math.addExact(cumulativeWorkNanos, elapsedNanos);
    }

    SkyIslandWorldVolumeId volumeId() {
        return volumeId;
    }

    ChunkPos chunkPos() {
        return chunkPos;
    }

    int minimumY() {
        return minimumY;
    }

    int nextColumn() {
        return nextColumn;
    }

    int height() {
        return height;
    }

    long classifiedVoxels() {
        return classifiedVoxels;
    }

    long provenAirSkippedVoxels() {
        return provenAirSkippedVoxels;
    }

    long cumulativeWorkNanos() {
        return cumulativeWorkNanos;
    }

    boolean complete() {
        return nextColumn == CHUNK_AREA;
    }

    private void verifyAdvance(
            SkyforgeNeoForge1211ChunkAdapter.ExactColumnAdvance advance,
            int expectedFirstColumn,
            int maximumColumns) {
        if (advance.firstColumn() != expectedFirstColumn) {
            throw new IllegalStateException("deferred materialization column cursor changed in flight");
        }
        int expectedPreparedColumns = Math.min(maximumColumns, CHUNK_AREA - expectedFirstColumn);
        if (advance.preparedColumns() != expectedPreparedColumns) {
            throw new IllegalStateException("deferred materialization prepared an unexpected column count");
        }
        if (advance.complete() != (advance.nextColumn() == CHUNK_AREA)) {
            throw new IllegalStateException("deferred materialization column completion changed in flight");
        }
    }

    @FunctionalInterface
    interface ExactColumnMaterializer {
        SkyforgeNeoForge1211ChunkAdapter.ExactColumnAdvance materialize(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos,
                int minimumY,
                int height,
                int firstColumn,
                int maximumColumns,
                ResourceLocation[] blockKeys);
    }

    record Advance(
            int preparedColumns,
            int nextColumn,
            Optional<MinecraftChunkMaterialization> completedMaterialization) {
        Advance {
            if (preparedColumns <= 0) {
                throw new IllegalArgumentException("preparedColumns must be positive");
            }
            if (nextColumn <= 0 || nextColumn > CHUNK_AREA) {
                throw new IllegalArgumentException("nextColumn must remain inside the chunk column interval");
            }
            Objects.requireNonNull(completedMaterialization, "completedMaterialization");
            if (completedMaterialization.isPresent() != (nextColumn == CHUNK_AREA)) {
                throw new IllegalArgumentException("completed materialization disagrees with column cursor");
            }
        }

        boolean complete() {
            return completedMaterialization.isPresent();
        }
    }
}
