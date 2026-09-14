package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Small exact-volume consumer of AUTH-0086 visible-water intent.
 *
 * <p>It deliberately selects no watershed or water availability. For each authored class that is
 * actually present, it realizes the first stable accepted intent of that kind. Missing classes are
 * left absent rather than synthesized; bounded accepted-corpus fixtures cover implementation risks
 * that the DR-00 canonical specimen does not carry. Water replaces only exact owner voxels, keeping
 * this initial static realization out of the native-spring provenance/propagation path.
 */
final class SkyforgeAuthoredVisibleHydrologyAdapter {
    enum Feature { CHANNEL, RETAINED_WATER, VERTICAL_DISCHARGE, EDGE_DISCHARGE }

    record Deployment(SkyIslandWorldVolumeId volumeId, Feature feature, List<BlockPos> positions) {
        Deployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            positions = List.copyOf(positions);
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("hydrology deployment requires owned positions");
            }
        }
    }

    private SkyforgeAuthoredVisibleHydrologyAdapter() {}

    static List<Deployment> plan(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        SkyIslandVisibleHydrologicRealizationPlan intent =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
        List<Deployment> deployments = new ArrayList<>();

        intent.channels().stream().findFirst().ifPresent(channel -> deployments.add(
                atPath(volume, terrain, Feature.CHANNEL, channel.path().points())));
        intent.retainedWater().stream().findFirst().ifPresent(retained -> deployments.add(
                atFootprint(volume, terrain, retained.footprint().cells())));
        intent.drops().stream()
                .filter(drop -> drop.kind() == SkyIslandVisibleHydrologicRealizationKind.CASCADE
                        || drop.kind() == SkyIslandVisibleHydrologicRealizationKind.WATERFALL)
                .findFirst()
                .ifPresent(vertical -> deployments.add(at(
                        volume,
                        terrain,
                        Feature.VERTICAL_DISCHARGE,
                        vertical.drop().position().x(),
                        vertical.drop().position().z(),
                        3)));
        intent.drops().stream()
                .filter(drop -> drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .findFirst()
                .ifPresent(edge -> deployments.add(at(
                        volume,
                        terrain,
                        Feature.EDGE_DISCHARGE,
                        edge.drop().position().x(),
                        edge.drop().position().z(),
                        2)));
        return List.copyOf(deployments);
    }

    /** Applies every authored deployment whose exact cells occur in an already-available chunk. */
    static int applyAvailable(ChunkAccess chunk, SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(terrain, "terrain");
        int written = 0;
        for (SkyIslandWorldVolume volume : terrain.candidateVolumes(chunk)) {
            var descriptor = terrain.authoredDescriptor(volume.id());
            if (descriptor.isEmpty()) {
                continue;
            }
            for (Deployment deployment : plan(descriptor.orElseThrow(), volume, terrain)) {
                written += apply(chunk, deployment);
            }
        }
        return written;
    }

    private static Deployment atPath(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Feature feature,
            List<io.github.nidaba.skyforge.world.SkyIslandLocalPosition> points) {
        List<BlockPos> positions = new ArrayList<>();
        for (var point : points) {
            positions.addAll(at(volume, terrain, feature, point.x(), point.z(), 1).positions());
        }
        return deployment(volume.id(), feature, positions);
    }

    private static Deployment atFootprint(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            List<io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell> cells) {
        List<BlockPos> positions = new ArrayList<>();
        for (var cell : cells) {
            positions.addAll(at(volume, terrain, Feature.RETAINED_WATER,
                    cell.position().x(), cell.position().z(), 1).positions());
        }
        return deployment(volume.id(), Feature.RETAINED_WATER, positions);
    }

    private static Deployment at(
            SkyIslandWorldVolume volume, SkyforgeNeoForge1211ChunkAdapter terrain, Feature feature,
            double localX, double localZ, int depth) {
        int x = (int) Math.round(volume.compiledVolume().descriptor().centerX() + localX);
        int z = (int) Math.round(volume.compiledVolume().descriptor().centerZ() + localZ);
        var range = terrain.integerSolidRange(volume.id(), x, z)
                .orElseThrow(() -> new IllegalStateException("AUTH-0086 intent has no realized owner column"));
        List<BlockPos> positions = new ArrayList<>();
        for (int y = range.maximumY(); y >= range.minimumY() && positions.size() < depth; y--) {
            if (terrain.isSolidOwnedBy(volume.id(), x, y, z)
                    && !terrain.isSolidOwnedByOtherVolume(volume.id(), x, y, z)) {
                positions.add(new BlockPos(x, y, z));
            }
        }
        return deployment(volume.id(), feature, positions);
    }

    private static Deployment deployment(
            SkyIslandWorldVolumeId volumeId, Feature feature, List<BlockPos> positions) {
        return new Deployment(volumeId, feature, new ArrayList<>(new LinkedHashSet<>(positions)));
    }

    /** Applies only a chunk's positions; replay retains water and writes nothing new. */
    static int apply(ChunkAccess chunk, Deployment deployment) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(deployment, "deployment");
        int written = 0;
        for (BlockPos position : deployment.positions()) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            if (!chunk.getBlockState(position).is(Blocks.WATER)) {
                chunk.setBlockState(position, Blocks.WATER.defaultBlockState(), false);
                written++;
            }
        }
        return written;
    }
}
