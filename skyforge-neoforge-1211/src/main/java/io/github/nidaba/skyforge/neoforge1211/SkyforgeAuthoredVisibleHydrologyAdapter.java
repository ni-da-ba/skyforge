package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.world.SkyIslandFluvialReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
        SkyIslandFluvialTerrainField fluvial =
                SkyIslandFluvialTerrainField.create(descriptor, intent.coherentHydrology());
        List<Deployment> deployments = new ArrayList<>();

        for (var channel : intent.channels()) {
            deployments.add(atPath(
                    descriptor,
                    fluvial,
                    volume,
                    terrain,
                    Feature.CHANNEL,
                    channel.path()));
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
            for (Deployment deployment : terrain.authoredHydrologyDeployments(volume.id())) {
                written += apply(chunk, deployment);
            }
        }
        return written;
    }

    private static Deployment atPath(
            SkyIslandDescriptor descriptor,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Feature feature,
            SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(fluvial, "fluvial");
        Objects.requireNonNull(path, "path");
        if (path.points().isEmpty()) {
            throw new IllegalArgumentException("authored channel path requires at least one point");
        }

        SkyIslandFluvialReachGeometry reach = fluvial.reaches().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "AUTH-0105 fluvial field lost accepted visible channel reach"));

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        for (Column column : candidateColumns(volume, reach)) {
            SkyIslandLocalPosition local = localPosition(volume, column);
            if (distanceToPath(local, path) > reach.valleyHalfWidth()) {
                continue;
            }
            var optionalRange = terrain.integerSolidRange(volume.id(), column.x(), column.z());
            if (optionalRange.isEmpty()) {
                continue;
            }
            var range = optionalRange.orElseThrow();
            double basePotential = fluvial.baseTerrain().sample(local);
            double dryPotential = fluvial.sample(local);
            double lowering = Math.max(0.0, basePotential - dryPotential);
            if (lowering <= 1.0e-12) {
                continue;
            }

            int baseSurfaceY = range.maximumY();
            int drySurfaceY = Math.max(
                    range.minimumY(),
                    baseSurfaceY - physicalLoweringBlocks(descriptor, lowering));

            var authoredWater = distanceToPath(local, path) <= reach.wetHalfWidth()
                    ? fluvial.waterSurfacePotential(local)
                    : java.util.OptionalDouble.empty();
            int waterTopY = Integer.MIN_VALUE;
            if (authoredWater.isPresent() && drySurfaceY < baseSurfaceY) {
                double waterDelta = authoredWater.orElseThrow() - basePotential;
                int projected = baseSurfaceY + physicalSignedDeltaBlocks(descriptor, waterDelta);
                waterTopY = Math.max(
                        drySurfaceY + 1,
                        Math.min(baseSurfaceY - 1, projected));
            }

            if (waterTopY != Integer.MIN_VALUE) {
                for (int y = drySurfaceY + 1; y <= waterTopY; y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), column.x(), y, column.z())
                            && !terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), y, column.z())) {
                        water.add(new BlockPos(column.x(), y, column.z()));
                    }
                }
                for (int y = waterTopY + 1; y <= baseSurfaceY; y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), column.x(), y, column.z())
                            && !terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), y, column.z())) {
                        carved.add(new BlockPos(column.x(), y, column.z()));
                    }
                }
            } else {
                for (int y = drySurfaceY + 1; y <= baseSurfaceY; y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), column.x(), y, column.z())
                            && !terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), y, column.z())) {
                        carved.add(new BlockPos(column.x(), y, column.z()));
                    }
                }
            }
        }

        if (water.isEmpty()) {
            throw new IllegalStateException("AUTH-0105 channel intent has no realized wet owner columns");
        }
        carved.removeAll(water);
        return deployment(
                volume.id(),
                feature,
                new ArrayList<>(water),
                new ArrayList<>(carved));
    }

    /**
     * Converts authored normalized dry-terrain lowering into Minecraft blocks.
     *
     * <p>The authored descriptor's relief budget is the neutral vertical scale. Any nonzero
     * accepted AUTH-0105 lowering therefore survives integer discretization by at least one block.
     */
    static int physicalLoweringBlocks(
            SkyIslandDescriptor descriptor,
            double normalizedLowering) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(normalizedLowering) || normalizedLowering < 0.0) {
            throw new IllegalArgumentException("normalizedLowering must be finite and nonnegative");
        }
        if (normalizedLowering == 0.0) {
            return 0;
        }
        return Math.max(
                1,
                (int) Math.round(normalizedLowering * descriptor.reliefBudget()));
    }

    static int physicalSignedDeltaBlocks(
            SkyIslandDescriptor descriptor,
            double normalizedDelta) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(normalizedDelta)) {
            throw new IllegalArgumentException("normalizedDelta must be finite");
        }
        return (int) Math.round(normalizedDelta * descriptor.reliefBudget());
    }

    private static LinkedHashSet<Column> candidateColumns(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialReachGeometry reach) {
        double minimumLocalX = Double.POSITIVE_INFINITY;
        double maximumLocalX = Double.NEGATIVE_INFINITY;
        double minimumLocalZ = Double.POSITIVE_INFINITY;
        double maximumLocalZ = Double.NEGATIVE_INFINITY;
        for (SkyIslandLocalPosition point : reach.path().points()) {
            minimumLocalX = Math.min(minimumLocalX, point.x());
            maximumLocalX = Math.max(maximumLocalX, point.x());
            minimumLocalZ = Math.min(minimumLocalZ, point.z());
            maximumLocalZ = Math.max(maximumLocalZ, point.z());
        }
        double margin = reach.valleyHalfWidth();
        var physical = volume.compiledVolume().descriptor();
        int minimumX = (int) Math.ceil(Math.max(
                volume.bounds().minimumX(),
                physical.centerX() + minimumLocalX - margin));
        int maximumX = (int) Math.floor(Math.min(
                volume.bounds().maximumX(),
                physical.centerX() + maximumLocalX + margin));
        int minimumZ = (int) Math.ceil(Math.max(
                volume.bounds().minimumZ(),
                physical.centerZ() + minimumLocalZ - margin));
        int maximumZ = (int) Math.floor(Math.min(
                volume.bounds().maximumZ(),
                physical.centerZ() + maximumLocalZ + margin));

        LinkedHashSet<Column> columns = new LinkedHashSet<>();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                columns.add(new Column(x, z));
            }
        }
        return columns;
    }

    private static SkyIslandLocalPosition localPosition(
            SkyIslandWorldVolume volume,
            Column column) {
        var physical = volume.compiledVolume().descriptor();
        return new SkyIslandLocalPosition(
                column.x() - physical.centerX(),
                column.z() - physical.centerZ());
    }

    private static double distanceToPath(
            SkyIslandLocalPosition position,
            SkyIslandNaturalizedChannelPath path) {
        double best = Double.POSITIVE_INFINITY;
        var points = path.points();
        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition a = points.get(index - 1);
            SkyIslandLocalPosition b = points.get(index);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            if (lengthSquared <= 1.0e-12) {
                best = Math.min(best, Math.hypot(position.x() - a.x(), position.z() - a.z()));
                continue;
            }
            double px = position.x() - a.x();
            double pz = position.z() - a.z();
            double fraction = Math.max(
                    0.0,
                    Math.min(1.0, (px * dx + pz * dz) / lengthSquared));
            double nearestX = a.x() + fraction * dx;
            double nearestZ = a.z() + fraction * dz;
            best = Math.min(
                    best,
                    Math.hypot(position.x() - nearestX, position.z() - nearestZ));
        }
        return best;
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
            if (!isWaterBearing(chunk.getBlockState(position))) {
                chunk.setBlockState(position, Blocks.WATER.defaultBlockState(), false);
                written++;
            }
        }
        return written;
    }

    /**
     * Runtime invariant for authored visible hydrology after Minecraft fluid simulation settles.
     *
     * <p>Literal WATER, FLOWING_WATER, and water-bearing states such as BUBBLE_COLUMN are all valid
     * physical realizations of the same authored wet cell. Air, lava, and unrelated blocks are not.
     */
    static boolean isWaterBearing(BlockState state) {
        Objects.requireNonNull(state, "state");
        var fluid = state.getFluidState().getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private record Column(int x, int z) {}
}
