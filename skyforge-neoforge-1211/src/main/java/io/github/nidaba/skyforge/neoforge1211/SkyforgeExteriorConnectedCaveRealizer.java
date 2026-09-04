package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
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
 * Minecraft realization seam for merged AUTH-0030 exterior-connected cave volume.
 *
 * <p>The backend does not reconstruct AUTH-0029 corridor geometry. It samples the accepted
 * AUTH-0030 union through AUTH-0027, requires every positive physical cell to be exact owner-solid,
 * and writes AIR only through the existing carver execution fence.
 *
 * <p>An exposure mouth cell is recognized only when an EXPOSURE_CONNECTION-positive owner cell has
 * an outward neighbor on its accepted side that is neither owner-solid nor foreign-solid. The
 * outward neighbor is never modified by this realizer.
 */
final class SkyforgeExteriorConnectedCaveRealizer {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SkyforgeExteriorConnectedCaveRealizer() {}

    static Result realize(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField caveField,
            LevelChunk chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(caveField, "caveField");
        Objects.requireNonNull(chunk, "chunk");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("exterior-connected cave target chunk belongs to another level");
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "exterior-connected cave realization requires an active Skyforge terrain binding");
        }

        var compiled = volume.compiledVolume();
        var descriptor = compiled.descriptor();
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                caveField,
                new SkyIslandCompiledVolumeColumnField(compiled));

        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.floor(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.ceil(volume.bounds().maximumY()));

        int sampledPhysicalBlocks = 0;
        int positiveSamples = 0;
        int basePositiveSamples = 0;
        int exposurePositiveSamples = 0;
        int upperExposureSamples = 0;
        int undersideExposureSamples = 0;
        int unsafePositiveSamples = 0;
        int mouthCells = 0;
        long provenanceDigest = FNV_OFFSET_BASIS;
        BlockPos firstUnsafe = null;
        BlockPos firstMouth = null;
        SkyIslandCaveExposureSide firstMouthSide = null;
        List<Candidate> candidates = new ArrayList<>();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            double localX = x - descriptor.centerX();
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                double localZ = z - descriptor.centerZ();
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(localX, localZ);
                for (int y = minimumY; y <= maximumY; y++) {
                    sampledPhysicalBlocks++;
                    BlockPos position = new BlockPos(x, y, z);
                    SkyIslandExteriorConnectedCaveVolumeSample sample = realized.sample(
                            new SkyIslandRealizedSubsurfacePosition(local, y));
                    if (!sample.inside()) {
                        continue;
                    }

                    positiveSamples++;
                    switch (sample.sourceKind()) {
                        case BASE_CAVE -> basePositiveSamples++;
                        case EXPOSURE_CONNECTION -> {
                            exposurePositiveSamples++;
                            if (sample.exposureSide() == SkyIslandCaveExposureSide.UPPER_SURFACE) {
                                upperExposureSamples++;
                            } else if (sample.exposureSide() == SkyIslandCaveExposureSide.UNDERSIDE) {
                                undersideExposureSamples++;
                            }
                        }
                        case NONE -> throw new IllegalStateException(
                                "positive AUTH-0030 sample cannot have NONE provenance");
                    }

                    provenanceDigest = mix(provenanceDigest, position.asLong());
                    provenanceDigest = mix(provenanceDigest, sample.sourceKind().ordinal());
                    provenanceDigest = mix(provenanceDigest, sample.systemId());
                    provenanceDigest = mix(provenanceDigest, sample.sourcePrimitiveKind().ordinal());
                    provenanceDigest = mix(provenanceDigest, sample.sourcePrimitiveId());
                    provenanceDigest = mix(
                            provenanceDigest,
                            sample.exposureSide() == null ? -1L : sample.exposureSide().ordinal());

                    boolean ownerSolid = ownerSolid(volume, position);
                    boolean foreignSolid = foreignSolid(volume, position);
                    if (!ownerSolid || foreignSolid) {
                        unsafePositiveSamples++;
                        if (firstUnsafe == null) {
                            firstUnsafe = position.immutable();
                        }
                        continue;
                    }

                    boolean mouth = false;
                    if (sample.sourceKind()
                            == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION) {
                        BlockPos outward = outward(position, sample.exposureSide());
                        mouth = !ownerSolid(volume, outward) && !foreignSolid(volume, outward);
                        if (mouth) {
                            mouthCells++;
                            if (firstMouth == null) {
                                firstMouth = position.immutable();
                                firstMouthSide = sample.exposureSide();
                            }
                        }
                    }
                    candidates.add(new Candidate(position.immutable(), sample, mouth));
                }
            }
        }

        if (unsafePositiveSamples > 0) {
            return new Result(
                    false,
                    sampledPhysicalBlocks,
                    positiveSamples,
                    basePositiveSamples,
                    exposurePositiveSamples,
                    upperExposureSamples,
                    undersideExposureSamples,
                    candidates.size(),
                    unsafePositiveSamples,
                    mouthCells,
                    0,
                    0,
                    FNV_OFFSET_BASIS,
                    provenanceDigest,
                    firstUnsafe,
                    firstMouth,
                    firstMouthSide);
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

        return new Result(
                true,
                sampledPhysicalBlocks,
                positiveSamples,
                basePositiveSamples,
                exposurePositiveSamples,
                upperExposureSamples,
                undersideExposureSamples,
                candidates.size(),
                0,
                mouthCells,
                writeSnapshot.writeAttempts(),
                writeSnapshot.changedBlocks(),
                writeSnapshot.changedPositionDigest(),
                provenanceDigest,
                null,
                firstMouth,
                firstMouthSide);
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolume volume,
            BlockPos position) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        volume.id(),
                        position.getX(),
                        position.getY(),
                        position.getZ())
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge terrain binding disappeared during AUTH-0030 realization"));
    }

    private static boolean foreignSolid(
            SkyIslandWorldVolume volume,
            BlockPos position) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                        volume.id(),
                        position.getX(),
                        position.getY(),
                        position.getZ())
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge terrain binding disappeared during AUTH-0030 realization"));
    }

    private static BlockPos outward(
            BlockPos position,
            SkyIslandCaveExposureSide side) {
        Objects.requireNonNull(side, "side");
        return side == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? position.above()
                : position.below();
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
            boolean accepted,
            int sampledPhysicalBlocks,
            int positiveSamples,
            int basePositiveSamples,
            int exposurePositiveSamples,
            int upperExposureSamples,
            int undersideExposureSamples,
            int ownerAuthorizedSamples,
            int unsafePositiveSamples,
            int mouthCells,
            int writeAttempts,
            int changedBlocks,
            long changedPositionDigest,
            long provenanceDigest,
            BlockPos firstUnsafePosition,
            BlockPos firstMouthPosition,
            SkyIslandCaveExposureSide firstMouthSide) {
        Result {
            if (sampledPhysicalBlocks < 0
                    || positiveSamples < 0
                    || basePositiveSamples < 0
                    || exposurePositiveSamples < 0
                    || upperExposureSamples < 0
                    || undersideExposureSamples < 0
                    || ownerAuthorizedSamples < 0
                    || unsafePositiveSamples < 0
                    || mouthCells < 0
                    || writeAttempts < 0
                    || changedBlocks < 0) {
                throw new IllegalArgumentException("AUTH-0030 realization counts must be non-negative");
            }
            if (basePositiveSamples + exposurePositiveSamples != positiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 provenance counts are inconsistent");
            }
            if (upperExposureSamples + undersideExposureSamples != exposurePositiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 exposure-side counts are inconsistent");
            }
            if (ownerAuthorizedSamples + unsafePositiveSamples != positiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 ownership counts are inconsistent");
            }
            if (mouthCells == 0 && firstMouthPosition != null) {
                throw new IllegalArgumentException("AUTH-0030 first mouth cannot exist without mouth cells");
            }
            if (firstMouthPosition == null && firstMouthSide != null) {
                throw new IllegalArgumentException("AUTH-0030 mouth side requires a mouth position");
            }
            if (!accepted && changedBlocks != 0) {
                throw new IllegalArgumentException("rejected AUTH-0030 realization cannot mutate blocks");
            }
        }
    }

    private record Candidate(
            BlockPos position,
            SkyIslandExteriorConnectedCaveVolumeSample sample,
            boolean mouth) {
        Candidate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(sample, "sample");
        }
    }
}
