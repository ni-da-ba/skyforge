package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writes an accepted Skyforge materialization into one real Minecraft ChunkAccess. */
public final class SkyforgeNeoForge1211ChunkWriter {
    private static final int CHUNK_WIDTH = 16;

    private final MinecraftBlockStateResolver blockStateResolver;

    public SkyforgeNeoForge1211ChunkWriter(MinecraftBlockStateResolver blockStateResolver) {
        this.blockStateResolver = Objects.requireNonNull(blockStateResolver, "blockStateResolver");
    }

    /** Resolves one backend-owned material key for non-mutating generator queries. */
    BlockState resolveForQuery(ResourceLocation key) {
        return blockStateResolver.resolve(Objects.requireNonNull(key, "key"));
    }

    /**
     * Resolves every accepted block key and writes the exact owned positions into the target chunk.
     *
     * <p>This exact mode is retained for isolated equivalence proofs and dedicated backends where
     * Skyforge owns the entire target interval. In a normal Minecraft terrain-composition path,
     * use {@link #writeSolidOverlay(ChunkAccess, MinecraftChunkMaterialization)} so Skyforge AIR
     * preserves backend-native terrain.
     */
    public MinecraftChunkWriteResult write(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        validateOwnership(chunk, materialization);
        return writeInternal(chunk, materialization, false);
    }

    /**
     * Adds only Skyforge-owned solid states to an existing Minecraft chunk.
     *
     * <p>AIR in a Skyforge materialization means that Skyforge contributes no solid at that
     * position. It does not authorize an additive backend to erase terrain that Minecraft or
     * another backend system already placed there. This mode therefore skips AIR positions
     * entirely while retaining strict registry resolution and read-back verification for every
     * Skyforge solid that is written.
     *
     * <p>Physical admission observation deliberately lives above this writer. That keeps a concrete
     * block writer free of generation-lifecycle side effects and allows deferred exact-volume writes
     * to reuse the same primitive without accidentally resurveying already-mutated chunks. When the
     * deferred stable-chunk lifecycle scope is active, that scope supplies only the runtime lighting
     * and client-broadcast side effects required after each verified mutation.
     */
    public MinecraftChunkWriteResult writeSolidOverlay(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        validateOwnership(chunk, materialization);
        return writeInternal(chunk, materialization, true);
    }

    private MinecraftChunkWriteResult writeInternal(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization,
            boolean solidOverlayOnly) {
        long maximumYExclusive = (long) materialization.minimumY() + materialization.height();
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
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    boolean expectedAir = SkyforgeMinecraftBlockPalette.AIR.equals(key);
                    if (solidOverlayOnly && expectedAir) {
                        continue;
                    }

                    int worldX = Math.addExact(minimumX, localX);
                    if (!expectedAir && !SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(worldX, worldY, worldZ)) {
                        continue;
                    }

                    BlockState state = blockStateResolver.resolve(key);
                    if (state.isAir() != expectedAir) {
                        throw new IllegalStateException(
                                "resolved BlockState changed authoritative Skyforge occupancy for " + key);
                    }

                    blockPos.set(worldX, worldY, worldZ);
                    BlockState previousState = chunk.getBlockState(blockPos);
                    chunk.setBlockState(blockPos, state, false);
                    BlockState stored = chunk.getBlockState(blockPos);
                    if (!stored.equals(state)) {
                        throw new IllegalStateException("ChunkAccess did not retain the resolved BlockState");
                    }
                    SkyforgeDeferredChunkMutationLifecycle.afterWrite(chunk, blockPos, previousState, stored);
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

    private static void validateOwnership(
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
    }
}
