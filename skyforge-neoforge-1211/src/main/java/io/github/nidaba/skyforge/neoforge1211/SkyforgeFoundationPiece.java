package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Serializable fill-only foundation attached to a vanilla structure start by Skyforge.
 *
 * <p>The piece occupies only the bounded vertical interval below the original structure floor. It
 * never removes or replaces solid terrain. Each chunk-local column may fill only above a solid
 * sample owned by the exact Skyforge volume recorded when the accommodation was admitted.
 */
final class SkyforgeFoundationPiece extends StructurePiece {
    private static final String ROOT_SEED_TAG = "SkyforgeRootSeed";
    private static final String GROUP_IDENTIFIER_TAG = "SkyforgeGroup";
    private static final String GROUP_ORDINAL_TAG = "SkyforgeGroupOrdinal";
    private static final String MEMBER_ORDINAL_TAG = "SkyforgeMemberOrdinal";
    private static final String GEOMETRY_SEED_TAG = "SkyforgeGeometrySeed";
    private static final String FOUNDATION_TOP_Y_TAG = "SkyforgeFoundationTopY";
    private static final String MAXIMUM_FILL_DEPTH_TAG = "SkyforgeMaximumFillDepth";

    private final SkyIslandWorldVolumeId supportingVolumeId;
    private final int foundationTopY;
    private final int maximumFillDepth;

    SkyforgeFoundationPiece(
            BoundingBox structureBounds,
            SkyIslandWorldVolumeId supportingVolumeId,
            int maximumFillDepth) {
        super(
                SkyforgeNeoForge1211StructurePieces.FOUNDATION.get(),
                0,
                foundationBounds(structureBounds, maximumFillDepth));
        this.supportingVolumeId = Objects.requireNonNull(supportingVolumeId, "supportingVolumeId");
        if (maximumFillDepth <= 0) {
            throw new IllegalArgumentException("maximumFillDepth must be positive");
        }
        this.foundationTopY = Math.subtractExact(structureBounds.minY(), 1);
        this.maximumFillDepth = maximumFillDepth;
    }

    SkyforgeFoundationPiece(CompoundTag tag) {
        super(SkyforgeNeoForge1211StructurePieces.FOUNDATION.get(), tag);
        this.supportingVolumeId = new SkyIslandWorldVolumeId(
                tag.getLong(ROOT_SEED_TAG),
                tag.getString(GROUP_IDENTIFIER_TAG),
                tag.getInt(GROUP_ORDINAL_TAG),
                tag.getInt(MEMBER_ORDINAL_TAG),
                tag.getLong(GEOMETRY_SEED_TAG));
        this.foundationTopY = tag.getInt(FOUNDATION_TOP_Y_TAG);
        this.maximumFillDepth = tag.getInt(MAXIMUM_FILL_DEPTH_TAG);
        if (maximumFillDepth <= 0) {
            throw new IllegalArgumentException("serialized maximumFillDepth must be positive");
        }
    }

    SkyIslandWorldVolumeId supportingVolumeId() {
        return supportingVolumeId;
    }

    int foundationTopY() {
        return foundationTopY;
    }

    int maximumFillDepth() {
        return maximumFillDepth;
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag) {
        tag.putLong(ROOT_SEED_TAG, supportingVolumeId.archipelagoRootSeed());
        tag.putString(GROUP_IDENTIFIER_TAG, supportingVolumeId.groupIdentifier());
        tag.putInt(GROUP_ORDINAL_TAG, supportingVolumeId.groupOrdinal());
        tag.putInt(MEMBER_ORDINAL_TAG, supportingVolumeId.memberOrdinal());
        tag.putLong(GEOMETRY_SEED_TAG, supportingVolumeId.geometrySeed());
        tag.putInt(FOUNDATION_TOP_Y_TAG, foundationTopY);
        tag.putInt(MAXIMUM_FILL_DEPTH_TAG, maximumFillDepth);
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBounds,
            ChunkPos chunkPos,
            BlockPos pivot) {
        int minimumX = Math.max(getBoundingBox().minX(), chunkBounds.minX());
        int maximumX = Math.min(getBoundingBox().maxX(), chunkBounds.maxX());
        int minimumZ = Math.max(getBoundingBox().minZ(), chunkBounds.minZ());
        int maximumZ = Math.min(getBoundingBox().maxZ(), chunkBounds.maxZ());
        if (minimumX > maximumX || minimumZ > maximumZ) {
            return;
        }

        int minimumSupportY = Math.max(
                level.getMinBuildHeight(),
                Math.subtractExact(foundationTopY, maximumFillDepth));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int worldZ = minimumZ; worldZ <= maximumZ; worldZ++) {
            for (int worldX = minimumX; worldX <= maximumX; worldX++) {
                int supportY = findOwnedSupportY(level, cursor, worldX, worldZ, minimumSupportY);
                if (supportY == Integer.MIN_VALUE || supportY >= foundationTopY) {
                    continue;
                }
                BlockState fillState = chooseFillState(level, cursor, worldX, supportY, worldZ);
                for (int worldY = supportY + 1; worldY <= foundationTopY; worldY++) {
                    cursor.set(worldX, worldY, worldZ);
                    if (chunkBounds.isInside(cursor) && level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, fillState, 2);
                    }
                }
            }
        }
    }

    private int findOwnedSupportY(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int worldZ,
            int minimumSupportY) {
        for (int worldY = foundationTopY; worldY >= minimumSupportY; worldY--) {
            cursor.set(worldX, worldY, worldZ);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }

            boolean ownedByAdmittedVolume = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            supportingVolumeId,
                            worldX,
                            worldY,
                            worldZ)
                    .orElseThrow(() -> new IllegalStateException(
                            "Skyforge foundation realization requires its compiled runtime binding"));
            return ownedByAdmittedVolume ? worldY : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState chooseFillState(
            WorldGenLevel level,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int supportY,
            int worldZ) {
        if (supportY > level.getMinBuildHeight()) {
            cursor.set(worldX, supportY - 1, worldZ);
            BlockState below = level.getBlockState(cursor);
            if (!below.isAir() && below.getFluidState().isEmpty()) {
                return below;
            }
        }
        cursor.set(worldX, supportY, worldZ);
        BlockState support = level.getBlockState(cursor);
        if (!support.isAir() && support.getFluidState().isEmpty()) {
            return support;
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static BoundingBox foundationBounds(BoundingBox structureBounds, int maximumFillDepth) {
        Objects.requireNonNull(structureBounds, "structureBounds");
        if (maximumFillDepth <= 0) {
            throw new IllegalArgumentException("maximumFillDepth must be positive");
        }
        int topY = Math.subtractExact(structureBounds.minY(), 1);
        int bottomY = Math.addExact(Math.subtractExact(topY, maximumFillDepth), 1);
        return new BoundingBox(
                structureBounds.minX(),
                bottomY,
                structureBounds.minZ(),
                structureBounds.maxX(),
                topY,
                structureBounds.maxZ());
    }
}
