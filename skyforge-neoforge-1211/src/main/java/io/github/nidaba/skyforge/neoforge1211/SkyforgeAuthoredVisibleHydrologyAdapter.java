package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlanner;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
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
 * Exact-volume consumer of AUTH-0086 visible-water intent.
 *
 * <p>The adapter does not select a watershed, invent a route, or synthesize water availability.
 * Every accepted visible-hydrology intent is projected one-for-one. Channel realization consumes
 * the accepted bankfull-width, depth, incision, and hydrologic-terrain-response semantics to cut a
 * bounded recessed bed before placing water. Minecraft therefore realizes authored geomorphology
 * rather than painting water onto an unchanged terrain surface.
 */
final class SkyforgeAuthoredVisibleHydrologyAdapter {
    enum Feature { CHANNEL, RETAINED_WATER, VERTICAL_DISCHARGE, EDGE_DISCHARGE }

    record Deployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions) {
        Deployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            positions = List.copyOf(positions);
            carvedPositions = List.copyOf(carvedPositions);
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("hydrology deployment requires owned water positions");
            }
            var overlap = new java.util.HashSet<>(positions);
            overlap.retainAll(carvedPositions);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("hydrology water and dry carved positions must be disjoint");
            }
        }
    }

    private SkyforgeAuthoredVisibleHydrologyAdapter() {}

    static List<Deployment> plan(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        SkyIslandVisibleHydrologicRealizationPlan intent =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
        List<Deployment> deployments = new ArrayList<>();

        for (var channel : intent.channels()) {
            deployments.add(atPath(descriptor, volume, terrain, Feature.CHANNEL, channel.path()));
        }
        for (var retained : intent.retainedWater()) {
            deployments.add(atFootprint(volume, terrain, retained.footprint().cells()));
        }
        for (var drop : intent.drops()) {
            if (drop.kind() == SkyIslandVisibleHydrologicRealizationKind.CASCADE
                    || drop.kind() == SkyIslandVisibleHydrologicRealizationKind.WATERFALL) {
                deployments.add(at(
                        volume,
                        terrain,
                        Feature.VERTICAL_DISCHARGE,
                        drop.drop().position().x(),
                        drop.drop().position().z(),
                        3));
            } else if (drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE) {
                deployments.add(at(
                        volume,
                        terrain,
                        Feature.EDGE_DISCHARGE,
                        drop.drop().position().x(),
                        drop.drop().position().z(),
                        2));
            }
        }

        // Connected reaches can overlap at confluences. Water authority wins globally over dry
        // channel-clearance carving so application order can never erase an accepted wet cell.
        var allWater = deployments.stream()
                .flatMap(deployment -> deployment.positions().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return deployments.stream()
                .map(deployment -> new Deployment(
                        deployment.volumeId(),
                        deployment.feature(),
                        deployment.positions(),
                        deployment.carvedPositions().stream()
                                .filter(position -> !allWater.contains(position))
                                .toList()))
                .toList();
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
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Feature feature,
            SkyIslandNaturalizedChannelPath path) {
        if (path.points().isEmpty()) {
            throw new IllegalArgumentException("authored channel path requires at least one point");
        }

        List<Column> centerline = rasterizedCenterline(volume, path);
        int radius = channelRadius(path);
        int waterDepth = channelDepth(path);
        int incisionDepth = channelIncisionDepth(descriptor, path);

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        for (Column center : centerline) {
            var centerRange = terrain.integerSolidRange(volume.id(), center.x(), center.z());
            if (centerRange.isEmpty()) {
                continue;
            }
            int centerSurfaceY = centerRange.orElseThrow().maximumY();
            int centerWaterTopY = centerSurfaceY - (incisionDepth - waterDepth);
            appendChannelCrossSection(
                    volume,
                    terrain,
                    center,
                    centerWaterTopY,
                    radius,
                    waterDepth,
                    water,
                    carved);
        }
        if (water.isEmpty()) {
            throw new IllegalStateException("AUTH-0086 channel intent has no realized owner columns");
        }
        return deployment(
                volume.id(),
                feature,
                new ArrayList<>(water),
                new ArrayList<>(carved));
    }

    /** Maps accepted bankfull width potential to a bounded 3- or 5-block channel footprint. */
    static int channelRadius(SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(path, "path");
        return 1 + (int) Math.round(path.profile().bankfullWidthPotential());
    }

    /** Maps accepted depth potential to a bounded one- or two-block water column. */
    static int channelDepth(SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(path, "path");
        return 1 + (int) Math.round(path.profile().depthPotential());
    }

    /**
     * Maps accepted channel incision plus AUTH-0014/0015 terrain response to a bounded physical bed.
     *
     * <p>The water surface is always recessed by at least one block below its local bank. No new
     * hydrologic threshold or route is introduced; this is only a discrete realization scale for
     * already-authored normalized geomorphic potentials.
     */
    static int channelIncisionDepth(
            SkyIslandDescriptor descriptor,
            SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(path, "path");
        int source = path.profile().segment().sourceCellIndex();
        int downstream = path.profile().segment().downstreamCellIndex();
        double terrainResponse = SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor).cells().stream()
                .filter(cell -> cell.watershedCellIndex() == source
                        || cell.watershedCellIndex() == downstream)
                .mapToDouble(cell -> Math.max(
                        0.0,
                        cell.baseSurfacePotential() - cell.adjustedSurfacePotential()))
                .map(lowering -> lowering / SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING)
                .map(value -> Math.max(0.0, Math.min(1.0, value)))
                .max()
                .orElse(0.0);
        double incision = Math.max(path.profile().incisionPotential(), terrainResponse);
        int waterDepth = channelDepth(path);
        return Math.max(waterDepth + 1, 2 + (int) Math.round(2.0 * incision));
    }

    private static void appendChannelCrossSection(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Column center,
            int centerWaterTopY,
            int radius,
            int waterDepth,
            LinkedHashSet<BlockPos> water,
            LinkedHashSet<BlockPos> carved) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance > radius) {
                    continue;
                }
                int x = center.x() + dx;
                int z = center.z() + dz;
                var optionalRange = terrain.integerSolidRange(volume.id(), x, z);
                if (optionalRange.isEmpty()) {
                    continue;
                }
                var range = optionalRange.orElseThrow();
                int waterTopY = Math.min(centerWaterTopY, range.maximumY() - 1);
                int waterBottomY = waterTopY - waterDepth + 1;
                if (waterBottomY < range.minimumY()) {
                    continue;
                }

                // The centerline carries the deepest authored incision. Banks become one block
                // shallower per Manhattan step where possible, preserving a readable cross-section
                // without widening the accepted route.
                int bankRelief = Math.max(0, radius - distance);
                int maximumCarveTop = Math.min(
                        range.maximumY(),
                        waterTopY + 1 + bankRelief);

                for (int y = waterTopY + 1; y <= maximumCarveTop; y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), x, y, z)
                            && !terrain.isSolidOwnedByOtherVolume(volume.id(), x, y, z)) {
                        carved.add(new BlockPos(x, y, z));
                    }
                }
                for (int y = waterBottomY; y <= waterTopY; y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), x, y, z)
                            && !terrain.isSolidOwnedByOtherVolume(volume.id(), x, y, z)) {
                        water.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private static List<Column> rasterizedCenterline(
            SkyIslandWorldVolume volume,
            SkyIslandNaturalizedChannelPath path) {
        LinkedHashSet<Column> columns = new LinkedHashSet<>();
        Column previous = null;
        for (var point : path.points()) {
            int worldX = (int) Math.round(volume.compiledVolume().descriptor().centerX() + point.x());
            int worldZ = (int) Math.round(volume.compiledVolume().descriptor().centerZ() + point.z());
            Column current = new Column(worldX, worldZ);
            if (previous == null) {
                columns.add(current);
            } else {
                appendConnectedColumns(previous, current, columns);
            }
            previous = current;
        }
        return List.copyOf(columns);
    }

    private static void appendConnectedColumns(
            Column from,
            Column to,
            LinkedHashSet<Column> columns) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps == 0) {
            columns.add(to);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            double fraction = (double) step / steps;
            int worldX = from.x() + (int) Math.round(dx * fraction);
            int worldZ = from.z() + (int) Math.round(dz * fraction);
            columns.add(new Column(worldX, worldZ));
        }
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
        return deployment(volume.id(), Feature.RETAINED_WATER, positions, List.of());
    }

    private static Deployment at(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Feature feature,
            double localX,
            double localZ,
            int depth) {
        int x = (int) Math.round(volume.compiledVolume().descriptor().centerX() + localX);
        int z = (int) Math.round(volume.compiledVolume().descriptor().centerZ() + localZ);
        return atWorldColumn(volume, terrain, feature, x, z, depth);
    }

    private static Deployment atWorldColumn(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Feature feature,
            int x,
            int z,
            int depth) {
        List<BlockPos> positions = ownedColumnPositions(volume, terrain, x, z, depth);
        if (positions.isEmpty()) {
            throw new IllegalStateException("AUTH-0086 intent has no realized owner column");
        }
        return deployment(volume.id(), feature, positions, List.of());
    }

    private static List<BlockPos> ownedColumnPositions(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            int x,
            int z,
            int depth) {
        var optionalRange = terrain.integerSolidRange(volume.id(), x, z);
        if (optionalRange.isEmpty()) {
            return List.of();
        }
        var range = optionalRange.orElseThrow();
        List<BlockPos> positions = new ArrayList<>();
        for (int y = range.maximumY(); y >= range.minimumY() && positions.size() < depth; y--) {
            if (terrain.isSolidOwnedBy(volume.id(), x, y, z)
                    && !terrain.isSolidOwnedByOtherVolume(volume.id(), x, y, z)) {
                positions.add(new BlockPos(x, y, z));
            }
        }
        return positions;
    }

    private static Deployment deployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions) {
        return new Deployment(
                volumeId,
                feature,
                new ArrayList<>(new LinkedHashSet<>(positions)),
                new ArrayList<>(new LinkedHashSet<>(carvedPositions)));
    }

    /**
     * Applies terrain response before water. Replay is idempotent: already-carved AIR and existing
     * water produce no new writes.
     */
    static int apply(ChunkAccess chunk, Deployment deployment) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(deployment, "deployment");
        int written = 0;
        for (BlockPos position : deployment.carvedPositions()) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            if (!chunk.getBlockState(position).isAir()) {
                chunk.setBlockState(position, Blocks.AIR.defaultBlockState(), false);
                written++;
            }
        }
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

    private record Column(int x, int z) {}
}
