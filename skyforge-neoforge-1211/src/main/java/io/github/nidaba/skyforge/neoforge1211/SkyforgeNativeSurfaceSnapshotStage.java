package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Short-lived handoff of pre-decoration native surface representation for later island realization. */
final class SkyforgeNativeSurfaceSnapshotStage {
    private static final ConcurrentHashMap<Long, MinecraftNativeSurfaceSnapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();

    private SkyforgeNativeSurfaceSnapshotStage() {}

    static void capture(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        MinecraftNativeSurfaceSnapshot snapshot = MinecraftNativeSurfaceSnapshot.capture(chunk);
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
