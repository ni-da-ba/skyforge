package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
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

    public static boolean acceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return SkyforgePopulationExecutionStage.activeExecution()
                .map(execution -> execution.acceptWrite(position))
                .orElse(true);
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
}
