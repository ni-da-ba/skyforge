package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Narrow public bridge used only by the isolated worldgen mixin package. */
public final class SkyforgeWorldGenRegionDomainBridge {
    private SkyforgeWorldGenRegionDomainBridge() {}

    public static boolean active() {
        return SkyforgePopulationExecutionStage.activeExecution().isPresent();
    }

    public static boolean isVisible(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return SkyforgePopulationExecutionStage.activeExecution()
                .map(execution -> execution.isVisible(position))
                .orElse(true);
    }

    /**
     * Non-mutating preflight used by {@code WorldGenLevel.ensureCanWrite}.
     *
     * <p>Vanilla ore generation and other optimized features may acquire raw chunk sections and
     * write them directly after this preflight, bypassing Level/WorldGenRegion#setBlock. Therefore
     * exact-volume isolation must participate here as well as at the high-level write seam.
     */
    public static boolean canWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()) {
            return true;
        }
        var active = execution.orElseThrow();
        boolean accepted = active.canWrite(position);
        SkyforgeUndergroundPlacementProbe.observeWritePreflight(
                active.operation(),
                position,
                accepted);
        return accepted;
    }

    public static boolean acceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()) {
            return true;
        }
        var active = execution.orElseThrow();
        boolean accepted = active.acceptWrite(position);
        SkyforgeUndergroundPlacementProbe.observeWriteDecision(
                active.operation(),
                position,
                accepted);
        return accepted;
    }

    /** State-aware write admission for high-level native feature writes. */
    public static boolean acceptWrite(BlockPos position, BlockState state) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()) {
            return true;
        }
        var active = execution.orElseThrow();
        boolean accepted = active.acceptWrite(position, state);
        SkyforgeUndergroundPlacementProbe.observeWriteDecision(
                active.operation(),
                position,
                accepted);
        return accepted;
    }

    public static OptionalInt exactHeight(
            Heightmap.Types heightmapType,
            int worldX,
            int worldZ,
            int minimumY,
            int height) {
        Objects.requireNonNull(heightmapType, "heightmapType");
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()) {
            return OptionalInt.empty();
        }
        var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                execution.orElseThrow().operation().volumeId(),
                worldX,
                worldZ,
                heightmapType,
                minimumY,
                height);
        return claim.isPresent()
                ? OptionalInt.of(claim.orElseThrow().height())
                : OptionalInt.of(minimumY);
    }

    /**
     * Returns the biome assigned to the active exact-volume population domain.
     *
     * <p>The quart coordinates are accepted deliberately even though the first implementation uses
     * one biome per proof volume. A later environment-field resolver may vary biome identity inside
     * one island without changing the mixin boundary.
     */
    public static Optional<Holder<Biome>> exactBiome(
            int quartX,
            int quartY,
            int quartZ) {
        return SkyforgePopulationExecutionStage.activeExecution()
                .flatMap(SkyforgePopulationExecutionStage.Execution::domainBiome);
    }

    /**
     * Returns the biome visible at a block position while an exact-volume population operation is
     * active.
     *
     * <p>Minecraft's ordinary {@code LevelReader#getBiome(BlockPos)} path delegates through
     * {@code BiomeManager#getBiome(BlockPos)}. That manager-level lookup does not necessarily call
     * back through {@code WorldGenRegion#getUncachedNoiseBiome} in a way a region mixin can safely
     * rely on. Exposing the same thread-local domain biome here lets the adapter scope the actual
     * biome-manager read seam without mutating base-world biome storage.
     */
    public static Optional<Holder<Biome>> exactBiome(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return SkyforgePopulationExecutionStage.activeExecution()
                .flatMap(SkyforgePopulationExecutionStage.Execution::domainBiome);
    }
}
