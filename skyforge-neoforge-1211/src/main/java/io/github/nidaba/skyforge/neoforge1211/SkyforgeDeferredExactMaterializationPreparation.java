package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * In-memory resumable preparation of one deferred exact-volume chunk projection.
 *
 * <p>Each advance delegates one bounded vertical slab to the already-authoritative exact-volume
 * materializer and copies the slab's Y-major block-key layers directly into their final offsets.
 * No mutable Minecraft world state is read or written here. The preparation is intentionally not
 * persisted: after restart it is safe to rebuild from the first slab before the persisted writer
 * cursor resumes.
 */
final class SkyforgeDeferredExactMaterializationPreparation {
    private static final int CHUNK_AREA = 16 * 16;

    private final SkyIslandWorldVolumeId volumeId;
    private final ChunkPos chunkPos;
    private final int minimumY;
    private final int height;
    private final ResourceLocation[] blockKeys;
    private int preparedHeight;
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
    }

    Advance advance(ExactSliceMaterializer materializer, int maximumSliceHeight) {
        Objects.requireNonNull(materializer, "materializer");
        if (maximumSliceHeight <= 0) {
            throw new IllegalArgumentException("maximumSliceHeight must be positive");
        }
        if (complete()) {
            throw new IllegalStateException("deferred materialization preparation is already complete");
        }

        int sliceHeight = Math.min(maximumSliceHeight, height - preparedHeight);
        int sliceMinimumY = Math.addExact(minimumY, preparedHeight);
        MinecraftChunkMaterialization slice = Objects.requireNonNull(
                materializer.materialize(volumeId, chunkPos, sliceMinimumY, sliceHeight),
                "materialized slice");
        verifySlice(slice, sliceMinimumY, sliceHeight);

        ResourceLocation[] sliceKeys = slice.blockKeys();
        int targetOffset = Math.multiplyExact(preparedHeight, CHUNK_AREA);
        System.arraycopy(sliceKeys, 0, blockKeys, targetOffset, sliceKeys.length);
        preparedHeight = Math.addExact(preparedHeight, sliceHeight);

        MinecraftChunkMaterialization completed = complete()
                ? new MinecraftChunkMaterialization(chunkPos, minimumY, height, blockKeys, 1)
                : null;
        return new Advance(sliceHeight, Optional.ofNullable(completed));
    }

    void recordWorkNanos(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("elapsedNanos must be nonnegative");
        }
        cumulativeWorkNanos = Math.addExact(cumulativeWorkNanos, elapsedNanos);
    }

    int preparedHeight() {
        return preparedHeight;
    }

    int height() {
        return height;
    }

    long cumulativeWorkNanos() {
        return cumulativeWorkNanos;
    }

    boolean complete() {
        return preparedHeight == height;
    }

    private void verifySlice(
            MinecraftChunkMaterialization slice,
            int expectedMinimumY,
            int expectedHeight) {
        if (!chunkPos.equals(slice.chunkPos())) {
            throw new IllegalStateException("deferred materialization slice changed chunk identity");
        }
        if (slice.minimumY() != expectedMinimumY || slice.height() != expectedHeight) {
            throw new IllegalStateException("deferred materialization slice changed requested vertical interval");
        }
        if (slice.candidateVolumeReferences() != 1) {
            throw new IllegalStateException("deferred exact-volume slice changed candidate-volume identity");
        }
    }

    @FunctionalInterface
    interface ExactSliceMaterializer {
        MinecraftChunkMaterialization materialize(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos,
                int minimumY,
                int height);
    }

    record Advance(
            int preparedHeight,
            Optional<MinecraftChunkMaterialization> completedMaterialization) {
        Advance {
            if (preparedHeight <= 0) {
                throw new IllegalArgumentException("preparedHeight must be positive");
            }
            Objects.requireNonNull(completedMaterialization, "completedMaterialization");
        }

        boolean complete() {
            return completedMaterialization.isPresent();
        }
    }
}
