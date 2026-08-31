package io.github.nidaba.skyforge.neoforge1211;

/** Immutable summary of one concrete ChunkAccess write operation. */
public record MinecraftChunkWriteResult(
        int assignedBlockCount,
        int solidBlockCount,
        int candidateVolumeReferences) {

    public MinecraftChunkWriteResult {
        if (assignedBlockCount < 0) {
            throw new IllegalArgumentException("assignedBlockCount must be non-negative");
        }
        if (solidBlockCount < 0 || solidBlockCount > assignedBlockCount) {
            throw new IllegalArgumentException("solidBlockCount must be within assigned block count");
        }
        if (candidateVolumeReferences < 0) {
            throw new IllegalArgumentException("candidateVolumeReferences must be non-negative");
        }
    }
}
