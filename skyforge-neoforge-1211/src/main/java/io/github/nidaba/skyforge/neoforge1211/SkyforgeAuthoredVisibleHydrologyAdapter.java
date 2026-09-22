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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions) {
        Deployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            positions = List.copyOf(positions);
            carvedPositions = List.copyOf(carvedPositions);
            surfacePositions = List.copyOf(surfacePositions);
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("hydrology deployment requires owned water positions");
            }
            var overlap = new java.util.HashSet<>(positions);
            overlap.retainAll(carvedPositions);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("hydrology water and dry carved positions must be disjoint");
            }
            var surfaceOverlap = new java.util.HashSet<>(surfacePositions);
            surfaceOverlap.retainAll(positions);
            surfaceOverlap.addAll(surfacePositions.stream()
                    .filter(carvedPositions::contains)
                    .toList());
            if (!surfaceOverlap.isEmpty()) {
                throw new IllegalArgumentException(
                        "hydrology surface dressing must remain below wet and carved cells");
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
        var allCarved = deployments.stream()
                .flatMap(deployment -> deployment.carvedPositions().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return deployments.stream()
                .map(deployment -> new Deployment(
                        deployment.volumeId(),
                        deployment.feature(),
                        deployment.positions(),
                        deployment.carvedPositions().stream()
                                .filter(position -> !allWater.contains(position))
                                .toList(),
                        deployment.surfacePositions().stream()
                                .filter(position -> !allWater.contains(position))
                                .filter(position -> !allCarved.contains(position))
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
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>();
        var candidates = candidateColumnDistances(volume, reach).entrySet().stream()
                .sorted(Comparator
                        .comparingInt((java.util.Map.Entry<Column, Double> entry) -> entry.getKey().z())
                        .thenComparingInt(entry -> entry.getKey().x()))
                .toList();
        for (var candidate : candidates) {
            Column column = candidate.getKey();
            SkyIslandLocalPosition local = localPosition(volume, column);
            double distance = candidate.getValue();
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
            var authoredWater = distance <= reach.wetHalfWidth()
                    ? fluvial.waterSurfacePotential(local)
                    : java.util.OptionalDouble.empty();

            int loweringBlocks = physicalLoweringBlocks(descriptor, lowering);
            if (authoredWater.isPresent()) {
                // A wet Minecraft cross-section needs one solid bed level plus at least one
                // water block below the pre-fluvial bank surface. A one-block carve cannot
                // satisfy both constraints and would put water back at the original surface.
                loweringBlocks = Math.max(2, loweringBlocks);
            }
            int drySurfaceY = Math.max(
                    range.minimumY(),
                    baseSurfaceY - loweringBlocks);

            if (distance <= reach.bankfullHalfWidth()
                    && terrain.isSolidOwnedBy(volume.id(), column.x(), drySurfaceY, column.z())
                    && !terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), drySurfaceY, column.z())) {
                surface.add(new BlockPos(column.x(), drySurfaceY, column.z()));
            }

            int waterTopY = Integer.MIN_VALUE;
            if (authoredWater.isPresent() && drySurfaceY <= baseSurfaceY - 2) {
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
                new ArrayList<>(carved),
                new ArrayList<>(surface));
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

    /**
     * Enumerates the exact valley corridor segment-by-segment instead of scanning one large reach
     * bounding rectangle and comparing every column against the full polyline. For every physical
     * column touched by one or more segment envelopes, the retained value is the exact minimum
     * Euclidean distance to the accepted naturalized path.
     */
    private static LinkedHashMap<Column, Double> candidateColumnDistances(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialReachGeometry reach) {
        LinkedHashMap<Column, Double> distances = new LinkedHashMap<>();
        var physical = volume.compiledVolume().descriptor();
        double centerX = physical.centerX();
        double centerZ = physical.centerZ();
        double margin = reach.valleyHalfWidth();
        var points = reach.path().points();

        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition a = points.get(index - 1);
            SkyIslandLocalPosition b = points.get(index);
            int minimumX = (int) Math.ceil(Math.max(
                    volume.bounds().minimumX(),
                    centerX + Math.min(a.x(), b.x()) - margin));
            int maximumX = (int) Math.floor(Math.min(
                    volume.bounds().maximumX(),
                    centerX + Math.max(a.x(), b.x()) + margin));
            int minimumZ = (int) Math.ceil(Math.max(
                    volume.bounds().minimumZ(),
                    centerZ + Math.min(a.z(), b.z()) - margin));
            int maximumZ = (int) Math.floor(Math.min(
                    volume.bounds().maximumZ(),
                    centerZ + Math.max(a.z(), b.z()) + margin));

            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int x = minimumX; x <= maximumX; x++) {
                    SkyIslandLocalPosition local =
                            new SkyIslandLocalPosition(x - centerX, z - centerZ);
                    double distance = distanceToSegment(local, a, b);
                    if (distance > margin) {
                        continue;
                    }
                    distances.merge(new Column(x, z), distance, Math::min);
                }
            }
        }
        return distances;
    }

    private static SkyIslandLocalPosition localPosition(
            SkyIslandWorldVolume volume,
            Column column) {
        var physical = volume.compiledVolume().descriptor();
        return new SkyIslandLocalPosition(
                column.x() - physical.centerX(),
                column.z() - physical.centerZ());
    }

    private static double distanceToSegment(
            SkyIslandLocalPosition position,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= 1.0e-12) {
            return Math.hypot(position.x() - a.x(), position.z() - a.z());
        }
        double px = position.x() - a.x();
        double pz = position.z() - a.z();
        double fraction = Math.max(
                0.0,
                Math.min(1.0, (px * dx + pz * dz) / lengthSquared));
        double nearestX = a.x() + fraction * dx;
        double nearestZ = a.z() + fraction * dz;
        return Math.hypot(position.x() - nearestX, position.z() - nearestZ);
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
        return deployment(volume.id(), Feature.RETAINED_WATER, positions, List.of(), List.of());
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
        return deployment(volume.id(), feature, positions, List.of(), List.of());
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
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions) {
        return new Deployment(
                volumeId,
                feature,
                new ArrayList<>(new LinkedHashSet<>(positions)),
                new ArrayList<>(new LinkedHashSet<>(carvedPositions)),
                new ArrayList<>(new LinkedHashSet<>(surfacePositions)));
    }

    /**
     * Applies terrain response before water. Replay is idempotent: already-carved AIR and existing
     * water produce no new writes.
     */
    static int apply(ChunkAccess chunk, Deployment deployment) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(deployment, "deployment");
        int written = 0;
        for (BlockPos position : deployment.surfacePositions()) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            // AUTH-0105 requires material dressing after dry terrain projection. Reuse the
            // accepted Minecraft carrier for Skyforge SURFACE_MANTLE rather than inheriting
            // an unrelated native-ocean top block into the fluvial bed/bank corridor.
            if (!chunk.getBlockState(position).is(Blocks.DIRT)) {
                chunk.setBlockState(position, Blocks.DIRT.defaultBlockState(), false);
                written++;
            }
        }
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
