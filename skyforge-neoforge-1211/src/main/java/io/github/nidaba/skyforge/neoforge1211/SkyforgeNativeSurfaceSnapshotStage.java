package io.github.nidaba.skyforge.neoforge1211;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Short-lived handoff of pre-decoration native surface representation for later island realization. */
final class SkyforgeNativeSurfaceSnapshotStage {
    private static final int MAXIMUM_RETAINED_SURFACE_SNAPSHOTS = 4096;
    private static final ConcurrentHashMap<Long, MinecraftNativeSurfaceSnapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();
    private static final Map<Long, MinecraftNativeSurfaceSnapshot> SURFACE_HISTORY =
            new LinkedHashMap<>(64, 0.75f, true);

    private SkyforgeNativeSurfaceSnapshotStage() {}

    static void capture(ChunkAccess chunk) {
        capture(chunk, 63);
    }

    static void capture(
            ChunkAccess chunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        Objects.requireNonNull(chunk, "chunk");
        capture(chunk, MinecraftNativeSurfaceSnapshot.capture(chunk, generator, randomState));
    }

    static void capture(ChunkAccess chunk, int baselineY) {
        Objects.requireNonNull(chunk, "chunk");
        capture(chunk, MinecraftNativeSurfaceSnapshot.capture(chunk, baselineY));
    }

    private static void capture(ChunkAccess chunk, MinecraftNativeSurfaceSnapshot snapshot) {
        retainSurfaceSnapshot(chunk.getPos().toLong(), snapshot);
        MinecraftNativeSurfaceSnapshot previous = SNAPSHOTS.put(chunk.getPos().toLong(), snapshot);
        if (previous != null) {
            throw new IllegalStateException("native surface snapshot already captured for chunk " + chunk.getPos());
        }
    }

    static MinecraftNativeSurfaceSnapshot consume(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        MinecraftNativeSurfaceSnapshot snapshot = SNAPSHOTS.remove(chunk.getPos().toLong());
        if (snapshot == null) {
            throw new IllegalStateException("missing native surface snapshot for chunk " + chunk.getPos());
        }
        return snapshot;
    }

    /**
     * Returns the immutable pre-decoration provider surface state for a column when it remains in
     * the bounded neighborhood cache. Surface population uses it as a deterministic read-only
     * support view when a native feature attaches across an exact-volume chunk boundary.
     */
    static Optional<BlockState> surfaceBlockAt(BlockPos position) {
        Objects.requireNonNull(position, "position");
        long chunkKey = new net.minecraft.world.level.ChunkPos(
                position.getX() >> 4, position.getZ() >> 4).toLong();
        MinecraftNativeSurfaceSnapshot snapshot;
        synchronized (SURFACE_HISTORY) {
            snapshot = SURFACE_HISTORY.get(chunkKey);
        }
        if (snapshot == null) {
            return Optional.empty();
        }
        var surface = snapshot.surface(Math.floorMod(position.getX(), 16), Math.floorMod(position.getZ(), 16));
        if (surface.isEmpty()) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(surface.orElseThrow().blockKey()).map(Block::defaultBlockState);
    }

    private static void retainSurfaceSnapshot(long chunkKey, MinecraftNativeSurfaceSnapshot snapshot) {
        synchronized (SURFACE_HISTORY) {
            SURFACE_HISTORY.put(chunkKey, snapshot);
            while (SURFACE_HISTORY.size() > MAXIMUM_RETAINED_SURFACE_SNAPSHOTS) {
                var oldest = SURFACE_HISTORY.entrySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }
}
