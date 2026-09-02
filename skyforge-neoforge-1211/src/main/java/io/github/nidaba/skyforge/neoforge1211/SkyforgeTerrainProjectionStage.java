package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Generation-scoped terrain-domain context for native template placement executed by Skyforge.
 *
 * <p>Vanilla terrain is captured immediately after vanilla surface construction and before
 * Skyforge realizes any suspended volume. That immutable snapshot is carried to the FEATURES
 * stage for the same chunk. Terrain-sensitive placement therefore queries either that base-world
 * snapshot or one exact compiled Skyforge volume; the two domains never compete through a global
 * heightmap.
 */
final class SkyforgeTerrainProjectionStage {
    private static final ConcurrentHashMap<Long, MinecraftBaseTerrainSurfaceSnapshot> BASE_WORLD =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private SkyforgeTerrainProjectionStage() {}

    static void captureBaseWorld(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        MinecraftBaseTerrainSurfaceSnapshot snapshot =
                MinecraftBaseTerrainSurfaceSnapshot.capture(chunk, Heightmap.Types.WORLD_SURFACE_WG);
        MinecraftBaseTerrainSurfaceSnapshot previous = BASE_WORLD.put(chunk.getPos().toLong(), snapshot);
        if (previous != null) {
            throw new IllegalStateException("base-world terrain snapshot already captured for chunk " + chunk.getPos());
        }
    }

    static Scope open(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Skyforge terrain-projection scope is already active on this thread");
        }
        MinecraftBaseTerrainSurfaceSnapshot snapshot = BASE_WORLD.remove(chunk.getPos().toLong());
        if (snapshot == null) {
            throw new IllegalStateException("missing pre-Skyforge base-world surface snapshot for chunk " + chunk.getPos());
        }
        ACTIVE.set(new Context(snapshot));
        return new Scope();
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    static OptionalInt baseWorldFirstFreeHeight(int worldX, int worldZ) {
        Context context = ACTIVE.get();
        return context == null
                ? OptionalInt.empty()
                : context.baseTerrain().firstFreeHeight(worldX, worldZ);
    }

    private record Context(MinecraftBaseTerrainSurfaceSnapshot baseTerrain) {
        private Context {
            Objects.requireNonNull(baseTerrain, "baseTerrain");
        }
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

        void requireActive() {
            if (closed || ACTIVE.get() == null) {
                throw new IllegalStateException("Skyforge terrain-projection scope is not active");
            }
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("Skyforge terrain-projection scope already closed");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
