package io.github.nidaba.skyforge.neoforge1211;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Short-lived Minecraft feature-placement scope for one chunk decoration call. */
final class SkyforgeNeoForge1211FeatureStage {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211FeatureStage.class.getName());

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
        Scope scope = new Scope(chunk, index);
        ACTIVE.set(scope);
        return scope;
    }

    static List<BlockPos> additionalPositions(int worldX, int worldZ) {
        Scope scope = ACTIVE.get();
        if (scope == null || scope.closed) {
            return List.of();
        }
        List<BlockPos> positions = scope.index
                .map(value -> value.positions(worldX, worldZ))
                .orElseGet(List::of);
        scope.recordQuery(positions);
        return positions;
    }

    static boolean hasActiveScope() {
        Scope scope = ACTIVE.get();
        return scope != null && !scope.closed;
    }

    static final class Scope implements AutoCloseable {
        private final ChunkAccess chunk;
        private final ChunkPos chunkPos;
        private final Optional<MinecraftAdditionalSurfaceIndex> index;
        private final int availablePositions;
        private final Set<BlockPos> emittedTargets = new HashSet<>();
        private int queries;
        private int emittedPositions;
        private boolean closed;

        private Scope(ChunkAccess chunk, Optional<MinecraftAdditionalSurfaceIndex> index) {
            this.chunk = Objects.requireNonNull(chunk, "chunk");
            this.chunkPos = chunk.getPos();
            this.index = Objects.requireNonNull(index, "index");
            this.availablePositions = index.map(MinecraftAdditionalSurfaceIndex::totalPositions).orElse(0);
        }

        void requireActive() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope is not active");
            }
        }

        private void recordQuery(List<BlockPos> positions) {
            queries++;
            emittedPositions += positions.size();
            emittedTargets.addAll(positions);
        }

        @Override
        public void close() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope changed before close");
            }
            if (Boolean.getBoolean(SkyforgeNeoForge1211DevRuntime.ENABLE_PROPERTY)
                    && Math.abs(chunkPos.x) <= 2
                    && Math.abs(chunkPos.z) <= 2) {
                long markerBlocks = emittedTargets.stream()
                        .filter(position -> chunk.getBlockState(position).is(Blocks.GOLD_BLOCK))
                        .count();
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SF-IMP-0038 feature diagnostic chunk=" + chunkPos
                                + " availableAdditionalPositions=" + availablePositions
                                + " modifierQueries=" + queries
                                + " emittedPositions=" + emittedPositions
                                + " markerBlocksAtEmittedPositions=" + markerBlocks);
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
