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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
    static final int MAX_RETAINED_BASIN_CUT_BLOCKS = 3;
    // Hard safety ceiling only. The isotonic solver searches from zero upward and uses the
    // smallest additional submerged bed cut that admits a contained non-climbing profile.
    static final int MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS = 16;
    static final int MAX_RETAINED_BANK_FILL_BLOCKS = 3;

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
            // Preserve the retained plan even if a package-local caller supplies mutable lists.
            // List.copyOf can reuse JDK immutable planner inputs without exposing caller mutation.
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
            carvedPositions = List.copyOf(Objects.requireNonNull(carvedPositions, "carvedPositions"));
            surfacePositions = List.copyOf(Objects.requireNonNull(surfacePositions, "surfacePositions"));
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("hydrology deployment requires owned water positions");
            }
            // These collections can contain hundreds of thousands of Minecraft positions.
            // Validate role disjointness with one membership set. Duplicates inside one role retain
            // the historical permissive behavior; only cross-role overlap is illegal.
            Set<BlockPos> occupied = new HashSet<>(positions);
            for (BlockPos carved : carvedPositions) {
                if (occupied.contains(carved)) {
                    throw new IllegalArgumentException(
                            "hydrology water and dry carved positions must be disjoint");
                }
            }
            occupied.addAll(carvedPositions);
            for (BlockPos surface : surfacePositions) {
                if (occupied.contains(surface)) {
                    throw new IllegalArgumentException(
                            "hydrology surface dressing must remain below wet and carved cells");
                }
            }
        }
    }

    /**
     * Mutable-free intermediate used only while resolving overlaps between accepted authored
     * features. It avoids constructing/validating a full immutable Deployment twice.
     */
    private record RawDeployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions) {
        private RawDeployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            positions = List.copyOf(positions);
            carvedPositions = List.copyOf(carvedPositions);
            surfacePositions = List.copyOf(surfacePositions);
        }
    }

    /**
     * Immutable Minecraft execution projection for exactly one chunk.
     *
     * <p>All authored roles are disjoint after global normalization, so one block-state map is enough
     * for mutation, fluid-boundary membership, and population-state lookup. Keeping this index
     * chunk-local prevents every chunk realization from rescanning whole-island hydrology lists.
     */
    record ChunkProjection(Map<BlockPos, BlockState> states) {
        ChunkProjection {
            Objects.requireNonNull(states, "states");
            states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        }

        Optional<BlockState> stateAt(BlockPos position) {
            return Optional.ofNullable(states.get(Objects.requireNonNull(position, "position")));
        }

        boolean containsWater(BlockPos position) {
            BlockState state = states.get(Objects.requireNonNull(position, "position"));
            return state != null && state.is(Blocks.WATER);
        }

        Optional<BlockState> populationState(BlockPos position) {
            BlockState state = states.get(Objects.requireNonNull(position, "position"));
            return Optional.ofNullable(state);
        }
    }

    record PlanningResult(
            List<Deployment> deployments,
            SkyIslandFluvialTerrainField fluvial) {
        PlanningResult {
            deployments = List.copyOf(Objects.requireNonNull(deployments, "deployments"));
            fluvial = Objects.requireNonNull(fluvial, "fluvial");
        }
    }

    private SkyforgeAuthoredVisibleHydrologyAdapter() {}

    static List<Deployment> plan(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        return planWithField(descriptor, volume, terrain).deployments();
    }

    static PlanningResult planWithField(
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
        List<RawDeployment> rawDeployments = new ArrayList<>();
        Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache =
                new HashMap<>();
        Map<Column, Double> basePotentialCache = new HashMap<>();
        Map<Column, Double> dryPotentialCache = new HashMap<>();

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
                            routedEdgeOutlet,
                            solidRangeCache,
                            basePotentialCache,
                            dryPotentialCache)
                    .ifPresent(rawDeployments::add);
        }
        if (!intent.retainedWater().isEmpty()) {
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            for (var retained : intent.retainedWater()) {
                atFootprint(
                                descriptor,
                                volume,
                                terrain,
                                watershed,
                                retained.footprint(),
                                solidRangeCache)
                        .ifPresent(rawDeployments::add);
            }
        }

        long projectedChannels = rawDeployments.stream()
                .filter(deployment -> deployment.feature() == Feature.CHANNEL)
                .count();
        long projectedRetainedWater = rawDeployments.stream()
                .filter(deployment -> deployment.feature() == Feature.RETAINED_WATER)
                .count();
        if (projectedChannels != intent.channels().size()
                || projectedRetainedWater != intent.retainedWater().size()) {
            throw new IllegalStateException(
                    "Minecraft hydrology projection lost accepted authored intent: channels="
                            + projectedChannels + "/" + intent.channels().size()
                            + ", retainedWater="
                            + projectedRetainedWater + "/" + intent.retainedWater().size()
                            + ", volume=" + volume.id().path());
        }

        // Drop events remain authored geomorphic semantics. Their cascade/waterfall shaping is
        // already consumed by the fluvial terrain field. Literal Minecraft fluid authority comes
        // only from connected routed channels or retained basins; a drop must never manufacture an
        // independent source column disconnected from its upstream watercourse.

        // Connected reaches can overlap at confluences. Resolve conflicts through chunk-partitioned
        // membership sets instead of one enormous whole-island Set<BlockPos>. The latter scales
        // poorly for production-size basins and needlessly rehashes millions of positions.
        Map<Long, Set<BlockPos>> waterByChunk = new HashMap<>();
        for (RawDeployment deployment : rawDeployments) {
            addMembershipByChunk(waterByChunk, deployment.positions());
        }

        Map<Long, Set<BlockPos>> carvedByChunk = new HashMap<>();
        for (RawDeployment deployment : rawDeployments) {
            for (BlockPos position : deployment.carvedPositions()) {
                if (!containsByChunk(waterByChunk, position)) {
                    addMembershipByChunk(carvedByChunk, position);
                }
            }
        }

        List<Deployment> deployments = new ArrayList<>(rawDeployments.size());
        for (RawDeployment deployment : rawDeployments) {
            List<BlockPos> carved = deployment.carvedPositions().stream()
                    .filter(position -> !containsByChunk(waterByChunk, position))
                    .toList();
            List<BlockPos> surface = deployment.surfacePositions().stream()
                    .filter(position -> !containsByChunk(waterByChunk, position))
                    .filter(position -> !containsByChunk(carvedByChunk, position))
                    .toList();
            deployments.add(new Deployment(
                    deployment.volumeId(),
                    deployment.feature(),
                    deployment.positions(),
                    carved,
                    surface));
        }
        return new PlanningResult(deployments, fluvial);
    }

    /** Applies every authored deployment whose exact cells occur in an already-available chunk. */
    static int applyAvailable(ChunkAccess chunk, SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(terrain, "terrain");
        int written = 0;
        for (SkyIslandWorldVolume volume : terrain.candidateVolumes(chunk)) {
            var projection = terrain.authoredHydrologyChunkProjection(volume.id(), chunk.getPos());
            if (projection.isPresent()) {
                written += apply(chunk, projection.orElseThrow());
            }
        }
        return written;
    }

    private static Optional<RawDeployment> atPath(
            SkyIslandDescriptor descriptor,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandNaturalizedChannelPath path,
            boolean routedEdgeOutlet,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
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

        Map<Column, ChannelPathProjection> candidateProjections =
                candidateColumnProjections(volume, reach);
        if (candidateProjections.isEmpty()) {
            throw channelProjectionFailure(volume, path, "no raster carrier columns");
        }
        Optional<PhysicalChannelGrade> physicalGrade = physicalChannelGrade(
                descriptor,
                volume,
                terrain,
                fluvial,
                reach,
                candidateProjections,
                routedEdgeOutlet,
                solidRangeCache,
                basePotentialCache,
                dryPotentialCache);
        if (physicalGrade.isEmpty()) {
            throw channelProjectionFailure(
                    volume,
                    path,
                    "bounded isotonic grade has no feasible raster-spine solution; candidateColumns="
                            + candidateProjections.size());
        }

        Map<Column, ChannelColumnPlan> columns = new LinkedHashMap<>();
        for (var candidate : candidateProjections.entrySet()) {
            Column column = candidate.getKey();
            SkyIslandLocalPosition local = localPosition(volume, column);
            double distance = candidate.getValue().distance();
            double fraction = candidate.getValue().fraction();
            var optionalRange = solidRangeCache.computeIfAbsent(
                    column,
                    ignored -> terrain.integerSolidRange(volume.id(), column.x(), column.z()));
            if (optionalRange.isEmpty()) {
                continue;
            }
            var range = optionalRange.orElseThrow();
            double basePotential = basePotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.baseTerrain().sample(local));
            double dryPotential = dryPotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.sample(local));
            double lowering = Math.max(0.0, basePotential - dryPotential);
            if (lowering <= 1.0e-12) {
                continue;
            }

            int baseSurfaceY = range.maximumY();
            OptionalDouble authoredWater = OptionalDouble.empty();
            if (distance <= reach.wetHalfWidth()) {
                double reachWater = fluvial.reachWaterSurfacePotential(reach, fraction);
                if (reachWater > dryPotential + 1.0e-12) {
                    authoredWater = OptionalDouble.of(reachWater);
                }
            }
            int loweringBlocks = physicalLoweringBlocks(descriptor, lowering);
            if (authoredWater.isPresent()) {
                loweringBlocks = Math.max(2, loweringBlocks);
            }
            int drySurfaceY = Math.max(range.minimumY(), baseSurfaceY - loweringBlocks);

            int waterTopY = Integer.MIN_VALUE;
            if (authoredWater.isPresent() && drySurfaceY <= baseSurfaceY - 2) {
                int projected = physicalGrade.orElseThrow().sample(fraction);

                // The discrete physical grade is solved from centerline carrier constraints and is
                // non-increasing by construction. Reconcile small independent-carrier mismatches by
                // lowering the narrow channel bed instead of raising/clamping the water surface.
                int requiredBedY = projected - 1;
                int extraCut = Math.max(0, drySurfaceY - requiredBedY);
                if (projected <= baseSurfaceY - 1
                        && projected >= range.minimumY() + 1
                        && extraCut <= physicalGrade.orElseThrow().reconciliationDepth()) {
                    drySurfaceY = Math.max(range.minimumY(), Math.min(drySurfaceY, requiredBedY));
                    waterTopY = projected;
                }
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
                            routedEdgeOutlet,
                            solidRangeCache)) {
                containedWet.add(column.column());
            }
        }
        int plannedWetColumns = (int) columns.values().stream()
                .filter(column -> column.waterTopY() != Integer.MIN_VALUE)
                .count();
        int containedWetColumns = containedWet.size();
        containedWet = largestConnectedFootprint(containedWet);
        if (containedWet.isEmpty()) {
            throw channelProjectionFailure(
                    volume,
                    path,
                    "no laterally contained wet component after bank-aware isotonic grade; "
                            + "candidateColumns=" + candidateProjections.size()
                            + ", plannedWetColumns=" + plannedWetColumns
                            + ", containedWetColumns=" + containedWetColumns);
        }

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            if (column.distance() <= reach.bankfullHalfWidth()
                    && uncontestedOwnedRangeCell(
                            terrain,
                            volume.id(),
                            column.column().x(),
                            column.drySurfaceY(),
                            column.column().z())) {
                surface.add(new BlockPos(
                        column.column().x(), column.drySurfaceY(), column.column().z()));
            }

            boolean wet = containedWet.contains(column.column());
            int carveFrom = column.drySurfaceY() + 1;
            int carveTo = column.baseSurfaceY();
            if (wet) {
                for (int y = column.drySurfaceY() + 1; y <= column.waterTopY(); y++) {
                    if (uncontestedOwnedRangeCell(
                            terrain,
                            volume.id(),
                            column.column().x(),
                            y,
                            column.column().z())) {
                        water.add(new BlockPos(column.column().x(), y, column.column().z()));
                    }
                }
                carveFrom = column.waterTopY() + 1;
            }
            for (int y = carveFrom; y <= carveTo; y++) {
                if (uncontestedOwnedRangeCell(
                        terrain,
                        volume.id(),
                        column.column().x(),
                        y,
                        column.column().z())) {
                    carved.add(new BlockPos(column.column().x(), y, column.column().z()));
                }
            }
        }

        if (water.isEmpty()) {
            throw channelProjectionFailure(
                    volume,
                    path,
                    "contained wet component produced no water cells; connectedWetColumns="
                            + containedWet.size());
        }
        carved.removeAll(water);
        return Optional.of(rawDeployment(
                volume.id(),
                Feature.CHANNEL,
                new ArrayList<>(water),
                new ArrayList<>(carved),
                new ArrayList<>(surface)));
    }

    private static IllegalStateException channelProjectionFailure(
            SkyIslandWorldVolume volume,
            SkyIslandNaturalizedChannelPath path,
            String reason) {
        return new IllegalStateException(
                "accepted channel cannot project: sourceCell="
                        + path.profile().segment().sourceCellIndex()
                        + ", downstreamCell="
                        + path.profile().segment().downstreamCellIndex()
                        + ", reason=" + reason
                        + ", volume=" + volume.id().path());
    }

    private static boolean laterallyContained(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, ChannelColumnPlan> columns,
            ChannelColumnPlan candidate,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path,
            boolean routedEdgeOutlet,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
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
            } else if (ownedSolidAt(
                    volume,
                    terrain,
                    solidRangeCache,
                    neighbor,
                    candidate.waterTopY())) {
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

    static Set<Column> largestConnectedFootprint(Set<Column> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<Column> unvisited = new LinkedHashSet<>(candidates);
        Set<Column> largest = Set.of();
        // Minecraft fluids connect through shared block faces. Corner-touching columns
        // are not one physically connected waterbody.
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
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
                // Component is no longer mutated after this iteration. Retain it directly and copy
                // only the final winner rather than repeatedly rehashing large intermediate sets.
                largest = component;
            }
        }
        return Collections.unmodifiableSet(largest);
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
    /**
     * Builds the integer Minecraft free-surface profile for one authored reach.
     *
     * <p>Each authored path point is registered against its nearest supported centerline column.
     * That produces a preferred local water Y plus a feasible interval bounded above by the
     * untouched carrier surface and below by at most a small additional channel-bed cut. The suffix
     * lower-bound pass and forward solve choose a deterministic non-increasing sequence inside all
     * of those intervals. The resulting profile follows local carrier elevation without ever
     * reintroducing per-column hydraulic steps.
     */
    /**
     * Builds the least-change bounded isotonic projection of one authored reach onto Minecraft Y.
     *
     * <p>The optimization domain is the actual rasterized wet carrier, not only authored path
     * vertices. One canonical column is selected for each approximately one-block longitudinal bin,
     * preferring the column nearest the authored centerline. Each sample contributes a preferred
     * local water Y and a feasible interval bounded above by the untouched carrier and below by the
     * maximum narrow-channel reconciliation cut. Dynamic programming then solves the exact integer
     * least-squares problem subject to a non-increasing downstream grade.
     */
    private static Optional<PhysicalChannelGrade> physicalChannelGrade(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach,
            Map<Column, ChannelPathProjection> candidateProjections,
            boolean routedEdgeOutlet,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
        if (candidateProjections.isEmpty() || !(reach.path().pathLength() > 0.0)) {
            return Optional.empty();
        }

        Map<Integer, ChannelCarrierCandidate> carrierByBin = new java.util.TreeMap<>();
        double pathLength = reach.path().pathLength();
        for (var entry : candidateProjections.entrySet()) {
            ChannelPathProjection projection = entry.getValue();
            if (projection.distance() > reach.wetHalfWidth()) {
                continue;
            }
            Column column = entry.getKey();
            var optionalRange = solidRangeCache.computeIfAbsent(
                    column,
                    ignored -> terrain.integerSolidRange(
                            volume.id(), column.x(), column.z()));
            if (optionalRange.isEmpty()) {
                continue;
            }
            var range = optionalRange.orElseThrow();
            SkyIslandLocalPosition local = localPosition(volume, column);
            double basePotential = basePotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.baseTerrain().sample(local));
            double dryPotential = dryPotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.sample(local));
            double lowering = Math.max(0.0, basePotential - dryPotential);
            if (lowering <= 1.0e-12) {
                continue;
            }

            double waterPotential = fluvial.reachWaterSurfacePotential(
                    reach, projection.fraction());
            if (waterPotential <= dryPotential + 1.0e-12) {
                continue;
            }

            int baseSurfaceY = range.maximumY();
            int loweringBlocks = Math.max(2, physicalLoweringBlocks(descriptor, lowering));
            int drySurfaceY = Math.max(range.minimumY(), baseSurfaceY - loweringBlocks);
            int ownerMinimumWaterTop = range.minimumY() + 1;
            int maximumWaterTop = baseSurfaceY - 1;
            OptionalInt bankCeiling = channelBankCeiling(
                    descriptor,
                    volume,
                    terrain,
                    fluvial,
                    reach,
                    candidateProjections,
                    column,
                    routedEdgeOutlet,
                    solidRangeCache,
                    basePotentialCache,
                    dryPotentialCache);
            if (bankCeiling.isEmpty()) {
                continue;
            }
            maximumWaterTop = Math.min(maximumWaterTop, bankCeiling.orElseThrow());
            if (ownerMinimumWaterTop > maximumWaterTop) {
                continue;
            }

            int desiredWaterTop = baseSurfaceY
                    + physicalSignedDeltaBlocks(
                            descriptor,
                            waterPotential - basePotential);
            desiredWaterTop = Math.max(
                    ownerMinimumWaterTop,
                    Math.min(maximumWaterTop, desiredWaterTop));

            int bin = (int) Math.round(projection.fraction() * pathLength);
            ChannelCarrierCandidate candidate = new ChannelCarrierCandidate(
                    column,
                    projection.distance(),
                    projection.fraction(),
                    ownerMinimumWaterTop,
                    drySurfaceY,
                    maximumWaterTop,
                    desiredWaterTop);
            ChannelCarrierCandidate previous = carrierByBin.get(bin);
            if (previous == null
                    || candidate.distance() < previous.distance() - 1.0e-12
                    || (Math.abs(candidate.distance() - previous.distance()) <= 1.0e-12
                            && (candidate.column().z() < previous.column().z()
                                    || (candidate.column().z() == previous.column().z()
                                            && candidate.column().x() < previous.column().x())))) {
                carrierByBin.put(bin, candidate);
            }
        }

        if (carrierByBin.isEmpty()) {
            return Optional.empty();
        }

        for (int reconciliationDepth = 0;
                reconciliationDepth <= MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS;
                reconciliationDepth++) {
            List<ChannelGradeSample> samples = new ArrayList<>(carrierByBin.size());
            boolean feasibleBounds = true;
            for (ChannelCarrierCandidate candidate : carrierByBin.values()) {
                int minimumWaterTop = Math.max(
                        candidate.ownerMinimumWaterTop(),
                        candidate.drySurfaceY() + 1 - reconciliationDepth);
                if (minimumWaterTop > candidate.maximumWaterTop()) {
                    feasibleBounds = false;
                    break;
                }
                samples.add(new ChannelGradeSample(
                        candidate.fraction(),
                        minimumWaterTop,
                        candidate.maximumWaterTop(),
                        candidate.desiredWaterTop()));
            }
            if (!feasibleBounds) {
                continue;
            }

            Optional<List<Integer>> solved = solveBoundedNonIncreasingGrade(samples);
            if (solved.isEmpty()) {
                continue;
            }

            List<Double> fractions = samples.stream()
                    .map(ChannelGradeSample::fraction)
                    .toList();
            return Optional.of(new PhysicalChannelGrade(
                    fractions,
                    solved.orElseThrow(),
                    reconciliationDepth));
        }
        return Optional.empty();
    }

    /**
     * Exact integer bounded isotonic regression for a downstream-non-increasing profile.
     *
     * <p>Minimizes sum((y_i - desired_i)^2) subject to lower_i <= y_i <= upper_i and
     * y_i >= y_(i+1). The Minecraft build-height span is small, so an O(samples * Y-span)
     * dynamic program is simpler and more robust than a bespoke floating-point active-set solver.
     */
    static Optional<List<Integer>> solveBoundedNonIncreasingGrade(
            List<ChannelGradeSample> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) {
            return Optional.empty();
        }

        int minimumY = samples.stream()
                .mapToInt(ChannelGradeSample::minimumWaterTop)
                .min()
                .orElseThrow();
        int maximumY = samples.stream()
                .mapToInt(ChannelGradeSample::maximumWaterTop)
                .max()
                .orElseThrow();
        if (minimumY > maximumY) {
            return Optional.empty();
        }

        int span = maximumY - minimumY + 1;
        long infinite = Long.MAX_VALUE / 8;
        long[] previous = new long[span];
        java.util.Arrays.fill(previous, infinite);
        int[][] predecessor = new int[samples.size()][span];
        for (int[] row : predecessor) {
            java.util.Arrays.fill(row, -1);
        }

        ChannelGradeSample first = samples.getFirst();
        for (int y = first.minimumWaterTop(); y <= first.maximumWaterTop(); y++) {
            long delta = (long) y - first.desiredWaterTop();
            previous[y - minimumY] = delta * delta;
        }

        for (int index = 1; index < samples.size(); index++) {
            long[] suffixCost = new long[span];
            int[] suffixArg = new int[span];
            long bestCost = infinite;
            int bestY = -1;
            for (int offset = span - 1; offset >= 0; offset--) {
                long cost = previous[offset];
                if (cost < bestCost) {
                    bestCost = cost;
                    bestY = minimumY + offset;
                }
                suffixCost[offset] = bestCost;
                suffixArg[offset] = bestY;
            }

            long[] current = new long[span];
            java.util.Arrays.fill(current, infinite);
            ChannelGradeSample sample = samples.get(index);
            for (int y = sample.minimumWaterTop(); y <= sample.maximumWaterTop(); y++) {
                int offset = y - minimumY;
                if (suffixCost[offset] >= infinite || suffixArg[offset] < 0) {
                    continue;
                }
                long delta = (long) y - sample.desiredWaterTop();
                current[offset] = suffixCost[offset] + delta * delta;
                predecessor[index][offset] = suffixArg[offset];
            }
            previous = current;
        }

        long bestCost = infinite;
        int terminalY = -1;
        for (int offset = 0; offset < span; offset++) {
            if (previous[offset] < bestCost) {
                bestCost = previous[offset];
                terminalY = minimumY + offset;
            }
        }
        if (terminalY < 0) {
            return Optional.empty();
        }

        int[] solved = new int[samples.size()];
        solved[solved.length - 1] = terminalY;
        for (int index = solved.length - 1; index > 0; index--) {
            int previousY = predecessor[index][solved[index] - minimumY];
            if (previousY < 0) {
                return Optional.empty();
            }
            solved[index - 1] = previousY;
        }

        List<Integer> result = new ArrayList<>(solved.length);
        for (int y : solved) {
            result.add(y);
        }
        return Optional.of(List.copyOf(result));
    }

    /**
     * Maximum water Y supportable by the existing/carved cardinal banks around one wet raster cell.
     *
     * <p>Neighbors inside the authored wet corridor are expected to carry water too. Neighbors
     * outside it constrain the water surface to their post-fluvial dry surface (or their untouched
     * owner surface when the reach does not shape that column). No new solid bank authority is
     * introduced here.
     */
    private static OptionalInt channelBankCeiling(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach,
            Map<Column, ChannelPathProjection> candidateProjections,
            Column wet,
            boolean routedEdgeOutlet,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
        int ceiling = Integer.MAX_VALUE;
        int outletBreaches = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] direction : directions) {
            Column bank = new Column(wet.x() + direction[0], wet.z() + direction[1]);
            ChannelPathProjection bankProjection = candidateProjections.get(bank);
            if (bankProjection != null && bankProjection.distance() <= reach.wetHalfWidth()) {
                continue;
            }

            boolean outletBreach = routedEdgeOutlet
                    && outletBreaches == 0
                    && channelOutletBreachAllowed(volume, wet, reach, reach.path());
            var optionalRange = solidRangeCache.computeIfAbsent(
                    bank,
                    ignored -> terrain.integerSolidRange(
                            volume.id(), bank.x(), bank.z()));
            if (optionalRange.isEmpty()) {
                if (outletBreach) {
                    outletBreaches++;
                    continue;
                }
                return OptionalInt.empty();
            }

            var range = optionalRange.orElseThrow();
            int bankTopY = range.maximumY();
            if (bankProjection != null) {
                SkyIslandLocalPosition bankLocal = localPosition(volume, bank);
                double basePotential = basePotentialCache.computeIfAbsent(
                        bank,
                        ignored -> fluvial.baseTerrain().sample(bankLocal));
                double dryPotential = dryPotentialCache.computeIfAbsent(
                        bank,
                        ignored -> fluvial.sample(bankLocal));
                double lowering = Math.max(0.0, basePotential - dryPotential);
                if (lowering > 1.0e-12) {
                    int loweringBlocks = physicalLoweringBlocks(descriptor, lowering);
                    bankTopY = Math.max(
                            range.minimumY(),
                            range.maximumY() - loweringBlocks);
                }
            }

            if (!uncontestedOwnedRangeCell(
                    terrain,
                    volume.id(),
                    bank.x(),
                    bankTopY,
                    bank.z())) {
                return OptionalInt.empty();
            }
            ceiling = Math.min(ceiling, bankTopY);
        }
        return OptionalInt.of(ceiling);
    }

    private static boolean channelOutletBreachAllowed(
            SkyIslandWorldVolume volume,
            Column wet,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path) {
        SkyIslandLocalPosition local = localPosition(volume, wet);
        SkyIslandLocalPosition outlet = path.points().getLast();
        return Math.hypot(local.x() - outlet.x(), local.z() - outlet.z())
                <= Math.max(1.5, reach.wetHalfWidth() * 0.75);
    }

    private static Map<Column, ChannelPathProjection> candidateColumnProjections(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialReachGeometry reach) {
        var points = reach.path().points();
        if (points.size() < 2 || !(reach.path().pathLength() > 0.0)) {
            return Map.of();
        }

        double margin = reach.valleyHalfWidth();
        double pathLength = reach.path().pathLength();
        var physical = volume.compiledVolume().descriptor();
        Map<Column, ChannelPathProjection> projections = new HashMap<>();
        double cumulativeBefore = 0.0;

        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition a = points.get(index - 1);
            SkyIslandLocalPosition b = points.get(index);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double segmentLengthSquared = dx * dx + dz * dz;
            double segmentLength = Math.sqrt(segmentLengthSquared);
            if (!(segmentLength > 0.0)) {
                continue;
            }

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
                    double px = local.x() - a.x();
                    double pz = local.z() - a.z();
                    double segmentFraction = Math.max(
                            0.0,
                            Math.min(1.0, (px * dx + pz * dz) / segmentLengthSquared));
                    double nearestX = a.x() + segmentFraction * dx;
                    double nearestZ = a.z() + segmentFraction * dz;
                    double distance = Math.hypot(
                            local.x() - nearestX,
                            local.z() - nearestZ);
                    if (distance > margin) {
                        continue;
                    }
                    double fraction = Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    (cumulativeBefore + segmentFraction * segmentLength)
                                            / pathLength));
                    Column column = new Column(x, z);
                    ChannelPathProjection candidate =
                            new ChannelPathProjection(distance, fraction);
                    ChannelPathProjection previous = projections.get(column);
                    if (previous == null
                            || candidate.distance() < previous.distance() - 1.0e-12
                            || (Math.abs(candidate.distance() - previous.distance()) <= 1.0e-12
                                    && candidate.fraction() < previous.fraction())) {
                        projections.put(column, candidate);
                    }
                }
            }
            cumulativeBefore += segmentLength;
        }

        if (projections.isEmpty()) {
            return Map.of();
        }
        var ordered = new ArrayList<>(projections.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<Column, ChannelPathProjection> entry) -> entry.getKey().z())
                .thenComparingInt(entry -> entry.getKey().x()));
        Map<Column, ChannelPathProjection> canonical = new LinkedHashMap<>();
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

    private static Optional<RawDeployment> atFootprint(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyFootprint footprint,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(watershed, "watershed");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(solidRangeCache, "solidRangeCache");
        Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex = new LinkedHashMap<>();
        for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
            cellsByIndex.put(cell.watershedCellIndex(), cell);
        }

        // A retained waterbody owns the area represented by its accepted watershed cells, not one
        // rounded Minecraft column at each coarse cell center. Rasterize those regular watershed
        // cells into exact-volume columns while retaining the accepted cell identity.
        Map<Column, RetainedColumnPlan> columns = retainedCandidateColumns(
                descriptor, volume, terrain, watershed, cellsByIndex, solidRangeCache);
        if (columns.isEmpty() || !coversEveryRetainedCell(columns.values(), cellsByIndex.keySet())) {
            return Optional.empty();
        }

        // Project the authored lake datum relative to each accepted cell's authored terrain.
        // The physical carrier is not absolutely Y-isomorphic to the semantic terrain, so absolute
        // authored Y cannot be copied directly. Relative water-minus-terrain potential is invariant
        // enough to project onto each physical column. A robust median then yields one flat Minecraft
        // water surface without allowing one unusually low column to drag the entire basin downward.
        List<Integer> projectedWaterTops = new ArrayList<>(columns.size());
        for (RetainedColumnPlan column : columns.values()) {
            double relativeWater = footprint.waterSurfacePotential()
                    - column.sourceCell().surfacePotential();
            projectedWaterTops.add(
                    column.baseSurfaceY()
                            + physicalSignedDeltaBlocks(descriptor, relativeWater));
        }
        projectedWaterTops.sort(Integer::compareTo);

        // The semantic waterbody requires one flat Minecraft surface, but the independently compiled
        // carrier does not preserve authored absolute Y exactly. Search the finite set of projected
        // authored datums rather than committing to one median and either excavating a bathtub or
        // dropping the lake. Candidates nearest the median are preferred; ties prefer the higher
        // plane because filling an existing depression is less destructive than cutting upland.
        List<Integer> waterTopCandidates = retainedWaterTopCandidates(projectedWaterTops);
        Map<Column, RetainedColumnPlan> realizable = Map.of();
        List<BlockPos> retainedBankFill = List.of();
        int waterTopY = Integer.MIN_VALUE;
        int maximumRepresentedCells = 0;
        int maximumConnectedColumns = 0;
        int candidatesWithFullCellCoverage = 0;
        int candidatesWithFullConnectivity = 0;
        int candidatesRejectedByContainment = 0;
        for (int candidateWaterTopY : waterTopCandidates) {
            Map<Column, RetainedColumnPlan> candidate = realizableRetainedColumns(
                    descriptor,
                    volume,
                    terrain,
                    columns,
                    candidateWaterTopY);
            Set<Integer> representedCells = candidate.values().stream()
                    .map(column -> column.sourceCell().watershedCellIndex())
                    .collect(java.util.stream.Collectors.toSet());
            maximumRepresentedCells = Math.max(maximumRepresentedCells, representedCells.size());
            if (candidate.isEmpty()
                    || !representedCells.containsAll(cellsByIndex.keySet())) {
                continue;
            }
            candidatesWithFullCellCoverage++;

            Set<Column> connected = largestConnectedFootprint(candidate.keySet());
            maximumConnectedColumns = Math.max(maximumConnectedColumns, connected.size());

            // The physical carrier can expose small detached fringe patches after the bounded
            // per-column cut test. Those patches are not authored as separate lakes and must not
            // invalidate an otherwise coherent basin. Retain only the largest face-connected
            // component, but require that component itself still represents every accepted
            // watershed cell. If any authored cell exists only on a detached patch, fail closed.
            Map<Column, RetainedColumnPlan> connectedCandidate = new LinkedHashMap<>();
            for (Column column : connected) {
                RetainedColumnPlan plan = candidate.get(column);
                if (plan != null) {
                    connectedCandidate.put(column, plan);
                }
            }
            Set<Integer> connectedRepresentedCells = connectedCandidate.values().stream()
                    .map(column -> column.sourceCell().watershedCellIndex())
                    .collect(java.util.stream.Collectors.toSet());
            if (!connectedRepresentedCells.containsAll(cellsByIndex.keySet())) {
                continue;
            }
            candidatesWithFullConnectivity++;

            Optional<List<BlockPos>> bankFill = retainedBankFillPositions(
                    volume,
                    terrain,
                    solidRangeCache,
                    connectedCandidate.keySet(),
                    candidateWaterTopY);
            if (bankFill.isEmpty()) {
                candidatesRejectedByContainment++;
                continue;
            }

            realizable = connectedCandidate;
            retainedBankFill = bankFill.orElseThrow();
            waterTopY = candidateWaterTopY;
            break;
        }
        if (realizable.isEmpty()) {
            throw new IllegalStateException(
                    "retained-water footprint cannot project without unbounded terrain surgery: "
                            + "requiredCells=" + cellsByIndex.size()
                            + ", candidateColumns=" + columns.size()
                            + ", candidatePlanes=" + waterTopCandidates.size()
                            + ", maxRepresentedCells=" + maximumRepresentedCells
                            + ", fullCoveragePlanes=" + candidatesWithFullCellCoverage
                            + ", maxConnectedColumns=" + maximumConnectedColumns
                            + ", fullConnectivityPlanes=" + candidatesWithFullConnectivity
                            + ", containmentRejectedPlanes=" + candidatesRejectedByContainment
                            + ", volume=" + volume.id().path());
        }

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>(retainedBankFill);
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
        return Optional.of(rawDeployment(
                volume.id(),
                Feature.RETAINED_WATER,
                new ArrayList<>(water),
                new ArrayList<>(carved),
                new ArrayList<>(surface)));
    }

    private static List<Integer> retainedWaterTopCandidates(List<Integer> sortedProjectedWaterTops) {
        Objects.requireNonNull(sortedProjectedWaterTops, "sortedProjectedWaterTops");
        if (sortedProjectedWaterTops.isEmpty()) {
            return List.of();
        }

        // Projected authored samples constrain a continuous semantic datum, while Minecraft admits
        // only integer Y planes. Testing only sampled rounded values can miss the physically valid
        // plane between two samples. Search every integer in the bounded projected envelope plus the
        // same small reconciliation allowance used by basin cutting; order by distance from the
        // authored median and prefer the higher level on ties.
        int median = sortedProjectedWaterTops.get(sortedProjectedWaterTops.size() / 2);
        int minimum = Math.subtractExact(
                sortedProjectedWaterTops.getFirst(),
                MAX_RETAINED_BASIN_CUT_BLOCKS);
        int maximum = Math.addExact(
                sortedProjectedWaterTops.getLast(),
                MAX_RETAINED_BASIN_CUT_BLOCKS);
        List<Integer> candidates = new ArrayList<>(maximum - minimum + 1);
        for (int value = minimum; value <= maximum; value++) {
            candidates.add(value);
        }
        candidates.sort(Comparator
                .comparingInt((Integer value) -> Math.abs(value - median))
                .thenComparing(Comparator.reverseOrder()));
        return List.copyOf(candidates);
    }

    private static Map<Column, RetainedColumnPlan> realizableRetainedColumns(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, RetainedColumnPlan> columns,
            int waterTopY) {
        Map<Column, RetainedColumnPlan> realizable = new LinkedHashMap<>();
        Set<Column> intendedFootprint = columns.keySet();
        for (RetainedColumnPlan column : columns.values()) {
            int cutAboveWater = Math.max(0, column.baseSurfaceY() - waterTopY);

            // The old global cut cap fragmented large authored lakes whenever the independently
            // compiled Minecraft carrier placed an incidental ridge through their interior. That
            // carrier ridge is not hydrologic authority: the accepted retained-water footprint says
            // the column is inundated. Preserve the strict cap where terrain surgery is visible at
            // the actual lake perimeter, but allow submerged interior ridges to be removed down to
            // the robust authored lake datum. This keeps the anti-bathtub invariant at shore while
            // allowing one connected physical realization of one connected authored basin.
            if (retainedFootprintBoundary(intendedFootprint, column.column())
                    && cutAboveWater > MAX_RETAINED_BASIN_CUT_BLOCKS) {
                continue;
            }

            int naturalBedY = Math.min(column.baseSurfaceY(), waterTopY - 1);
            if (naturalBedY < column.minimumY()) {
                continue;
            }

            int desiredDepth = Math.max(
                    1,
                    physicalLoweringBlocks(descriptor, column.sourceCell().waterDepthPotential()));
            int deepestPermittedBed = Math.max(
                    column.minimumY(),
                    naturalBedY - MAX_RETAINED_BASIN_CUT_BLOCKS);
            int desiredBedY = Math.max(column.minimumY(), waterTopY - desiredDepth);
            int bedY = Math.max(deepestPermittedBed, Math.min(naturalBedY, desiredBedY));

            if (bedY >= waterTopY
                    || !ownedSolidInterval(
                            volume,
                            terrain,
                            column.column(),
                            column.minimumY(),
                            column.baseSurfaceY(),
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
        return realizable;
    }

    static boolean retainedFootprintBoundary(Set<Column> footprint, Column candidate) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(candidate, "candidate");
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            if (!footprint.contains(new Column(
                    candidate.x() + direction[0],
                    candidate.z() + direction[1]))) {
                return true;
            }
        }
        return false;
    }

    private static Map<Column, RetainedColumnPlan> retainedCandidateColumns(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWatershedPlan watershed,
            Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
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
                if (sourceCell.shoreline()
                        && !retainedShorelineContains(
                                local,
                                sourceCell,
                                cellsByIndex,
                                watershed,
                                halfSpacing)) {
                    continue;
                }
                Column column = new Column(x, z);
                var optionalRange = solidRangeCache.computeIfAbsent(
                        column,
                        ignored -> terrain.integerSolidRange(
                                volume.id(), column.x(), column.z()));
                if (optionalRange.isEmpty()) {
                    continue;
                }
                var range = optionalRange.orElseThrow();
                if (!uncontestedOwnedRangeCell(terrain, volume.id(), x, range.maximumY(), z)) {
                    continue;
                }
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

    /**
     * Rasterizes one coarse shoreline cell without breaking the footprint's authored topology.
     *
     * <p>An isolated disk per shoreline cell looks less grid-like, but it also turns cardinally
     * connected coarse cells into disconnected Minecraft puddles whenever the disk radius is less
     * than half the watershed spacing. Keep the rounded center lobe, then join it to every retained
     * cardinal neighbor with a rounded corridor. The nearest-cell gate in
     * {@link #retainedCandidateColumns} still bounds authority to the accepted coarse footprint, so
     * these corridors preserve topology without expanding the lake into unauthored cells.
     */
    static boolean retainedShorelineContains(
            SkyIslandLocalPosition local,
            SkyIslandWaterbodyFootprintCell sourceCell,
            Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex,
            SkyIslandWatershedPlan watershed,
            double halfSpacing) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(sourceCell, "sourceCell");
        Objects.requireNonNull(cellsByIndex, "cellsByIndex");
        Objects.requireNonNull(watershed, "watershed");
        if (!sourceCell.shoreline()) {
            return true;
        }
        if (!Double.isFinite(halfSpacing) || halfSpacing <= 0.0) {
            throw new IllegalArgumentException("halfSpacing must be finite and positive");
        }

        double sourceRadius = retainedShorelineRadius(sourceCell, halfSpacing);
        if (Math.hypot(
                        local.x() - sourceCell.position().x(),
                        local.z() - sourceCell.position().z())
                <= sourceRadius) {
            return true;
        }

        int sourceIndex = sourceCell.watershedCellIndex();
        int sourceX = sourceIndex % watershed.gridSize();
        int sourceZ = sourceIndex / watershed.gridSize();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int neighborX = sourceX + direction[0];
            int neighborZ = sourceZ + direction[1];
            if (neighborX < 0
                    || neighborZ < 0
                    || neighborX >= watershed.gridSize()
                    || neighborZ >= watershed.gridSize()) {
                continue;
            }
            SkyIslandWaterbodyFootprintCell neighbor =
                    cellsByIndex.get(neighborZ * watershed.gridSize() + neighborX);
            if (neighbor == null) {
                continue;
            }
            double neighborRadius = neighbor.shoreline()
                    ? retainedShorelineRadius(neighbor, halfSpacing)
                    : halfSpacing;
            double corridorRadius = Math.min(sourceRadius, neighborRadius);
            if (distanceToSegment(local, sourceCell.position(), neighbor.position())
                    <= corridorRadius) {
                return true;
            }
        }
        return false;
    }

    private static double retainedShorelineRadius(
            SkyIslandWaterbodyFootprintCell cell,
            double halfSpacing) {
        double depth = Math.sqrt(Math.max(0.0, cell.waterDepthPotential()));
        return halfSpacing * (0.40 + 0.60 * depth);
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
            int authoritativeMinimumY,
            int authoritativeMaximumY,
            int minimumY,
            int maximumY) {
        if (minimumY < authoritativeMinimumY || maximumY > authoritativeMaximumY) {
            return false;
        }
        // The accepted built-in volume compiler proves vertical continuity between the
        // exact range endpoints. Single-volume projection therefore needs no interior density
        // reclassification; stacked projection still excludes any foreign owner per cell.
        if (!terrain.hasMultipleCompiledVolumes()) {
            return true;
        }
        for (int y = minimumY; y <= maximumY; y++) {
            if (terrain.isSolidOwnedByOtherVolume(volume.id(), column.x(), y, column.z())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the bounded terrain fill required to contain one retained waterbody.
     *
     * <p>The semantic basin is authoritative for its planform. The independently compiled physical
     * carrier is allowed a small symmetric shoreline reconciliation: existing high terrain may be
     * cut by {@link #MAX_RETAINED_BASIN_CUT_BLOCKS}, and a low but supported bank may be raised by
     * {@link #MAX_RETAINED_BANK_FILL_BLOCKS}. A true void edge, foreign-volume conflict, or deeper
     * required dam fails closed. Interior basin conditioning is handled separately and never grants
     * authority outside the accepted wet footprint.
     */
    private static Optional<List<BlockPos>> retainedBankFillPositions(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Set<Column> wetColumns,
            int waterTopY) {
        LinkedHashSet<BlockPos> fill = new LinkedHashSet<>();
        Set<Column> visitedBanks = new HashSet<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Column wet : wetColumns) {
            for (int[] direction : directions) {
                Column bank = new Column(wet.x() + direction[0], wet.z() + direction[1]);
                if (wetColumns.contains(bank) || !visitedBanks.add(bank)) {
                    continue;
                }
                if (ownedSolidAt(volume, terrain, solidRangeCache, bank, waterTopY)) {
                    continue;
                }

                var optionalRange = solidRangeCache.computeIfAbsent(
                        bank,
                        ignored -> terrain.integerSolidRange(
                                volume.id(), bank.x(), bank.z()));
                if (optionalRange.isEmpty()) {
                    // Do not manufacture an island rim over true exterior void.
                    return Optional.empty();
                }
                var range = optionalRange.orElseThrow();
                if (waterTopY <= range.maximumY()) {
                    // A carrier block exists at this level but is not uncontested target-volume
                    // ownership (for example, a stacked foreign volume). Never fill through it.
                    return Optional.empty();
                }

                int fillDepth = waterTopY - range.maximumY();
                if (fillDepth > MAX_RETAINED_BANK_FILL_BLOCKS) {
                    return Optional.empty();
                }
                for (int y = range.maximumY() + 1; y <= waterTopY; y++) {
                    if (!volume.bounds().contains(bank.x(), y, bank.z())
                            || terrain.isSolidOwnedByOtherVolume(
                                    volume.id(), bank.x(), y, bank.z())) {
                        return Optional.empty();
                    }
                    fill.add(new BlockPos(bank.x(), y, bank.z()));
                }
            }
        }
        return Optional.of(List.copyOf(fill));
    }

    private static boolean ownedSolidAt(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Column column,
            int y) {
        var range = solidRangeCache.computeIfAbsent(
                column,
                ignored -> terrain.integerSolidRange(volume.id(), column.x(), column.z()));
        if (range.isEmpty()
                || y < range.orElseThrow().minimumY()
                || y > range.orElseThrow().maximumY()) {
            return false;
        }
        return uncontestedOwnedRangeCell(terrain, volume.id(), column.x(), y, column.z());
    }

    /**
     * Ownership check for a Y already proven inside the accepted compiler's continuous solid range.
     *
     * <p>The range itself proves target-volume occupancy. Only stacked catalogs need an additional
     * per-cell exclusion for a foreign exact volume.
     */
    private static boolean uncontestedOwnedRangeCell(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWorldVolumeId volumeId,
            int x,
            int y,
            int z) {
        return !terrain.hasMultipleCompiledVolumes()
                || !terrain.isSolidOwnedByOtherVolume(volumeId, x, y, z);
    }

    private static RawDeployment rawDeployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions) {
        return new RawDeployment(
                volumeId,
                feature,
                new ArrayList<>(new LinkedHashSet<>(positions)),
                new ArrayList<>(new LinkedHashSet<>(carvedPositions)),
                new ArrayList<>(new LinkedHashSet<>(surfacePositions)));
    }

    private static void addMembershipByChunk(
            Map<Long, Set<BlockPos>> byChunk,
            Iterable<BlockPos> positions) {
        for (BlockPos position : positions) {
            addMembershipByChunk(byChunk, position);
        }
    }

    private static void addMembershipByChunk(
            Map<Long, Set<BlockPos>> byChunk,
            BlockPos position) {
        long chunkKey = new ChunkPos(position).toLong();
        byChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(position);
    }

    private static boolean containsByChunk(
            Map<Long, Set<BlockPos>> byChunk,
            BlockPos position) {
        Set<BlockPos> positions = byChunk.get(new ChunkPos(position).toLong());
        return positions != null && positions.contains(position);
    }

    static Map<Long, ChunkProjection> indexByChunk(List<Deployment> deployments) {
        Objects.requireNonNull(deployments, "deployments");
        Map<Long, LinkedHashMap<BlockPos, BlockState>> mutable = new LinkedHashMap<>();
        for (Deployment deployment : deployments) {
            indexHydrologySurfaceStates(mutable, deployment.surfacePositions());
            indexStates(mutable, deployment.carvedPositions(), Blocks.AIR.defaultBlockState());
            indexStates(mutable, deployment.positions(), Blocks.WATER.defaultBlockState());
        }

        Map<Long, ChunkProjection> result = new LinkedHashMap<>();
        for (var entry : mutable.entrySet()) {
            result.put(entry.getKey(), new ChunkProjection(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void indexHydrologySurfaceStates(
            Map<Long, LinkedHashMap<BlockPos, BlockState>> byChunk,
            Iterable<BlockPos> positions) {
        for (BlockPos position : positions) {
            long chunkKey = new ChunkPos(position).toLong();
            var states = byChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
            BlockState desired = hydrologySurfaceState(position);
            BlockState previous = states.putIfAbsent(position, desired);
            if (previous != null && !previous.equals(desired)) {
                throw new IllegalStateException(
                        "normalized authored hydrology assigned conflicting surface states at " + position);
            }
        }
    }

    /**
     * Backend material expression for exposed fluvial/lacustrine substrate.
     *
     * <p>Do not paint authored beds with the ordinary biome top block: that produced grass/dirt
     * slabs through rivers and lakes. The coordinate hash is deterministic and feature-independent,
     * so overlapping channel/lake surface authority resolves to the same sediment state.
     */
    static BlockState hydrologySurfaceState(BlockPos position) {
        Objects.requireNonNull(position, "position");
        long mixed = position.asLong() * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 33;
        int bucket = Math.floorMod((int) (mixed ^ (mixed >>> 32)), 16);
        if (bucket < 10) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (bucket < 14) {
            return Blocks.CLAY.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static void indexStates(
            Map<Long, LinkedHashMap<BlockPos, BlockState>> byChunk,
            Iterable<BlockPos> positions,
            BlockState desiredState) {
        for (BlockPos position : positions) {
            long chunkKey = new ChunkPos(position).toLong();
            var states = byChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
            BlockState previous = states.putIfAbsent(position, desiredState);
            if (previous != null && !previous.equals(desiredState)) {
                throw new IllegalStateException(
                        "normalized authored hydrology assigned conflicting states at " + position);
            }
        }
    }

    static int apply(ChunkAccess chunk, ChunkProjection projection) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(projection, "projection");
        int written = 0;
        for (var entry : projection.states().entrySet()) {
            BlockPos position = entry.getKey();
            BlockState desired = entry.getValue();
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                throw new IllegalArgumentException(
                        "authored hydrology chunk projection contains a foreign chunk position");
            }
            BlockState current = chunk.getBlockState(position);
            if (desired.is(Blocks.WATER)) {
                if (!isWaterBearing(current)) {
                    chunk.setBlockState(position, desired, false);
                    written++;
                }
            } else if (desired.isAir()) {
                if (!current.isAir()) {
                    chunk.setBlockState(position, desired, false);
                    written++;
                }
            } else if (isHydrologySurfaceMaterial(desired)) {
                if (!current.equals(desired)) {
                    chunk.setBlockState(position, desired, false);
                    written++;
                }
            } else {
                throw new IllegalStateException(
                        "unsupported authored hydrology projection state " + desired);
            }
        }
        return written;
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
            BlockState desired = hydrologySurfaceState(position);
            if (!chunk.getBlockState(position).equals(desired)) {
                chunk.setBlockState(position, desired, false);
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
    static boolean isHydrologySurfaceMaterial(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.STONE);
    }

    static boolean isWaterBearing(BlockState state) {
        Objects.requireNonNull(state, "state");
        var fluid = state.getFluidState().getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private record ChannelCarrierCandidate(
            Column column,
            double distance,
            double fraction,
            int ownerMinimumWaterTop,
            int drySurfaceY,
            int maximumWaterTop,
            int desiredWaterTop) {}

    private record ChannelGradeSample(
            double fraction,
            int minimumWaterTop,
            int maximumWaterTop,
            int desiredWaterTop) {
        private ChannelGradeSample {
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
                throw new IllegalArgumentException("channel grade fraction must be finite and in [0, 1]");
            }
            if (minimumWaterTop > maximumWaterTop) {
                throw new IllegalArgumentException("invalid channel grade sample bounds");
            }
        }
    }

    private record PhysicalChannelGrade(
            List<Double> fractions,
            List<Integer> waterTops,
            int reconciliationDepth) {
        private PhysicalChannelGrade {
            fractions = List.copyOf(fractions);
            waterTops = List.copyOf(waterTops);
            if (fractions.isEmpty() || fractions.size() != waterTops.size()) {
                throw new IllegalArgumentException(
                        "physical channel grade requires matching nonempty samples");
            }
            if (reconciliationDepth < 0
                    || reconciliationDepth > MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS) {
                throw new IllegalArgumentException("invalid channel reconciliation depth");
            }
        }

        private int sample(double fraction) {
            if (fractions.size() == 1 || fraction <= fractions.getFirst()) {
                return waterTops.getFirst();
            }
            if (fraction >= fractions.getLast()) {
                return waterTops.getLast();
            }
            int low = 0;
            int high = fractions.size() - 1;
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (fractions.get(middle) <= fraction) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            double lowerFraction = fractions.get(low);
            double upperFraction = fractions.get(high);
            if (upperFraction - lowerFraction <= 1.0e-12) {
                return Math.min(waterTops.get(low), waterTops.get(high));
            }
            double t = (fraction - lowerFraction) / (upperFraction - lowerFraction);
            return (int) Math.round(
                    waterTops.get(low)
                            + t * (waterTops.get(high) - waterTops.get(low)));
        }
    }

    private record ChannelPathProjection(
            double distance,
            double fraction) {
        private ChannelPathProjection {
            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new IllegalArgumentException("channel path distance must be finite and nonnegative");
            }
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
                throw new IllegalArgumentException("channel path fraction must be finite and in [0, 1]");
            }
        }
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

    record Column(int x, int z) {}
}
