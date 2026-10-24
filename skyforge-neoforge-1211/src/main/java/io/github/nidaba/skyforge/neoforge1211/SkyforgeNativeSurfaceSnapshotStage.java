package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Short-lived handoff of pre-decoration native surface representation for later island realization. */
final class SkyforgeNativeSurfaceSnapshotStage {
    private static final ConcurrentHashMap<Long, MinecraftNativeSurfaceSnapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();
    private static final AtomicReference<MinecraftNativeSurfaceSnapshot> ACCEPTANCE_SNAPSHOT =
            new AtomicReference<>();

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

    static void capture(
            net.minecraft.server.level.WorldGenRegion level,
            net.minecraft.world.level.StructureManager structureManager,
            ChunkAccess chunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        Objects.requireNonNull(chunk, "chunk");
        capture(
                chunk,
                MinecraftNativeSurfaceSnapshot.captureProviderSurface(
                        level, structureManager, chunk, generator, randomState));
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
        retainAcceptanceSnapshot(chunk, snapshot);
        return snapshot;
    }

    /**
     * Consumes the pre-decoration snapshot when the surface stage ran in this process. If a saved
     * void-base world resumes chunk generation at FEATURES after a server/client restart, its
     * short-lived snapshot was not persisted; deterministically rebuild the detached provider
     * surface from the same live worldgen context instead of failing chunk generation.
     */
    static MinecraftNativeSurfaceSnapshot consumeOrCaptureProviderSurface(
            net.minecraft.server.level.WorldGenRegion level,
            net.minecraft.world.level.StructureManager structureManager,
            ChunkAccess chunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(structureManager, "structureManager");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(randomState, "randomState");
        MinecraftNativeSurfaceSnapshot snapshot = SNAPSHOTS.remove(chunk.getPos().toLong());
        if (snapshot == null) {
            snapshot = MinecraftNativeSurfaceSnapshot.captureProviderSurface(
                    level, structureManager, chunk, generator, randomState);
        }
        retainAcceptanceSnapshot(chunk, snapshot);
        return snapshot;
    }

    private static void retainAcceptanceSnapshot(
            ChunkAccess chunk,
            MinecraftNativeSurfaceSnapshot snapshot) {
        if (Boolean.getBoolean(SkyforgeTectonicTerrainAcceptance.ENABLE_PROPERTY)
                && chunk.getPos().equals(SkyforgeTectonicTerrainAcceptance.REVIEW_CHUNK)) {
            ACCEPTANCE_SNAPSHOT.set(snapshot);
        }
    }

    static Optional<MinecraftNativeSurfaceSnapshot> consumeAcceptanceSnapshot(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (!chunk.getPos().equals(SkyforgeTectonicTerrainAcceptance.REVIEW_CHUNK)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ACCEPTANCE_SNAPSHOT.getAndSet(null));
    }
}
