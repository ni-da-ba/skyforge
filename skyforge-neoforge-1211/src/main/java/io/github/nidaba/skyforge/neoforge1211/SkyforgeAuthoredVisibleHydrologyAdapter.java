package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.world.SkyIslandFluvialReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlan;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    enum Feature { CHANNEL, RETAINED_WATER }

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

        Set<Integer> routedEdgeOutlets = intent.drops().stream()
                .filter(drop -> drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .map(drop -> drop.drop().sourceCellIndex())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (var channel : intent.channels()) {
            boolean routedEdgeOutlet =
                    routedEdgeOutlets.contains(channel.path().profile().segment().downstreamCellIndex());
            atPath(
                            descriptor,
                            fluvial,
                            volume,
                            terrain,
                            channel.path(),
                            routedEdgeOutlet)
                    .ifPresent(deployments::add);
        }
        for (var retained : intent.retainedWater()) {
            atFootprint(descriptor, volume, terrain, retained.footprint())
                    .ifPresent(deployments::add);
        }

        // Drop events remain authored geomorphic semantics. Their cascade/waterfall shaping is
        // already consumed by the fluvial terrain field. Literal Minecraft fluid authority comes
        // only from connected routed channels or retained basins; a drop must never manufacture an
        // independent source column disconnected from its upstream watercourse.

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

    private static Optional<Deployment> atPath(
            SkyIslandDescriptor descriptor,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandNaturalizedChannelPath path,
            boolean routedEdgeOutlet) {
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

        Map<Column, ChannelColumnPlan> columns = new LinkedHashMap<>();
        for (var candidate : candidateColumnDistances(volume, reach).entrySet()) {
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
                loweringBlocks = Math.max(2, loweringBlocks);
            }
            int drySurfaceY = Math.max(range.minimumY(), baseSurfaceY - loweringBlocks);

            int waterTopY = Integer.MIN_VALUE;
            if (authoredWater.isPresent() && drySurfaceY <= baseSurfaceY - 2) {
                double waterDelta = authoredWater.orElseThrow() - basePotential;
                int projected = baseSurfaceY + physicalSignedDeltaBlocks(descriptor, waterDelta);
                waterTopY = Math.max(
                        drySurfaceY + 1,
                        Math.min(baseSurfaceY - 1, projected));
            }
            columns.put(
                    column,
                    new ChannelColumnPlan(column, distance, baseSurfaceY, drySurfaceY, waterTopY));
        }

        Set<Column> containedWet = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            if (column.waterTopY() != Integer.MIN_VALUE
                    && laterallyContained(
                            volume,
                            terrain,
                            columns,
                            column,
                            reach,
                            path,
                            routedEdgeOutlet)) {
                containedWet.add(column.column());
            }
        }
        containedWet = largestConnectedFootprint(containedWet);
        if (containedWet.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            if (column.distance() <= reach.bankfullHalfWidth()
                    && terrain.isSolidOwnedBy(
                            volume.id(), column.column().x(), column.drySurfaceY(), column.column().z())
                    && !terrain.isSolidOwnedByOtherVolume(
                            volume.id(), column.column().x(), column.drySurfaceY(), column.column().z())) {
                surface.add(new BlockPos(
                        column.column().x(), column.drySurfaceY(), column.column().z()));
            }

            boolean wet = containedWet.contains(column.column());
            int carveFrom = column.drySurfaceY() + 1;
            int carveTo = column.baseSurfaceY();
            if (wet) {
                for (int y = column.drySurfaceY() + 1; y <= column.waterTopY(); y++) {
                    if (terrain.isSolidOwnedBy(volume.id(), column.column().x(), y, column.column().z())
                            && !terrain.isSolidOwnedByOtherVolume(
                                    volume.id(), column.column().x(), y, column.column().z())) {
                        water.add(new BlockPos(column.column().x(), y, column.column().z()));
                    }
                }
                carveFrom = column.waterTopY() + 1;
            }
            for (int y = carveFrom; y <= carveTo; y++) {
                if (terrain.isSolidOwnedBy(volume.id(), column.column().x(), y, column.column().z())
                        && !terrain.isSolidOwnedByOtherVolume(
                                volume.id(), column.column().x(), y, column.column().z())) {
                    carved.add(new BlockPos(column.column().x(), y, column.column().z()));
                }
            }
        }

        if (water.isEmpty()) {
            return Optional.empty();
        }
        carved.removeAll(water);
        return Optional.of(deployment(
                volume.id(),
                Feature.CHANNEL,
                new ArrayList<>(water),
                new ArrayList<>(carved),
                new ArrayList<>(surface)));
    }

    private static boolean laterallyContained(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, ChannelColumnPlan> columns,
            ChannelColumnPlan candidate,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path,
            boolean routedEdgeOutlet) {
        int breaches = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            Column neighbor = new Column(
                    candidate.column().x() + direction[0],
                    candidate.column().z() + direction[1]);
            ChannelColumnPlan planned = columns.get(neighbor);
            if (planned != null) {
                if (planned.waterTopY() != Integer.MIN_VALUE
                        || planned.drySurfaceY() >= candidate.waterTopY()) {
                    continue;
                }
            } else if (terrain.isSolidOwnedBy(
                            volume.id(), neighbor.x(), candidate.waterTopY(), neighbor.z())
                    && !terrain.isSolidOwnedByOtherVolume(
                            volume.id(), neighbor.x(), candidate.waterTopY(), neighbor.z())) {
                continue;
            }
            breaches++;
        }
        if (breaches == 0) {
            return true;
        }
        if (!routedEdgeOutlet || breaches != 1) {
            return false;
        }
        SkyIslandLocalPosition local = localPosition(volume, candidate.column());
        SkyIslandLocalPosition outlet = path.points().getLast();
        return Math.hypot(local.x() - outlet.x(), local.z() - outlet.z())
                <= Math.max(1.5, reach.wetHalfWidth() * 0.75);
    }

    private static Set<Column> largestConnectedFootprint(Set<Column> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<Column> unvisited = new LinkedHashSet<>(candidates);
        Set<Column> largest = Set.of();
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        while (!unvisited.isEmpty()) {
            Column seed = unvisited.iterator().next();
            var queue = new ArrayDeque<Column>();
            var component = new LinkedHashSet<Column>();
            queue.add(seed);
            unvisited.remove(seed);
            while (!queue.isEmpty()) {
                Column current = queue.removeFirst();
                component.add(current);
                for (int[] direction : directions) {
                    Column neighbor = new Column(
                            current.x() + direction[0],
                            current.z() + direction[1]);
                    if (unvisited.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            if (component.size() > largest.size()) {
                largest = Set.copyOf(component);
            }
        }
        return largest;
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
     * Returns exactly the integer columns within one reach's valley corridor, together with each
     * column's minimum distance to the authored path.
     *
     * <p>The former implementation scanned the path's complete expanded bounding rectangle and then
     * compared every column against every segment. A long diagonal or meandering reach therefore
     * paid for large areas that were nowhere near the channel. Segment-local rasterization is
     * geometrically equivalent: a point lies within the path corridor iff it lies within the
     * expanded bounding box of at least one segment and its exact point-to-segment distance is
     * within the valley half-width. Distances are merged by minimum and returned in canonical z/x
     * order so deployment ordering remains unchanged.
     */
    private static Map<Column, Double> candidateColumnDistances(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialReachGeometry reach) {
        var points = reach.path().points();
        if (points.size() < 2) {
            return Map.of();
        }

        double margin = reach.valleyHalfWidth();
        var physical = volume.compiledVolume().descriptor();
        Map<Column, Double> distances = new HashMap<>();

        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition a = points.get(index - 1);
            SkyIslandLocalPosition b = points.get(index);

            int minimumX = (int) Math.ceil(Math.max(
                    volume.bounds().minimumX(),
                    physical.centerX() + Math.min(a.x(), b.x()) - margin));
            int maximumX = (int) Math.floor(Math.min(
                    volume.bounds().maximumX(),
                    physical.centerX() + Math.max(a.x(), b.x()) + margin));
            int minimumZ = (int) Math.ceil(Math.max(
                    volume.bounds().minimumZ(),
                    physical.centerZ() + Math.min(a.z(), b.z()) - margin));
            int maximumZ = (int) Math.floor(Math.min(
                    volume.bounds().maximumZ(),
                    physical.centerZ() + Math.max(a.z(), b.z()) + margin));

            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int x = minimumX; x <= maximumX; x++) {
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                            x - physical.centerX(),
                            z - physical.centerZ());
                    double distance = distanceToSegment(local, a, b);
                    if (distance > margin) {
                        continue;
                    }
                    distances.merge(new Column(x, z), distance, Math::min);
                }
            }
        }

        if (distances.isEmpty()) {
            return Map.of();
        }

        var ordered = new ArrayList<>(distances.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<Column, Double> entry) -> entry.getKey().z())
                .thenComparingInt(entry -> entry.getKey().x()));
        Map<Column, Double> canonical = new LinkedHashMap<>();
        for (var entry : ordered) {
            canonical.put(entry.getKey(), entry.getValue());
        }
        return canonical;
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

    private static SkyIslandLocalPosition localPosition(
            SkyIslandWorldVolume volume,
            Column column) {
        var physical = volume.compiledVolume().descriptor();
        return new SkyIslandLocalPosition(
                column.x() - physical.centerX(),
                column.z() - physical.centerZ());
    }

    private static Optional<Deployment> atFootprint(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWaterbodyFootprint footprint) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(footprint, "footprint");

        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex = new LinkedHashMap<>();
        for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
            cellsByIndex.put(cell.watershedCellIndex(), cell);
        }

        // A retained waterbody owns the area represented by its accepted watershed cells, not one
        // rounded Minecraft column at each coarse cell center. Rasterize those regular watershed
        // cells into exact-volume columns while retaining the accepted cell identity.
        Map<Column, RetainedColumnPlan> columns = retainedCandidateColumns(
                descriptor, volume, terrain, watershed, cellsByIndex);
        if (columns.isEmpty() || !coversEveryRetainedCell(columns.values(), cellsByIndex.keySet())) {
            return Optional.empty();
        }

        // The authored descriptor and the independently compiled physical carrier deliberately do
        // not promise an absolute world-Y isomorphism. A lake therefore cannot map each coarse
        // sample to an independent Y without becoming stepped source blocks. Choose the highest
        // single recessed plane that every represented physical column can own. Relative authored
        // water depth still controls the basin bed beneath that common surface.
        int minimumSurfaceY = columns.values().stream()
                .mapToInt(RetainedColumnPlan::baseSurfaceY)
                .min()
                .orElseThrow();
        int waterTopY = minimumSurfaceY - 1;
        if (columns.values().stream().anyMatch(column -> waterTopY <= column.minimumY())) {
            return Optional.empty();
        }

        Map<Column, RetainedColumnPlan> realizable = new LinkedHashMap<>();
        for (RetainedColumnPlan column : columns.values()) {
            int depthBlocks = Math.max(
                    1,
                    physicalLoweringBlocks(descriptor, column.sourceCell().waterDepthPotential()));
            int bedY = Math.max(column.minimumY(), waterTopY - depthBlocks);
            if (bedY >= waterTopY
                    || !ownedSolidInterval(
                            volume,
                            terrain,
                            column.column(),
                            bedY,
                            column.baseSurfaceY())) {
                continue;
            }
            realizable.put(
                    column.column(),
                    new RetainedColumnPlan(
                            column.column(),
                            column.sourceCell(),
                            column.minimumY(),
                            column.baseSurfaceY(),
                            bedY));
        }

        if (realizable.isEmpty()
                || !coversEveryRetainedCell(realizable.values(), cellsByIndex.keySet())) {
            return Optional.empty();
        }

        Set<Column> connected = largestConnectedFootprint(realizable.keySet());
        if (connected.size() != realizable.size()) {
            // Retained water has no channel-style projection authority to discard a disconnected
            // lobe. If the complete accepted footprint cannot become one physical waterbody, fail
            // closed instead of silently changing the basin.
            return Optional.empty();
        }
        for (RetainedColumnPlan column : realizable.values()) {
            if (!retainedWaterContained(
                    volume, terrain, realizable.keySet(), column.column(), waterTopY)) {
                return Optional.empty();
            }
        }

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>();
        for (RetainedColumnPlan column : realizable.values()) {
            surface.add(new BlockPos(column.column().x(), column.bedY(), column.column().z()));
            for (int y = column.bedY() + 1; y <= waterTopY; y++) {
                water.add(new BlockPos(column.column().x(), y, column.column().z()));
            }
            for (int y = waterTopY + 1; y <= column.baseSurfaceY(); y++) {
                carved.add(new BlockPos(column.column().x(), y, column.column().z()));
            }
        }
        if (water.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(deployment(
                volume.id(),
                Feature.RETAINED_WATER,
                new ArrayList<>(water),
                new ArrayList<>(carved),
                new ArrayList<>(surface)));
    }

    private static Map<Column, RetainedColumnPlan> retainedCandidateColumns(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWatershedPlan watershed,
            Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex) {
        double halfSpacing = watershed.spacing() * 0.5;
        double minimumLocalX = cellsByIndex.values().stream()
                .mapToDouble(cell -> cell.position().x())
                .min()
                .orElseThrow() - halfSpacing;
        double maximumLocalX = cellsByIndex.values().stream()
                .mapToDouble(cell -> cell.position().x())
                .max()
                .orElseThrow() + halfSpacing;
        double minimumLocalZ = cellsByIndex.values().stream()
                .mapToDouble(cell -> cell.position().z())
                .min()
                .orElseThrow() - halfSpacing;
        double maximumLocalZ = cellsByIndex.values().stream()
                .mapToDouble(cell -> cell.position().z())
                .max()
                .orElseThrow() + halfSpacing;

        var physical = volume.compiledVolume().descriptor();
        int minimumX = (int) Math.ceil(Math.max(
                volume.bounds().minimumX(), physical.centerX() + minimumLocalX));
        int maximumX = (int) Math.floor(Math.min(
                volume.bounds().maximumX(), physical.centerX() + maximumLocalX));
        int minimumZ = (int) Math.ceil(Math.max(
                volume.bounds().minimumZ(), physical.centerZ() + minimumLocalZ));
        int maximumZ = (int) Math.floor(Math.min(
                volume.bounds().maximumZ(), physical.centerZ() + maximumLocalZ));

        Map<Column, RetainedColumnPlan> result = new LinkedHashMap<>();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - physical.centerX(), z - physical.centerZ());
                int cellIndex = nearestWatershedCellIndex(descriptor, watershed, local);
                SkyIslandWaterbodyFootprintCell sourceCell = cellsByIndex.get(cellIndex);
                if (sourceCell == null) {
                    continue;
                }
                var optionalRange = terrain.integerSolidRange(volume.id(), x, z);
                if (optionalRange.isEmpty()) {
                    continue;
                }
                var range = optionalRange.orElseThrow();
                if (!terrain.isSolidOwnedBy(volume.id(), x, range.maximumY(), z)
                        || terrain.isSolidOwnedByOtherVolume(volume.id(), x, range.maximumY(), z)) {
                    continue;
                }
                Column column = new Column(x, z);
                result.put(
                        column,
                        new RetainedColumnPlan(
                                column,
                                sourceCell,
                                range.minimumY(),
                                range.maximumY(),
                                Integer.MIN_VALUE));
            }
        }
        return result;
    }

    private static int nearestWatershedCellIndex(
            SkyIslandDescriptor descriptor,
            SkyIslandWatershedPlan watershed,
            SkyIslandLocalPosition local) {
        double radius = descriptor.nominalRadius();
        int gx = (int) Math.round((local.x() + radius) / watershed.spacing());
        int gz = (int) Math.round((local.z() + radius) / watershed.spacing());
        gx = Math.max(0, Math.min(watershed.gridSize() - 1, gx));
        gz = Math.max(0, Math.min(watershed.gridSize() - 1, gz));
        return gz * watershed.gridSize() + gx;
    }

    private static boolean coversEveryRetainedCell(
            Iterable<RetainedColumnPlan> columns,
            Set<Integer> requiredCellIndices) {
        Set<Integer> represented = new HashSet<>();
        for (RetainedColumnPlan column : columns) {
            represented.add(column.sourceCell().watershedCellIndex());
        }
        return represented.containsAll(requiredCellIndices);
    }

    private static boolean ownedSolidInterval(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Column column,
            int minimumY,
            int maximumY) {
        for (int y = minimumY; y <= maximumY; y++) {
            if (!terrain.isSolidOwnedBy(volume.id(), column.x(), y, column.z())
                    || terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), y, column.z())) {
                return false;
            }
        }
        return true;
    }

    private static boolean retainedWaterContained(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Set<Column> wetColumns,
            Column candidate,
            int waterTopY) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            Column neighbor = new Column(
                    candidate.x() + direction[0],
                    candidate.z() + direction[1]);
            if (wetColumns.contains(neighbor)) {
                continue;
            }
            if (!terrain.isSolidOwnedBy(volume.id(), neighbor.x(), waterTopY, neighbor.z())
                    || terrain.isSolidOwnedByOtherVolume(volume.id(), neighbor.x(), waterTopY, neighbor.z())) {
                return false;
            }
        }
        return true;
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

    private record ChannelColumnPlan(
            Column column,
            double distance,
            int baseSurfaceY,
            int drySurfaceY,
            int waterTopY) {}

    private record RetainedColumnPlan(
            Column column,
            SkyIslandWaterbodyFootprintCell sourceCell,
            int minimumY,
            int baseSurfaceY,
            int bedY) {}

    private record Column(int x, int z) {}
}
