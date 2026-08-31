package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;

/** Writes an accepted Skyforge materialization into one real Minecraft ChunkAccess. */
public final class SkyforgeNeoForge1211ChunkWriter {
    private static final int CHUNK_WIDTH = 16;

    private final MinecraftBlockStateResolver blockStateResolver;

    public SkyforgeNeoForge1211ChunkWriter(MinecraftBlockStateResolver blockStateResolver) {
        this.blockStateResolver = Objects.requireNonNull(blockStateResolver, "blockStateResolver");
    }

    /**
     * Resolves every accepted block key and writes the exact owned positions into the target chunk.
     *
     * <p>The writer does not plan, classify, or reinterpret Skyforge geometry. It consumes the
     * already-accepted chunk materialization and performs only Minecraft registry resolution and
     * chunk storage mutation.
     */
    public MinecraftChunkWriteResult write(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(materialization, "materialization");
        if (!chunk.getPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("materialization ChunkPos differs from target ChunkAccess");
        }

        long maximumYExclusive = (long) materialization.minimumY() + materialization.height();
        if (materialization.minimumY() < chunk.getMinBuildHeight()
                || maximumYExclusive > chunk.getMaxBuildHeight()) {
            throw new IllegalArgumentException("materialization vertical interval exceeds target ChunkAccess");
        }

        int minimumX = materialization.chunkPos().getMinBlockX();
        int minimumZ = materialization.chunkPos().getMinBlockZ();
        int assigned = 0;
        int solid = 0;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int worldY = materialization.minimumY();
                worldY < maximumYExclusive;
                worldY++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int worldZ = Math.addExact(minimumZ, localZ);
                for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                    int worldX = Math.addExact(minimumX, localX);
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    BlockState state = blockStateResolver.resolve(key);
                    boolean expectedAir = SkyforgeMinecraftBlockPalette.AIR.equals(key);
                    if (state.isAir() != expectedAir) {
                        throw new IllegalStateException(
                                "resolved BlockState changed authoritative Skyforge occupancy for " + key);
                    }

                    blockPos.set(worldX, worldY, worldZ);
                    chunk.setBlockState(blockPos, state, false);
                    BlockState stored = chunk.getBlockState(blockPos);
                    if (!stored.equals(state)) {
                        throw new IllegalStateException("ChunkAccess did not retain the resolved BlockState");
                    }
                    assigned++;
                    if (!state.isAir()) {
                        solid++;
                    }
                }
            }
        }

        return new MinecraftChunkWriteResult(
                assigned,
                solid,
                materialization.candidateVolumeReferences());
    }
}
