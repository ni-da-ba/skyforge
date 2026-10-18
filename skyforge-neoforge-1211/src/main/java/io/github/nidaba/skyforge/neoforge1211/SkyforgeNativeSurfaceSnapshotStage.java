package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Short-lived handoff of pre-decoration native surface representation for later island realization. */
final class SkyforgeNativeSurfaceSnapshotStage {
    private static final ConcurrentHashMap<Long, MinecraftNativeSurfaceSnapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();

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
}
