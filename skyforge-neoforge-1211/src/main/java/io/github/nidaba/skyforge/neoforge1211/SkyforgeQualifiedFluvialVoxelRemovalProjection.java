package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelColumn;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelComponentPlan;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelComponentStatus;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SkyIslandHydrologyRuntimeAuthorization;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Literal NeoForge projection of accepted F4C removal authority into exact block positions.
 *
 * <p>This class performs no routing, grading, widening, carrier repair, bank fill, water placement,
 * or terrain mutation. It merely expands each already-qualified F4C column's exact surface-removal
 * interval into immutable Minecraft positions.
 */
final class SkyforgeQualifiedFluvialVoxelRemovalProjection {
    record ComponentRemoval(
            SkyIslandWorldVolumeId volumeId,
            int terminalCellIndex,
            List<BlockPos> positions) {
        ComponentRemoval {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            positions = List.copyOf(positions);
            if (positions.isEmpty()) {
                throw new IllegalArgumentException(
                        "qualified F4D component removal requires at least one position");
            }
        }
    }

    private SkyforgeQualifiedFluvialVoxelRemovalProjection() {}

    static List<ComponentRemoval> plan(
            SkyIslandFluvialVoxelQuantizationPlan quantization,
            SkyIslandHydrologyRuntimeAuthorization authorization) {
        Objects.requireNonNull(quantization, "quantization");
        Objects.requireNonNull(authorization, "authorization");
        if (!authorization.quantization().equals(quantization)) {
            throw new IllegalArgumentException(
                    "F4I authorization must match the exact F4H quantization plan");
        }
        List<ComponentRemoval> result = plan(quantization);
        for (ComponentRemoval component : result) {
            if (!authorization.allowsVolume(component.volumeId())
                    || component.positions().stream().anyMatch(position ->
                            !authorization.allowsRemoval(
                                    position.getX(), position.getY(), position.getZ()))) {
                throw new IllegalStateException(
                        "F4I authorization rejected an exact projected removal position");
            }
        }
        return result;
    }

    static List<ComponentRemoval> plan(SkyIslandFluvialVoxelQuantizationPlan quantization) {
        Objects.requireNonNull(quantization, "quantization");
        SkyIslandWorldVolumeId volumeId =
                quantization.association().realizedVolumeId();

        List<ComponentRemoval> removals = new ArrayList<>();
        Set<Long> globallyOwned = new LinkedHashSet<>();
        int projectedPositions = 0;

        for (SkyIslandFluvialVoxelComponentPlan component : quantization.components()) {
            if (component.status() != SkyIslandFluvialVoxelComponentStatus.QUALIFIED) {
                if (!component.columns().isEmpty()) {
                    throw new IllegalStateException(
                            "rejected F4C component leaked voxel columns into F4D");
                }
                continue;
            }

            List<BlockPos> positions = new ArrayList<>();
            for (SkyIslandFluvialVoxelColumn column : component.columns()) {
                for (int y = column.targetMaximumSolidY() + 1;
                        y <= column.originalSupport().maximumSolidY();
                        y++) {
                    BlockPos position =
                            new BlockPos(column.worldX(), y, column.worldZ());
                    if (!globallyOwned.add(position.asLong())) {
                        throw new IllegalStateException(
                                "F4D components claim the same removal position");
                    }
                    positions.add(position);
                    projectedPositions++;
                }
            }

            if (!positions.isEmpty()) {
                removals.add(new ComponentRemoval(
                        volumeId,
                        component.terminalCellIndex(),
                        positions));
            }
        }

        if (projectedPositions != quantization.totalRemovedSolidBlocks()) {
            throw new IllegalStateException(
                    "F4D literal position count differs from accepted F4C removal count");
        }
        return List.copyOf(removals);
    }
}
