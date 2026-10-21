package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * First Minecraft realization seam for backend-neutral AUTH-0026/AUTH-0027 cave authorship.
 *
 * <p>The adapter does not understand cave graphs, chamber geometry, passage geometry, exposure
 * intent, or Minecraft-native carvers. It translates one already-authoritative compiled physical
 * volume into AUTH-0027 semantic depth, queries AUTH-0026 occupancy, preflights every positive
 * sample against the existing exact-volume owner fence, then writes AIR through
 * {@link SkyforgeCarverExecutionStage}.
 *
 * <p>The chunk is an explicit already-loaded target. No neighbor chunk is requested or forced.
 */
final class SkyforgeAuthoredCaveRealizer {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SkyforgeAuthoredCaveRealizer() {}

    static Result realize(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandCaveVolumeField caveField,
            LevelChunk chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(caveField, "caveField");
        Objects.requireNonNull(chunk, "chunk");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("authored cave target chunk belongs to another level");
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("authored cave realization requires an active Skyforge terrain binding");
        }

        var compiled = volume.compiledVolume();
        var descriptor = compiled.descriptor();
        var realizedField = new SkyIslandRealizedCaveVolumeField(
                caveField,
                new SkyIslandCompiledVolumeColumnField(compiled));

        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.floor(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.ceil(volume.bounds().maximumY()));

        int sampledPhysicalBlocks = 0;
        int positiveAuthoredSamples = 0;
        int unsafePositiveSamples = 0;
        long authoredProvenanceDigest = FNV_OFFSET_BASIS;
        BlockPos firstUnsafe = null;
        List<Candidate> candidates = new ArrayList<>();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            double localX = x - descriptor.centerX();
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                double localZ = z - descriptor.centerZ();
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(localX, localZ);
                for (int y = minimumY; y <= maximumY; y++) {
                    sampledPhysicalBlocks++;
                    BlockPos position = new BlockPos(x, y, z);
                    SkyIslandCaveVolumeSample sample = realizedField.sample(
                            new SkyIslandRealizedSubsurfacePosition(local, y));
                    if (!sample.inside()) {
                        continue;
                    }

                    positiveAuthoredSamples++;
                    authoredProvenanceDigest = mix(authoredProvenanceDigest, position.asLong());
                    authoredProvenanceDigest = mix(authoredProvenanceDigest, sample.systemId());
                    authoredProvenanceDigest = mix(
                            authoredProvenanceDigest,
                            sample.primitiveKind().ordinal());
                    authoredProvenanceDigest = mix(authoredProvenanceDigest, sample.primitiveId());

                    boolean ownerSolid = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volume.id(), x, y, z)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge runtime binding disappeared during authored cave preflight"));
                    boolean foreignSolid = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                                    volume.id(), x, y, z)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge runtime binding disappeared during authored cave preflight"));
                    if (!ownerSolid || foreignSolid) {
                        unsafePositiveSamples++;
                        if (firstUnsafe == null) {
                            firstUnsafe = position.immutable();
                        }
                        continue;
                    }
                    candidates.add(new Candidate(position.immutable(), sample));
                }
            }
        }

        // Sealed first-generation realization is atomic per target chunk. A positive authored cave
        // request that reaches exterior/foreign terrain vetoes the entire target before AIR writes.
        if (unsafePositiveSamples > 0) {
            return new Result(
                    false,
                    sampledPhysicalBlocks,
                    positiveAuthoredSamples,
                    candidates.size(),
                    unsafePositiveSamples,
                    0,
                    0,
                    FNV_OFFSET_BASIS,
                    authoredProvenanceDigest,
                    firstUnsafe,
                    null,
                    -1,
                    SkyIslandCaveVolumeSample.PrimitiveKind.NONE,
                    -1,
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE);
        }

        SkyforgeCarverExecutionStage.Snapshot writeSnapshot;
        try (var domain = SkyforgeGenerationDomainStage.openIsland(volume.id());
                var execution = SkyforgeCarverExecutionStage.open(volume.id(), chunk.getPos())) {
            domain.requireActive();
            execution.requireActive();
            for (Candidate candidate : candidates) {
                chunk.setBlockState(candidate.position(), Blocks.AIR.defaultBlockState(), false);
            }
            writeSnapshot = execution.snapshot();
        }

        Candidate sampleCandidate = candidates.isEmpty() ? null : candidates.getFirst();
        return new Result(
                true,
                sampledPhysicalBlocks,
                positiveAuthoredSamples,
                candidates.size(),
                0,
                writeSnapshot.writeAttempts(),
                writeSnapshot.changedBlocks(),
                writeSnapshot.changedPositionDigest(),
                authoredProvenanceDigest,
                null,
                sampleCandidate == null ? null : sampleCandidate.position(),
                sampleCandidate == null ? -1 : sampleCandidate.sample().systemId(),
                sampleCandidate == null
                        ? SkyIslandCaveVolumeSample.PrimitiveKind.NONE
                        : sampleCandidate.sample().primitiveKind(),
                sampleCandidate == null ? -1 : sampleCandidate.sample().primitiveId(),
                writeSnapshot.minimumChangedY(),
                writeSnapshot.maximumChangedY());
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    record Result(
            boolean sealedAndAccepted,
            int sampledPhysicalBlocks,
            int positiveAuthoredSamples,
            int ownerAuthorizedSamples,
            int unsafePositiveSamples,
            int writeAttempts,
            int changedBlocks,
            long changedPositionDigest,
            long authoredProvenanceDigest,
            BlockPos firstUnsafePosition,
            BlockPos sampleAuthoredPosition,
            int sampleSystemId,
            SkyIslandCaveVolumeSample.PrimitiveKind samplePrimitiveKind,
            int samplePrimitiveId,
            int minimumChangedY,
            int maximumChangedY) {
        Result {
            Objects.requireNonNull(samplePrimitiveKind, "samplePrimitiveKind");
            if (sampledPhysicalBlocks < 0
                    || positiveAuthoredSamples < 0
                    || ownerAuthorizedSamples < 0
                    || unsafePositiveSamples < 0
                    || writeAttempts < 0
                    || changedBlocks < 0) {
                throw new IllegalArgumentException("authored cave realization counts must be non-negative");
            }
            if (ownerAuthorizedSamples + unsafePositiveSamples != positiveAuthoredSamples) {
                throw new IllegalArgumentException("authored cave realization evidence counts are inconsistent");
            }
            if (!sealedAndAccepted && changedBlocks != 0) {
                throw new IllegalArgumentException("rejected authored cave realization cannot change blocks");
            }
        }
    }

    private record Candidate(
            BlockPos position,
            SkyIslandCaveVolumeSample sample) {
        private Candidate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(sample, "sample");
        }
    }
}
