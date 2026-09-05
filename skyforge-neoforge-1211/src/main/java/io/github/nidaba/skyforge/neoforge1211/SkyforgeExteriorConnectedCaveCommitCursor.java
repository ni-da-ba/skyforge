package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Resumable commit of one fully-preflighted AUTH-0030 candidate list.
 *
 * <p>Preflight remains the authority for ownership and provenance. This cursor only schedules the
 * accepted AIR writes in their original deterministic order. Each batch opens the existing exact
 * carver mutation fence, so scheduling changes neither authorization nor client-notification
 * semantics.
 */
final class SkyforgeExteriorConnectedCaveCommitCursor {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final SkyIslandWorldVolume volume;
    private final SkyforgeExteriorConnectedCavePreparationCursor.Prepared prepared;
    private final net.minecraft.world.level.ChunkPos chunkPos;

    private int nextCandidate;
    private int writeAttempts;
    private int changedBlocks;
    private long changedPositionDigest = FNV_OFFSET_BASIS;
    private boolean complete;
    private SkyforgeExteriorConnectedCaveRealizer.Result result;

    SkyforgeExteriorConnectedCaveCommitCursor(
            SkyIslandWorldVolume volume,
            SkyforgeExteriorConnectedCavePreparationCursor.Prepared prepared,
            LevelChunk chunk) {
        this.volume = Objects.requireNonNull(volume, "volume");
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(chunk, "chunk");
        this.chunkPos = chunk.getPos();

        if (prepared.unsafePositiveSamples() > 0 || prepared.candidates().isEmpty()) {
            finish();
        }
    }

    Advance advance(
            ServerLevel level,
            LevelChunk chunk,
            int maximumCandidateWrites) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        if (maximumCandidateWrites <= 0) {
            throw new IllegalArgumentException("AUTH-0030 commit cursor budget must be positive");
        }
        if (chunk.getLevel() != level || !chunk.getPos().equals(chunkPos)) {
            throw new IllegalArgumentException("AUTH-0030 commit cursor resumed against another chunk");
        }
        if (complete) {
            return new Advance(false, true, 0, 0);
        }

        int end = Math.min(
                prepared.candidates().size(),
                Math.addExact(nextCandidate, maximumCandidateWrites));
        int processed = 0;
        int changedThisBatch = 0;

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volume.id());
                var execution = SkyforgeCarverExecutionStage.open(volume.id(), chunkPos)) {
            domain.requireActive();
            execution.requireActive();

            while (nextCandidate < end) {
                BlockPos position = prepared.candidates().get(nextCandidate);
                boolean wasAir = chunk.getBlockState(position).isAir();
                chunk.setBlockState(position, Blocks.AIR.defaultBlockState(), false);
                boolean isAir = chunk.getBlockState(position).isAir();
                if (!wasAir && isAir) {
                    changedThisBatch++;
                    changedPositionDigest ^= position.asLong();
                    changedPositionDigest *= FNV_PRIME;
                }
                nextCandidate++;
                processed++;
            }

            var snapshot = execution.snapshot();
            writeAttempts = Math.addExact(writeAttempts, snapshot.writeAttempts());
            changedBlocks = Math.addExact(changedBlocks, snapshot.changedBlocks());
            if (snapshot.changedBlocks() != changedThisBatch) {
                throw new IllegalStateException(
                        "AUTH-0030 commit cursor observed inconsistent changed-block accounting: execution="
                                + snapshot.changedBlocks() + ", local=" + changedThisBatch);
            }
        }

        if (nextCandidate == prepared.candidates().size()) {
            finish();
        }
        return new Advance(processed > 0, complete, processed, changedThisBatch);
    }

    boolean complete() {
        return complete;
    }

    SkyforgeExteriorConnectedCaveRealizer.Result result() {
        if (!complete || result == null) {
            throw new IllegalStateException("AUTH-0030 commit cursor is not complete");
        }
        return result;
    }

    private void finish() {
        complete = true;
        if (prepared.unsafePositiveSamples() > 0) {
            result = new SkyforgeExteriorConnectedCaveRealizer.Result(
                    false,
                    prepared.sampledPhysicalBlocks(),
                    prepared.positiveSamples(),
                    prepared.basePositiveSamples(),
                    prepared.exposurePositiveSamples(),
                    prepared.upperExposureSamples(),
                    prepared.undersideExposureSamples(),
                    prepared.candidates().size(),
                    prepared.unsafePositiveSamples(),
                    prepared.mouthCells(),
                    0,
                    0,
                    FNV_OFFSET_BASIS,
                    prepared.provenanceDigest(),
                    prepared.firstUnsafePosition(),
                    prepared.firstMouthPosition(),
                    prepared.firstMouthSide());
            return;
        }

        result = new SkyforgeExteriorConnectedCaveRealizer.Result(
                true,
                prepared.sampledPhysicalBlocks(),
                prepared.positiveSamples(),
                prepared.basePositiveSamples(),
                prepared.exposurePositiveSamples(),
                prepared.upperExposureSamples(),
                prepared.undersideExposureSamples(),
                prepared.candidates().size(),
                0,
                prepared.mouthCells(),
                writeAttempts,
                changedBlocks,
                changedPositionDigest,
                prepared.provenanceDigest(),
                null,
                prepared.firstMouthPosition(),
                prepared.firstMouthSide());
    }

    record Advance(
            boolean worked,
            boolean complete,
            int processedCandidates,
            int changedBlocks) {}
}
