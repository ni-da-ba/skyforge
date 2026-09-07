package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandVerticalColumn;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Resumable preflight for one exact-volume AUTH-0030 chunk.
 *
 * <p>The cursor performs the same canonical AUTH-0027 -> AUTH-0030 sampling and exact ownership
 * checks as the one-shot realizer, but can stop between bounded quanta. It owns no mutations.
 * Native carving and authored AIR writes remain downstream of successful complete preflight.
 */
final class SkyforgeExteriorConnectedCavePreparationCursor {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final SkyIslandWorldVolume volume;
    private final SkyIslandRealizedExteriorConnectedCaveVolumeField realized;
    private final SkyforgeExteriorConnectedCaveSpatialIndex.Slice spatialSlice;
    private final Predicate<BlockPos> ownerSolid;
    private final Predicate<BlockPos> foreignSolid;
    private final ChunkPos chunkPos;
    private final int minimumY;
    private final int maximumY;
    private final int sampledPhysicalBlocks;
    private final double centerX;
    private final double centerZ;

    private final List<BlockPos> candidates = new ArrayList<>();

    private int currentX;
    private int currentZ;
    private SkyIslandLocalPosition currentLocal;
    private SkyIslandVerticalColumn currentColumn;
    private int currentY;
    private int currentMaximumY;
    private boolean columnActive;
    private boolean complete;
    private Prepared preparedResult;

    private int positiveSamples;
    private int basePositiveSamples;
    private int exposurePositiveSamples;
    private int upperExposureSamples;
    private int undersideExposureSamples;
    private int unsafePositiveSamples;
    private int mouthCells;
    private long provenanceDigest = FNV_OFFSET_BASIS;
    private BlockPos firstUnsafe;
    private BlockPos firstMouth;
    private SkyIslandCaveExposureSide firstMouthSide;

    SkyforgeExteriorConnectedCavePreparationCursor(
            SkyIslandWorldVolume volume,
            SkyIslandRealizedExteriorConnectedCaveVolumeField realized,
            SkyforgeExteriorConnectedCaveSpatialIndex spatialIndex,
            ChunkPos chunkPos,
            int minimumBuildY,
            int maximumBuildYExclusive,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        this.volume = Objects.requireNonNull(volume, "volume");
        this.realized = Objects.requireNonNull(realized, "realized");
        Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.ownerSolid = Objects.requireNonNull(ownerSolid, "ownerSolid");
        this.foreignSolid = Objects.requireNonNull(foreignSolid, "foreignSolid");

        this.minimumY = Math.max(
                minimumBuildY,
                (int) Math.floor(volume.bounds().minimumY()));
        this.maximumY = Math.min(
                maximumBuildYExclusive - 1,
                (int) Math.ceil(volume.bounds().maximumY()));
        int physicalYSpan = maximumY >= minimumY ? maximumY - minimumY + 1 : 0;
        this.sampledPhysicalBlocks = Math.multiplyExact(16 * 16, physicalYSpan);

        var descriptor = volume.compiledVolume().descriptor();
        this.centerX = descriptor.centerX();
        this.centerZ = descriptor.centerZ();
        double localMinimumX = chunkPos.getMinBlockX() - centerX;
        double localMaximumX = chunkPos.getMaxBlockX() - centerX;
        double localMinimumZ = chunkPos.getMinBlockZ() - centerZ;
        double localMaximumZ = chunkPos.getMaxBlockZ() - centerZ;
        this.spatialSlice = spatialIndex.slice(
                localMinimumX,
                localMaximumX,
                localMinimumZ,
                localMaximumZ);

        this.currentX = chunkPos.getMinBlockX();
        this.currentZ = chunkPos.getMinBlockZ();
        if (physicalYSpan == 0) {
            this.complete = true;
        }
    }

    Advance advance(
            int maximumColumnsStarted,
            int maximumVoxelSteps,
            int maximumCanonicalSamples) {
        if (maximumColumnsStarted <= 0
                || maximumVoxelSteps <= 0
                || maximumCanonicalSamples <= 0) {
            throw new IllegalArgumentException("AUTH-0030 preparation budgets must be positive");
        }
        if (complete) {
            return new Advance(false, true, 0, 0, 0);
        }

        int columnsStarted = 0;
        int voxelSteps = 0;
        int canonicalSamples = 0;
        boolean worked = false;

        while (!complete) {
            if (!columnActive) {
                if (columnsStarted >= maximumColumnsStarted) {
                    break;
                }
                columnsStarted++;
                worked = true;
                startCurrentColumn();
                if (complete) {
                    break;
                }
                if (!columnActive) {
                    continue;
                }
            }

            while (columnActive && currentY <= currentMaximumY) {
                if (voxelSteps >= maximumVoxelSteps) {
                    return new Advance(worked, false, columnsStarted, voxelSteps, canonicalSamples);
                }
                voxelSteps++;
                worked = true;

                double depth = currentColumn.depthFractionAt(currentY).orElse(Double.NaN);
                if (Double.isFinite(depth)) {
                    var semantic = new SkyIslandSubsurfacePosition(currentLocal, depth);
                    if (spatialSlice.mayContainPositive(semantic)) {
                        if (canonicalSamples >= maximumCanonicalSamples) {
                            return new Advance(worked, false, columnsStarted, voxelSteps - 1, canonicalSamples);
                        }
                        canonicalSamples++;
                        sampleCanonical(currentX, currentY, currentZ, semantic);
                    }
                }
                currentY++;
            }

            if (columnActive && currentY > currentMaximumY) {
                columnActive = false;
                advanceColumn();
            }
        }

        return new Advance(worked, complete, columnsStarted, voxelSteps, canonicalSamples);
    }

