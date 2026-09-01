package io.github.nidaba.skyforge.neoforge1211;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
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
        Scope scope = new Scope(chunk.getPos(), index);
        ACTIVE.set(scope);
        return scope;
    }

    /** Accepted SF-IMP-0038 dry-land reachability view. */
    static List<BlockPos> additionalPositions(int worldX, int worldZ) {
        Scope scope = ACTIVE.get();
        if (scope == null || scope.closed) {
            return List.of();
        }
        return scope.index
                .map(value -> value.positions(worldX, worldZ))
                .orElseGet(List::of);
    }

    static List<BlockPos> suitablePositions(
            int worldX,
            int worldZ,
            MinecraftSurfaceSuitability suitability) {
        Objects.requireNonNull(suitability, "suitability");
        Scope scope = ACTIVE.get();
        if (scope == null || scope.closed) {
            return List.of();
        }
        List<BlockPos> positions = scope.index
                .map(value -> value.positions(worldX, worldZ, suitability))
                .orElseGet(List::of);
        scope.recordSuitabilityQuery(suitability, positions.size());
        return positions;
    }

    static boolean hasActiveScope() {
        Scope scope = ACTIVE.get();
        return scope != null && !scope.closed;
    }

    static final class Scope implements AutoCloseable {
        private final ChunkPos chunkPos;
        private final Optional<MinecraftAdditionalSurfaceIndex> index;
        private final Map<MinecraftSurfaceSuitability, Integer> queryCounts =
                new EnumMap<>(MinecraftSurfaceSuitability.class);
        private final Map<MinecraftSurfaceSuitability, Integer> emittedCounts =
                new EnumMap<>(MinecraftSurfaceSuitability.class);
        private boolean closed;

        private Scope(ChunkPos chunkPos, Optional<MinecraftAdditionalSurfaceIndex> index) {
            this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
            this.index = Objects.requireNonNull(index, "index");
        }

        void requireActive() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope is not active");
            }
        }

        private void recordSuitabilityQuery(
                MinecraftSurfaceSuitability suitability,
                int emittedPositions) {
            queryCounts.merge(suitability, 1, Integer::sum);
            emittedCounts.merge(suitability, emittedPositions, Integer::sum);
        }

        @Override
        public void close() {
            if (closed || ACTIVE.get() != this) {
                throw new IllegalStateException("Skyforge feature-placement scope changed before close");
            }
            if (Boolean.getBoolean(SkyforgeNeoForge1211DevRuntime.ENABLE_PROPERTY)
                    && Math.abs(chunkPos.x) <= 2
                    && Math.abs(chunkPos.z) <= 2) {
                int dryLand = index.map(value -> value.totalPositions(MinecraftSurfaceSuitability.DRY_LAND)).orElse(0);
                int dryOpen = index.map(value -> value.totalPositions(MinecraftSurfaceSuitability.DRY_OPEN)).orElse(0);
                int submerged = index
                        .map(value -> value.totalPositions(MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR))
                        .orElse(0);
                int openWater = index
                        .map(value -> value.totalPositions(MinecraftSurfaceSuitability.OPEN_WATER_FLOOR))
                        .orElse(0);
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SF-IMP-0040 exposure diagnostic chunk=" + chunkPos
                                + " dryLand=" + dryLand
                                + " dryOpen=" + dryOpen
                                + " submergedWaterFloor=" + submerged
                                + " openWaterFloor=" + openWater
                                + " dryOpenQueries=" + queryCounts.getOrDefault(MinecraftSurfaceSuitability.DRY_OPEN, 0)
                                + " dryOpenEmitted=" + emittedCounts.getOrDefault(MinecraftSurfaceSuitability.DRY_OPEN, 0)
                                + " submergedQueries="
                                + queryCounts.getOrDefault(MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR, 0)
                                + " submergedEmitted="
                                + emittedCounts.getOrDefault(MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR, 0)
                                + " openWaterQueries="
                                + queryCounts.getOrDefault(MinecraftSurfaceSuitability.OPEN_WATER_FLOOR, 0)
                                + " openWaterEmitted="
                                + emittedCounts.getOrDefault(MinecraftSurfaceSuitability.OPEN_WATER_FLOOR, 0));
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
