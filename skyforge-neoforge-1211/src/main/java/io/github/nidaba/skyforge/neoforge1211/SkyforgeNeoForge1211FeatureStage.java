package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Short-lived Minecraft feature-placement scope for one chunk decoration call. */
final class SkyforgeNeoForge1211FeatureStage {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private SkyforgeNeoForge1211FeatureStage() {}

    /**
     * Opens a scope from the live post-carver chunk and accepted Skyforge occupancy.
     *
     * <p>The scope is intentionally thread-local and must be closed around the synchronous vanilla
     * biome-decoration call. If no Skyforge runtime binding is active, the scope remains valid but
     * exposes no supplemental positions.
     */
    static Scope open(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("a Skyforge feature-placement scope is already active on this thread");
        }

        Optional<MinecraftAdditionalSurfaceIndex> index =
                SkyforgeNeoForge1211SurfaceStage.materializeOccupancy(chunk)
                        .map(materialization -> MinecraftAdditionalSurfaceIndex.from(chunk, materialization));
        Scope scope = new Scope(index);
        ACTIVE.set(scope);
        return scope;
    }

    static List<BlockPos> additionalPositions(int worldX, int worldZ) {
        Scope scope = ACTIVE.get();
        if (scope == null || scope.closed) {
            return List.of();
        }
        return scope.index
                .map(value -> value.positions(worldX, worldZ))
                .orElseGet(List::of);
    }

    static boolean hasActiveScope() {
        Scope scope = ACTIVE.get();
        return scope != null && !scope.closed;
    }

    static final class Scope implements AutoCloseable {
        private final Optional<MinecraftAdditionalSurfaceIndex> index;
        private boolean closed;

        private Scope(Optional<MinecraftAdditionalSurfaceIndex> index) {
            this.index = Objects.requireNonNull(index, "index");
        }

        void requireActive() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope is not active");
            }
        }

        @Override
        public void close() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope changed before close");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