    boolean complete() {
        return complete;
    }

    Prepared prepared() {
        if (!complete) {
            throw new IllegalStateException("AUTH-0030 preparation is not complete");
        }
        if (preparedResult == null) {
            preparedResult = new Prepared(
                    sampledPhysicalBlocks,
                    positiveSamples,
                    basePositiveSamples,
                    exposurePositiveSamples,
                    upperExposureSamples,
                    undersideExposureSamples,
                    candidates,
                    unsafePositiveSamples,
                    mouthCells,
                    provenanceDigest,
                    firstUnsafe,
                    firstMouth,
                    firstMouthSide);
        }
        return preparedResult;
    }

    private void startCurrentColumn() {
        if (currentX > chunkPos.getMaxBlockX()) {
            complete = true;
            return;
        }

        currentLocal = new SkyIslandLocalPosition(
                currentX - centerX,
                currentZ - centerZ);
        var physicalColumn = realized.transform().columns().columnAt(currentLocal);
        if (physicalColumn.isEmpty()) {
            advanceColumn();
            return;
        }

        currentColumn = physicalColumn.orElseThrow();
        currentY = Math.max(minimumY, (int) Math.ceil(currentColumn.undersideY()));
        currentMaximumY = Math.min(maximumY, (int) Math.floor(currentColumn.upperY()));
        if (currentMaximumY < currentY) {
            advanceColumn();
            return;
        }
        columnActive = true;
    }

    private void advanceColumn() {
        columnActive = false;
        currentColumn = null;
        currentLocal = null;
        currentZ++;
        if (currentZ > chunkPos.getMaxBlockZ()) {
            currentZ = chunkPos.getMinBlockZ();
            currentX++;
        }
        if (currentX > chunkPos.getMaxBlockX()) {
            complete = true;
        }
    }

    private void sampleCanonical(
            int x,
            int y,
            int z,
            SkyIslandSubsurfacePosition semantic) {
        SkyIslandExteriorConnectedCaveVolumeSample sample =
                realized.semanticField().sample(semantic);
        if (!sample.inside()) {
            return;
        }

        BlockPos position = new BlockPos(x, y, z);
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

        boolean owner = ownerSolid.test(position);
        boolean foreign = foreignSolid.test(position);
        if (!owner || foreign) {
            unsafePositiveSamples++;
            if (firstUnsafe == null) {
                firstUnsafe = position.immutable();
            }
            return;
        }

        if (sample.sourceKind()
                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION) {
            BlockPos outward = outward(position, sample.exposureSide());
            boolean mouth = !ownerSolid.test(outward) && !foreignSolid.test(outward);
            if (mouth) {
                mouthCells++;
                if (firstMouth == null) {
                    firstMouth = position.immutable();
                    firstMouthSide = sample.exposureSide();
                }
            }
        }
        candidates.add(position.immutable());
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

    record Advance(
            boolean worked,
            boolean complete,
            int columnsStarted,
            int voxelSteps,
            int canonicalSamples) {}

    record Prepared(
            int sampledPhysicalBlocks,
            int positiveSamples,
            int basePositiveSamples,
            int exposurePositiveSamples,
            int upperExposureSamples,
            int undersideExposureSamples,
            List<BlockPos> candidates,
            int unsafePositiveSamples,
            int mouthCells,
            long provenanceDigest,
            BlockPos firstUnsafePosition,
            BlockPos firstMouthPosition,
            SkyIslandCaveExposureSide firstMouthSide) {
        Prepared {
            candidates = List.copyOf(candidates);
            if (sampledPhysicalBlocks < 0
                    || positiveSamples < 0
                    || basePositiveSamples < 0
                    || exposurePositiveSamples < 0
                    || upperExposureSamples < 0
                    || undersideExposureSamples < 0
                    || unsafePositiveSamples < 0
                    || mouthCells < 0) {
                throw new IllegalArgumentException("AUTH-0030 preparation counts must be non-negative");
            }
            if (basePositiveSamples + exposurePositiveSamples != positiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 preparation provenance counts are inconsistent");
            }
            if (upperExposureSamples + undersideExposureSamples != exposurePositiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 preparation exposure counts are inconsistent");
            }
            if (candidates.size() + unsafePositiveSamples != positiveSamples) {
                throw new IllegalArgumentException("AUTH-0030 preparation ownership counts are inconsistent");
            }
        }
    }
}
