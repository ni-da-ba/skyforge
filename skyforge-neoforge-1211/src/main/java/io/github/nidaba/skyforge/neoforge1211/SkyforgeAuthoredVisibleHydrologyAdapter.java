package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.world.SkyIslandChannelDrop;
import io.github.nidaba.skyforge.world.SkyIslandContinuousHydrologicTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandFluvialReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandHydrologicTerrainSurfacePlanner;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprint;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMargin;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyMarginCell;
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
    // Flowing channels may never manufacture a levee to compensate for a carrier mismatch.
    // Accepted cascade/waterfall/edge-discharge semantics own the only physical breach. Standing
    // retained water keeps a small explicit bank-reconciliation budget because a flat datum can
    // legitimately meet a slightly low but otherwise supported shoreline column.
    static final int MAX_CHANNEL_BANK_FILL_BLOCKS = 0;
    static final int MAX_RETAINED_BANK_FILL_BLOCKS = 3;
    // The first wet rings of a retained basin form a shallow littoral ramp instead of a vertical
    // bathtub cut. Deeper authored basin geometry remains unchanged beyond this bounded fringe.
    static final int RETAINED_LITTORAL_GRADE_RINGS = 4;
    // A channel touching retained water should hydraulically converge to the basin datum without
    // replacing the bounded isotonic solve. Weight those contact samples strongly enough that the
    // least-change solution prefers a seamless inlet/outlet whenever carrier bounds permit it.
    static final int RETAINED_JUNCTION_GRADE_WEIGHT = 32;
    // Blend a short physical approach into an accepted retained-water datum. This is not a new
    // watershed relation: it is enabled only for a reach that actually touches retained water.
    static final int RETAINED_JUNCTION_BLEND_BLOCKS = 4;

    enum Feature { CHANNEL, RETAINED_WATER }

    record Deployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions,
            List<BlockPos> forcedSurfacePositions) {
        Deployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            // Preserve the retained plan even if a package-local caller supplies mutable lists.
            // List.copyOf can reuse JDK immutable planner inputs without exposing caller mutation.
            positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
            carvedPositions = List.copyOf(Objects.requireNonNull(carvedPositions, "carvedPositions"));
            surfacePositions = List.copyOf(Objects.requireNonNull(surfacePositions, "surfacePositions"));
            forcedSurfacePositions = List.copyOf(
                    Objects.requireNonNull(forcedSurfacePositions, "forcedSurfacePositions"));
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
            Set<BlockPos> authoredSurface = new HashSet<>(surfacePositions);
            for (BlockPos surface : surfacePositions) {
                if (occupied.contains(surface)) {
                    throw new IllegalArgumentException(
                            "hydrology surface dressing must remain below wet and carved cells");
                }
            }
            for (BlockPos forced : forcedSurfacePositions) {
                if (!authoredSurface.contains(forced)) {
                    throw new IllegalArgumentException(
                            "forced hydrology surface material must be a subset of authored surface ownership");
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
            List<BlockPos> surfacePositions,
            List<BlockPos> forcedSurfacePositions) {
        private RawDeployment {
            volumeId = Objects.requireNonNull(volumeId, "volumeId");
            feature = Objects.requireNonNull(feature, "feature");
            positions = List.copyOf(positions);
            carvedPositions = List.copyOf(carvedPositions);
            surfacePositions = List.copyOf(surfacePositions);
            forcedSurfacePositions = List.copyOf(forcedSurfacePositions);
        }
    }

    /**
     * Immutable Minecraft execution projection for exactly one chunk.
     *
     * <p>All authored roles are disjoint after global normalization, so one block-state map is enough
     * for mutation, fluid-boundary membership, and population-state lookup. Keeping this index
     * chunk-local prevents every chunk realization from rescanning whole-island hydrology lists.
     */
    record ChunkProjection(
            Map<BlockPos, BlockState> states,
            Set<BlockPos> surfacePositions,
            Set<BlockPos> forcedSurfacePositions) {
        ChunkProjection {
            Objects.requireNonNull(states, "states");
            Objects.requireNonNull(surfacePositions, "surfacePositions");
            Objects.requireNonNull(forcedSurfacePositions, "forcedSurfacePositions");
            states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
            surfacePositions = Collections.unmodifiableSet(new LinkedHashSet<>(surfacePositions));
            forcedSurfacePositions = Collections.unmodifiableSet(
                    new LinkedHashSet<>(forcedSurfacePositions));
            if (!surfacePositions.containsAll(forcedSurfacePositions)) {
                throw new IllegalArgumentException(
                        "forced chunk-local hydrology surface must remain inside surface ownership");
            }
        }

        Optional<BlockState> stateAt(BlockPos position) {
            Objects.requireNonNull(position, "position");
            BlockState state = states.get(position);
            if (state != null) {
                return Optional.of(state);
            }
            if (surfacePositions.contains(position)) {
                // Preserved beds are hydrology-owned without being physically repainted. Expose a
                // stable non-falling substrate sentinel to placement predicates; the live block
                // remains the compiled/native geology until a registered dressing feature changes it.
                return Optional.of(Blocks.STONE.defaultBlockState());
            }
            return Optional.empty();
        }

        boolean containsWater(BlockPos position) {
            BlockState state = states.get(Objects.requireNonNull(position, "position"));
            return state != null && state.is(Blocks.WATER);
        }

        boolean containsSurface(BlockPos position) {
            return surfacePositions.contains(Objects.requireNonNull(position, "position"));
        }

        Optional<BlockState> populationState(BlockPos position) {
            return stateAt(position);
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
        Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache =
                new HashMap<>();
        // Physical hydrology must share one vertical datum across channels and retained water.
        // Cache the raw authored elevation here; the continuous hydrologic/fluvial fields remain
        // the target dry surface relative to that datum. Using the already-adjusted hydrologic
        // surface as the channel datum double-counts terrain response and can offset river/lake
        // junctions by several physical blocks.
        Map<Column, Double> basePotentialCache = new HashMap<>();
        Map<Column, Double> dryPotentialCache = new HashMap<>();

        // Retained basins are the hydraulic boundary condition for any channel that enters or exits
        // them. Project those basins first so the channel grade solver can target the actual
        // qualified Minecraft datum instead of solving independently and being clipped afterward.
        List<RawDeployment> rawRetained = new ArrayList<>();
        List<RetainedHydraulicBoundary> retainedHydraulicBoundaries = new ArrayList<>();
        if (!intent.retainedWater().isEmpty()) {
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            for (var retained : intent.retainedWater()) {
                Optional<RawDeployment> projected = atFootprint(
                        descriptor,
                        volume,
                        terrain,
                        watershed,
                        retained.footprint(),
                        retained.margin(),
                        fluvial.baseTerrain(),
                        solidRangeCache);
                if (projected.isEmpty()) {
                    continue;
                }
                RawDeployment deployment = projected.orElseThrow();
                rawRetained.add(deployment);
                Set<Integer> watershedCells = retained.footprint().cells().stream()
                        .map(SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                retainedHydraulicBoundaries.add(new RetainedHydraulicBoundary(
                        watershedCells,
                        retainedWaterTopByColumn(List.of(deployment))));
            }
        }
        Map<Column, Integer> retainedWaterTopByColumn = retainedWaterTopByColumn(rawRetained);

        List<RawDeployment> rawChannels = new ArrayList<>();
        Set<Integer> routedEdgeOutlets = intent.drops().stream()
                .filter(drop -> drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .map(drop -> drop.drop().sourceCellIndex())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (var channel : intent.channels()) {
            boolean routedEdgeOutlet =
                    routedEdgeOutlets.contains(channel.path().profile().segment().downstreamCellIndex());
            RetainedHydraulicBoundaryMatch retainedBoundary =
                    retainedHydraulicBoundaryFor(
                            channel.path(),
                            retainedHydraulicBoundaries);
            if (retainedBoundary.sourceConnected()
                    && retainedBoundary.downstreamConnected()) {
                rawChannels.add(submergedChannelDeployment(
                        fluvial,
                        volume,
                        channel.path(),
                        retainedBoundary.waterTopByColumn(),
                        rawRetained));
                continue;
            }
            atPath(
                            descriptor,
                            fluvial,
                            volume,
                            terrain,
                            channel.path(),
                            routedEdgeOutlet,
                            retainedBoundary.waterTopByColumn(),
                            retainedBoundary.sourceConnected(),
                            retainedBoundary.downstreamConnected(),
                            solidRangeCache,
                            basePotentialCache,
                            dryPotentialCache)
                    .ifPresent(rawChannels::add);
        }

        // Preserve the historical external deployment order (channels, then retained water) even
        // though retained basins are projected first internally for hydraulic boundary conditions.
        List<RawDeployment> rawDeployments =
                new ArrayList<>(rawChannels.size() + rawRetained.size());
        rawDeployments.addAll(rawChannels);
        rawDeployments.addAll(rawRetained);

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

        // Retained water owns its complete submerged physical column. A routed reach may enter or
        // leave that footprint, but it must not excavate a second river trench through the lake bed
        // or extend a different water grade above/below the retained basin. Keep only channel-water
        // cells that coincide with the basin's own water volume, and suppress channel carve/surface
        // authority anywhere inside the retained (x,z) footprint.
        //
        // Use chunk-partitioned membership so production-size basins retain the existing bounded
        // lookup behavior instead of creating one enormous whole-island position set.
        Map<Long, Set<BlockPos>> retainedWaterByChunk = new HashMap<>();
        Map<Long, Set<Column>> retainedColumnsByChunk = new HashMap<>();
        Map<Long, Map<Column, List<BlockPos>>> retainedWaterByColumnByChunk = new HashMap<>();
        for (RawDeployment deployment : rawDeployments) {
            if (deployment.feature() != Feature.RETAINED_WATER) {
                continue;
            }
            addMembershipByChunk(retainedWaterByChunk, deployment.positions());
            for (BlockPos position : deployment.positions()) {
                addColumnMembershipByChunk(retainedColumnsByChunk, position);
                long chunkKey = new ChunkPos(position).toLong();
                retainedWaterByColumnByChunk
                        .computeIfAbsent(chunkKey, ignored -> new HashMap<>())
                        .computeIfAbsent(
                                new Column(position.getX(), position.getZ()),
                                ignored -> new ArrayList<>())
                        .add(position);
            }
        }

        List<List<BlockPos>> normalizedWater = new ArrayList<>(rawDeployments.size());
        Map<Long, Set<BlockPos>> waterByChunk = new HashMap<>();
        for (RawDeployment deployment : rawDeployments) {
            List<BlockPos> water;
            if (deployment.feature() != Feature.CHANNEL) {
                water = deployment.positions();
            } else {
                LinkedHashSet<BlockPos> sharedWater = new LinkedHashSet<>();
                for (BlockPos position : deployment.positions()) {
                    if (!containsColumnByChunk(retainedColumnsByChunk, position)) {
                        sharedWater.add(position);
                        continue;
                    }
                    Map<Column, List<BlockPos>> retainedByColumn =
                            retainedWaterByColumnByChunk.get(new ChunkPos(position).toLong());
                    if (retainedByColumn == null) {
                        continue;
                    }
                    List<BlockPos> retainedColumn = retainedByColumn.get(
                            new Column(position.getX(), position.getZ()));
                    if (retainedColumn != null) {
                        // A reach absorbed by a lake still retains channel evidence, but its
                        // physical water role is exactly the basin's flat water column. This keeps
                        // route/corridor diagnostics meaningful without reintroducing an independent
                        // submerged grade or trench.
                        sharedWater.addAll(retainedColumn);
                    }
                }
                var canonicalPositionOrder = Comparator
                        .comparingInt((BlockPos position) -> position.getZ())
                        .thenComparingInt(position -> position.getX())
                        .thenComparingInt(position -> position.getY());
                var canonicalSharedWater = new ArrayList<>(sharedWater);
                canonicalSharedWater.sort(canonicalPositionOrder);
                water = List.copyOf(canonicalSharedWater);
            }
            if (water.isEmpty()) {
                throw new IllegalStateException(
                        "retained-water precedence lost all physical water authority for an accepted channel: volume="
                                + deployment.volumeId().path());
            }
            normalizedWater.add(water);
            addMembershipByChunk(waterByChunk, water);
        }

        // Every individual channel/basin deployment authors a contiguous vertical water interval,
        // but two accepted reaches can share one raster column at different physical grades. The
        // final Minecraft state is their union, so leaving an air voxel between those intervals
        // creates literal hovering water. Close only gaps bounded by authored water above and below;
        // never extend beyond the existing vertical envelope, and never cross a foreign stacked
        // volume. Assign a gap to the nearest authored water above it so provenance follows the
        // descending upper interval rather than manufacturing a new independent water source.
        normalizedWater = closeVerticalWaterGaps(
                normalizedWater, volume, terrain);
        waterByChunk.clear();
        for (List<BlockPos> water : normalizedWater) {
            addMembershipByChunk(waterByChunk, water);
        }

        Map<Long, Set<BlockPos>> carvedByChunk = new HashMap<>();
        for (RawDeployment deployment : rawDeployments) {
            for (BlockPos position : deployment.carvedPositions()) {
                if (deployment.feature() == Feature.CHANNEL
                        && containsColumnByChunk(retainedColumnsByChunk, position)) {
                    continue;
                }
                if (!containsByChunk(waterByChunk, position)) {
                    addMembershipByChunk(carvedByChunk, position);
                }
            }
        }

        List<Deployment> deployments = new ArrayList<>(rawDeployments.size());
        for (int index = 0; index < rawDeployments.size(); index++) {
            RawDeployment deployment = rawDeployments.get(index);
            List<BlockPos> carved = deployment.carvedPositions().stream()
                    .filter(position -> deployment.feature() != Feature.CHANNEL
                            || !containsColumnByChunk(retainedColumnsByChunk, position))
                    .filter(position -> !containsByChunk(waterByChunk, position))
                    .toList();
            List<BlockPos> surface = deployment.surfacePositions().stream()
                    .filter(position -> deployment.feature() != Feature.CHANNEL
                            || !containsColumnByChunk(retainedColumnsByChunk, position))
                    .filter(position -> !containsByChunk(waterByChunk, position))
                    .filter(position -> !containsByChunk(carvedByChunk, position))
                    .toList();
            List<BlockPos> forcedSurface = deployment.forcedSurfacePositions().stream()
                    .filter(position -> deployment.feature() != Feature.CHANNEL
                            || !containsColumnByChunk(retainedColumnsByChunk, position))
                    .filter(position -> !containsByChunk(waterByChunk, position))
                    .filter(position -> !containsByChunk(carvedByChunk, position))
                    .toList();
            deployments.add(new Deployment(
                    deployment.volumeId(),
                    deployment.feature(),
                    normalizedWater.get(index),
                    carved,
                    surface,
                    forcedSurface));
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
            Map<Column, Integer> retainedWaterTopByColumn,
            boolean sourceConnectedToRetained,
            boolean downstreamConnectedToRetained,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(fluvial, "fluvial");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(retainedWaterTopByColumn, "retainedWaterTopByColumn");
        if (path.points().isEmpty()) {
            throw new IllegalArgumentException("authored channel path requires at least one point");
        }

        SkyIslandFluvialReachGeometry reach = fluvial.reaches().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "AUTH-0105 fluvial field lost accepted visible channel reach"));
        Optional<SkyIslandLocalPosition> routedOutlet =
                fluvial.terminalDrop(reach).map(SkyIslandChannelDrop::position);
        if (routedOutlet.isEmpty() && routedEdgeOutlet) {
            routedOutlet = Optional.of(path.points().getLast());
        }

        Map<Column, ChannelPathProjection> candidateProjections =
                candidateColumnProjections(volume, fluvial, reach);
        if (candidateProjections.isEmpty()) {
            throw channelProjectionFailure(volume, path, "no raster carrier columns");
        }
        RetainedApproachWindow retainedApproach = retainedApproachWindow(
                fluvial,
                reach,
                candidateProjections,
                retainedWaterTopByColumn,
                sourceConnectedToRetained,
                downstreamConnectedToRetained);
        Optional<PhysicalChannelGrade> physicalGrade = physicalChannelGrade(
                descriptor,
                volume,
                terrain,
                fluvial,
                reach,
                candidateProjections,
                routedOutlet,
                retainedWaterTopByColumn,
                retainedApproach,
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
                    ignored -> fluvial.baseTerrain().baseElevation(local));
            double dryPotential = dryPotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.sample(local));
            double dryAdjustment = dryPotential - basePotential;
            boolean spineCarrier = physicalGrade.orElseThrow().contains(column);
            if (Math.abs(dryAdjustment) <= 1.0e-12 && !spineCarrier) {
                continue;
            }

            int baseSurfaceY = range.maximumY();
            double authoredBankPotential = fluvial.baseTerrain().sample(local);
            int authoredBankSurfaceY = projectedTerrainSurfaceY(
                    descriptor,
                    baseSurfaceY,
                    range.minimumY(),
                    basePotential,
                    authoredBankPotential);
            if (!authoredSurfaceExtensionFeasible(
                    volume, terrain, column, baseSurfaceY, authoredBankSurfaceY)) {
                continue;
            }
            OptionalDouble authoredWater = OptionalDouble.empty();
            if (distance <= effectiveWetHalfWidth(
                    fluvial,
                    reach,
                    candidate.getValue(),
                    column,
                    retainedWaterTopByColumn,
                    retainedApproach)) {
                double reachWater = fluvial.reachWaterSurfacePotential(reach, fraction);
                if (reachWater > dryPotential + 1.0e-12 || spineCarrier) {
                    // The accepted visible reach owns its connected centerline spine. Independent
                    // dry-field voxel quantization may erase a one-column semantic lowering, but it
                    // must not punch a hole through an otherwise solid authored channel carrier.
                    authoredWater = OptionalDouble.of(reachWater);
                }
            }
            int drySurfaceY = projectedTerrainSurfaceY(
                    descriptor,
                    baseSurfaceY,
                    range.minimumY(),
                    basePotential,
                    dryPotential);
            if (authoredWater.isPresent()) {
                drySurfaceY = Math.max(
                        range.minimumY(),
                        Math.min(drySurfaceY, authoredBankSurfaceY - 2));
            }
            if (!authoredSurfaceExtensionFeasible(
                    volume, terrain, column, baseSurfaceY, drySurfaceY)) {
                continue;
            }

            int waterTopY = Integer.MIN_VALUE;
            if (authoredWater.isPresent()) {
                int projected = physicalGrade.orElseThrow().sample(fraction);

                // The discrete physical grade is solved from centerline carrier constraints and is
                // non-increasing by construction. Reconcile small independent-carrier mismatches by
                // lowering the narrow channel bed instead of raising/clamping the water surface.
                int requiredBedY = projected - 1;
                int extraCut = Math.max(0, drySurfaceY - requiredBedY);
                if (projected <= authoredBankSurfaceY - 1
                        && projected >= range.minimumY() + 1
                        && extraCut <= MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS) {
                    // The isotonic spine still chooses the least-change water grade and the
                    // smallest cut needed by its canonical carrier samples. Raster columns beside
                    // that spine may need a slightly deeper submerged cut to remain face-connected
                    // after voxelization; permit that only within the same hard reconciliation
                    // ceiling rather than letting the spine's minimum depth fragment the river.
                    drySurfaceY = Math.max(range.minimumY(), Math.min(drySurfaceY, requiredBedY));
                    waterTopY = projected;
                }
            }
            columns.put(
                    column,
                    new ChannelColumnPlan(column, distance, baseSurfaceY, drySurfaceY, waterTopY));
        }

        Set<Column> plannedWet = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            if (column.waterTopY() != Integer.MIN_VALUE) {
                plannedWet.add(column.column());
            }
        }

        Set<Column> containedWet = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            if (column.waterTopY() == Integer.MIN_VALUE) {
                continue;
            }
            Optional<List<BlockPos>> bankFill = channelBankFillPositions(
                    volume,
                    terrain,
                    columns,
                    plannedWet,
                    column,
                    reach,
                    path,
                    routedOutlet,
                    retainedWaterTopByColumn,
                    solidRangeCache);
            if (bankFill.isPresent()) {
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

        // Re-evaluate containment against the surviving connected component. Removing one rejected
        // fringe cell can expose a neighboring wet cell as a new lateral edge, so prune to a fixed
        // point instead of either (a) treating disconnected candidates as imaginary banks or
        // (b) rejecting the entire accepted reach because a widened mouth/fringe cell cannot be
        // contained. The authored centerline/path-coverage checks below remain authoritative.
        for (int iteration = 0; iteration <= plannedWetColumns; iteration++) {
            LinkedHashSet<Column> stillContained = new LinkedHashSet<>();
            for (Column wetColumn : containedWet) {
                ChannelColumnPlan column = columns.get(wetColumn);
                if (column == null) {
                    throw new IllegalStateException("connected wet component lost its channel plan");
                }
                if (channelBankFillPositions(
                                volume,
                                terrain,
                                columns,
                                containedWet,
                                column,
                                reach,
                                path,
                                routedOutlet,
                                retainedWaterTopByColumn,
                                solidRangeCache)
                        .isPresent()) {
                    stillContained.add(wetColumn);
                }
            }
            Set<Column> next = largestConnectedFootprint(stillContained);
            if (next.equals(containedWet)) {
                break;
            }
            containedWet = next;
            if (containedWet.isEmpty()) {
                throw channelProjectionFailure(
                        volume,
                        path,
                        "iterative bank containment pruned the entire wet component; "
                                + "plannedWetColumns=" + plannedWetColumns
                                + ", initiallyContainedWetColumns=" + containedWetColumns);
            }
            if (iteration == plannedWetColumns) {
                throw new IllegalStateException("channel containment pruning failed to converge");
            }
        }

        LinkedHashSet<BlockPos> channelBankFill = new LinkedHashSet<>();
        for (Column wetColumn : containedWet) {
            ChannelColumnPlan column = columns.get(wetColumn);
            Optional<List<BlockPos>> bankFill = channelBankFillPositions(
                    volume,
                    terrain,
                    columns,
                    containedWet,
                    column,
                    reach,
                    path,
                    routedOutlet,
                    retainedWaterTopByColumn,
                    solidRangeCache);
            if (bankFill.isEmpty()) {
                throw new IllegalStateException(
                        "fixed-point channel containment lost a previously verified wet column");
            }
            channelBankFill.addAll(bankFill.orElseThrow());
        }

        var physicalDescriptor = volume.compiledVolume().descriptor();
        for (int pointIndex = 0; pointIndex < path.points().size(); pointIndex++) {
            SkyIslandLocalPosition point = path.points().get(pointIndex);
            double nearestWetDistance = containedWet.stream()
                    .mapToDouble(column -> Math.hypot(
                            column.x() - physicalDescriptor.centerX() - point.x(),
                            column.z() - physicalDescriptor.centerZ() - point.z()))
                    .min()
                    .orElse(Double.POSITIVE_INFINITY);
            double localWetTolerance = Math.max(
                    fluvial.wetHalfWidthAt(reach, 0.0),
                    fluvial.wetHalfWidthAt(reach, 1.0)) + 1.0;
            if (nearestWetDistance > localWetTolerance) {
                throw channelProjectionFailure(
                        volume,
                        path,
                        "connected raster spine lost authored centerline coverage at point="
                                + pointIndex + "/" + path.points().size()
                                + ", nearestWetDistance=" + nearestWetDistance
                                + ", wetTolerance=" + localWetTolerance
                                + ", connectedWetColumns=" + containedWet.size()
                                + ", plannedWetColumns=" + plannedWetColumns
                                + ", containedWetColumns=" + containedWetColumns);
            }
        }

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>(channelBankFill);
        LinkedHashSet<BlockPos> forcedSurface = new LinkedHashSet<>(channelBankFill);
        LinkedHashSet<BlockPos> authoredTerrainFill = new LinkedHashSet<>();
        for (ChannelColumnPlan column : columns.values()) {
            boolean wet = containedWet.contains(column.column());
            boolean plannedAsWet = column.waterTopY() != Integer.MIN_VALUE;

            // A column that was semantically wet but did not survive the final contained component
            // must remain solid terrain. Carving it without water creates the visible dry slots and
            // hovering edges reported in H6.
            if (plannedAsWet && !wet) {
                continue;
            }

            if (column.drySurfaceY() > column.baseSurfaceY()) {
                Optional<List<BlockPos>> extension = authoredSurfaceExtensionPositions(
                        volume,
                        terrain,
                        column.column(),
                        column.baseSurfaceY(),
                        column.drySurfaceY());
                if (extension.isEmpty()) {
                    throw channelProjectionFailure(
                            volume,
                            path,
                            "authored positive terrain response conflicts with volume ownership at "
                                    + column.column());
                }
                authoredTerrainFill.addAll(extension.orElseThrow());
            }

            ChannelPathProjection projection = candidateProjections.get(column.column());
            if (projection != null
                    && column.distance() <= fluvial.bankfullHalfWidthAt(
                            reach, projection.fraction())
                    && uncontestedOwnedRangeCell(
                            terrain,
                            volume.id(),
                            column.column().x(),
                            column.drySurfaceY(),
                            column.column().z())) {
                surface.add(new BlockPos(
                        column.column().x(), column.drySurfaceY(), column.column().z()));
            }

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
        surface.addAll(authoredTerrainFill);
        forcedSurface.addAll(authoredTerrainFill);
        carved.removeAll(water);
        carved.removeAll(channelBankFill);
        carved.removeAll(authoredTerrainFill);
        return Optional.of(rawDeployment(
                volume.id(),
                Feature.CHANNEL,
                new ArrayList<>(water),
                new ArrayList<>(carved),
                new ArrayList<>(surface),
                new ArrayList<>(forcedSurface)));
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

    /**
     * Returns the bounded solid-bank reconciliation needed to contain one planned wet column.
     *
     * <p>AUTH-0105 owns the channel route and dry bank geometry; the independently compiled
     * Minecraft carrier may quantize a neighboring dry bank a few blocks below the isotonic water
     * grade. Preserve the authored route by retaining/filling only that narrow bank discrepancy.
     * True void, foreign ownership, or a bank deeper than the small fill allowance still fails
     * closed. This is deliberately distinct from retained-basin terrain surgery: channel fill is
     * local to dry cardinal banks around an already-authored wet corridor.
     */
    private static Optional<List<BlockPos>> channelBankFillPositions(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<Column, ChannelColumnPlan> columns,
            Set<Column> plannedWet,
            ChannelColumnPlan candidate,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path,
            Optional<SkyIslandLocalPosition> routedOutlet,
            Map<Column, Integer> retainedWaterTopByColumn,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
        Objects.requireNonNull(retainedWaterTopByColumn, "retainedWaterTopByColumn");
        LinkedHashSet<BlockPos> fill = new LinkedHashSet<>();
        int outletBreaches = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            Column bank = new Column(
                    candidate.column().x() + direction[0],
                    candidate.column().z() + direction[1]);
            if (plannedWet.contains(bank)) {
                continue;
            }
            if (retainedWaterTopByColumn.containsKey(bank)) {
                // Standing water is an authored hydraulic opening. Its submerged physical carrier
                // is lake bed, not a dry side bank, and must not cap or reject the river surface.
                continue;
            }
            if (routedOutlet.isPresent()
                    && outletBreaches == 0
                    && channelOutletBreachAllowed(
                            volume,
                            candidate.column(),
                            bank,
                            reach,
                            path,
                            routedOutlet.orElseThrow())) {
                outletBreaches++;
                continue;
            }

            ChannelColumnPlan plannedBank = columns.get(bank);
            int bankTopY;
            if (plannedBank != null) {
                // A semantically wet column removed from the final contained component is not
                // carved later: realization restores it as ordinary solid terrain at baseSurfaceY.
                // Evaluate that same realized bank here. Using its lowered drySurfaceY would make
                // fixed-point containment pessimistically erode inward through perfectly solid
                // restored terrain and can eventually disconnect the authored centerline.
                boolean restoredRejectedWet =
                        plannedBank.waterTopY() != Integer.MIN_VALUE
                                && !plannedWet.contains(bank);
                bankTopY = restoredRejectedWet
                        ? plannedBank.baseSurfaceY()
                        : plannedBank.drySurfaceY();
                if (!uncontestedOwnedRangeCell(
                        terrain,
                        volume.id(),
                        bank.x(),
                        bankTopY,
                        bank.z())) {
                    return Optional.empty();
                }
            } else {
                var optionalRange = solidRangeCache.computeIfAbsent(
                        bank,
                        ignored -> terrain.integerSolidRange(
                                volume.id(), bank.x(), bank.z()));
                if (optionalRange.isEmpty()) {
                    // A missing cardinal carrier is true exterior geometry. Only the exact authored
                    // downstream outlet was admitted above; every other gap fails closed.
                    return Optional.empty();
                } else {
                    var range = optionalRange.orElseThrow();
                    bankTopY = range.maximumY();
                    if (!uncontestedOwnedRangeCell(
                            terrain,
                            volume.id(),
                            bank.x(),
                            bankTopY,
                            bank.z())) {
                        return Optional.empty();
                    }
                }
            }

            if (bankTopY >= candidate.waterTopY()) {
                continue;
            }

            int fillDepth = candidate.waterTopY() - bankTopY;
            if (fillDepth > MAX_CHANNEL_BANK_FILL_BLOCKS) {
                return Optional.empty();
            }

            for (int y = bankTopY + 1; y <= candidate.waterTopY(); y++) {
                if (!volume.bounds().contains(bank.x(), y, bank.z())
                        || terrain.isSolidOwnedByOtherVolume(
                                volume.id(), bank.x(), y, bank.z())) {
                    return Optional.empty();
                }
                fill.add(new BlockPos(bank.x(), y, bank.z()));
            }
        }
        return Optional.of(List.copyOf(fill));
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

    static int physicalTerrainDeltaBlocks(
            SkyIslandDescriptor descriptor,
            double normalizedDelta) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(normalizedDelta)) {
            throw new IllegalArgumentException("normalizedDelta must be finite");
        }
        if (Math.abs(normalizedDelta) <= 1.0e-12) {
            return 0;
        }
        int magnitude = Math.max(
                1,
                (int) Math.round(Math.abs(normalizedDelta) * descriptor.reliefBudget()));
        return normalizedDelta < 0.0 ? -magnitude : magnitude;
    }

    static int maximumAuthoredTerrainRaisingBlocks(SkyIslandDescriptor descriptor) {
        return physicalTerrainDeltaBlocks(
                descriptor,
                SkyIslandHydrologicTerrainSurfacePlanner.MAX_RAISING);
    }

    private static int projectedTerrainSurfaceY(
            SkyIslandDescriptor descriptor,
            int baseSurfaceY,
            int minimumY,
            double basePotential,
            double targetPotential) {
        int projected = baseSurfaceY
                + physicalTerrainDeltaBlocks(
                        descriptor,
                        targetPotential - basePotential);
        return Math.max(minimumY, projected);
    }

    private static boolean authoredSurfaceExtensionFeasible(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Column column,
            int baseSurfaceY,
            int targetSurfaceY) {
        if (targetSurfaceY <= baseSurfaceY) {
            return true;
        }
        for (int y = baseSurfaceY + 1; y <= targetSurfaceY; y++) {
            if (!volume.bounds().contains(column.x(), y, column.z())
                    || terrain.isSolidOwnedByOtherVolume(
                            volume.id(), column.x(), y, column.z())) {
                return false;
            }
        }
        return true;
    }

    private static Optional<List<BlockPos>> authoredSurfaceExtensionPositions(
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Column column,
            int baseSurfaceY,
            int targetSurfaceY) {
        if (!authoredSurfaceExtensionFeasible(
                volume, terrain, column, baseSurfaceY, targetSurfaceY)) {
            return Optional.empty();
        }
        if (targetSurfaceY <= baseSurfaceY) {
            return Optional.of(List.of());
        }
        List<BlockPos> result = new ArrayList<>(targetSurfaceY - baseSurfaceY);
        for (int y = baseSurfaceY + 1; y <= targetSurfaceY; y++) {
            result.add(new BlockPos(column.x(), y, column.z()));
        }
        return Optional.of(List.copyOf(result));
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
            Optional<SkyIslandLocalPosition> routedOutlet,
            Map<Column, Integer> retainedWaterTopByColumn,
            RetainedApproachWindow retainedApproach,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
        if (candidateProjections.isEmpty() || !(reach.path().pathLength() > 0.0)) {
            return Optional.empty();
        }

        Map<Column, ChannelCarrierCandidate> carrierCandidates = new LinkedHashMap<>();
        boolean reachTouchesRetainedWater = candidateProjections.keySet().stream()
                .anyMatch(column -> retainedHydraulicTarget(
                                column, retainedWaterTopByColumn)
                        .isPresent());

        List<String> carrierRejections = new ArrayList<>();
        int wetCorridorCandidates = 0;
        int solidCarrierCandidates = 0;
        int bankableCarrierCandidates = 0;
        for (var entry : candidateProjections.entrySet()) {
            ChannelPathProjection projection = entry.getValue();
            if (projection.distance() > effectiveWetHalfWidth(
                    fluvial,
                    reach,
                    projection,
                    entry.getKey(),
                    retainedWaterTopByColumn,
                    retainedApproach)) {
                continue;
            }
            wetCorridorCandidates++;
            Column column = entry.getKey();
            var optionalRange = solidRangeCache.computeIfAbsent(
                    column,
                    ignored -> terrain.integerSolidRange(
                            volume.id(), column.x(), column.z()));
            if (optionalRange.isEmpty()) {
                continue;
            }
            solidCarrierCandidates++;
            var range = optionalRange.orElseThrow();
            SkyIslandLocalPosition local = localPosition(volume, column);
            double basePotential = basePotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.baseTerrain().baseElevation(local));
            double dryPotential = dryPotentialCache.computeIfAbsent(
                    column,
                    ignored -> fluvial.sample(local));
            double waterPotential = fluvial.reachWaterSurfacePotential(
                    reach, projection.fraction());

            int baseSurfaceY = range.maximumY();
            double authoredBankPotential = fluvial.baseTerrain().sample(local);
            int authoredBankSurfaceY = projectedTerrainSurfaceY(
                    descriptor,
                    baseSurfaceY,
                    range.minimumY(),
                    basePotential,
                    authoredBankPotential);
            int drySurfaceY = projectedTerrainSurfaceY(
                    descriptor,
                    baseSurfaceY,
                    range.minimumY(),
                    basePotential,
                    dryPotential);
            drySurfaceY = Math.max(
                    range.minimumY(),
                    Math.min(drySurfaceY, authoredBankSurfaceY - 2));
            if (!authoredSurfaceExtensionFeasible(
                    volume, terrain, column, baseSurfaceY, authoredBankSurfaceY)
                    || !authoredSurfaceExtensionFeasible(
                            volume, terrain, column, baseSurfaceY, drySurfaceY)) {
                continue;
            }
            int ownerMinimumWaterTop = range.minimumY() + 1;
            int maximumWaterTop = authoredBankSurfaceY - 1;
            OptionalInt bankCeiling = channelBankCeiling(
                    descriptor,
                    volume,
                    terrain,
                    fluvial,
                    reach,
                    candidateProjections,
                    column,
                    drySurfaceY,
                    routedOutlet,
                    retainedWaterTopByColumn,
                    retainedApproach,
                    solidRangeCache,
                    basePotentialCache,
                    dryPotentialCache);
            if (bankCeiling.isEmpty()) {
                if (wetCorridorCandidates <= 64) {
                    carrierRejections.add(column.x() + "," + column.z()
                            + ":bankCeilingEmpty@" + projection.fraction());
                }
                continue;
            }
            bankableCarrierCandidates++;
            // Bank containment is a grade constraint, not permission to manufacture a levee.
            // MAX_CHANNEL_BANK_FILL_BLOCKS is zero by policy, so this reduces the admissible water
            // surface to the real post-fluvial bank ceiling.
            int rawBankCeiling = bankCeiling.orElseThrow();
            if (rawBankCeiling != Integer.MAX_VALUE) {
                maximumWaterTop = Math.min(
                        maximumWaterTop,
                        rawBankCeiling + MAX_CHANNEL_BANK_FILL_BLOCKS);
            }
            if (ownerMinimumWaterTop > maximumWaterTop) {
                if (wetCorridorCandidates <= 64) {
                    carrierRejections.add(column.x() + "," + column.z()
                            + ":bounds[" + ownerMinimumWaterTop + ">" + maximumWaterTop
                            + "]@" + projection.fraction());
                }
                continue;
            }

            int desiredWaterTop = baseSurfaceY
                    + physicalSignedDeltaBlocks(
                            descriptor,
                            waterPotential - basePotential);
            int preferenceWeight = 1;
            Optional<RetainedJunctionTarget> retainedTarget =
                    reachTouchesRetainedWater
                            && retainedApproach.authorizes(
                                    projection.fraction(), reach.path().pathLength())
                    ? retainedHydraulicBlendTarget(column, retainedWaterTopByColumn)
                    : Optional.empty();
            if (retainedTarget.isPresent()) {
                RetainedJunctionTarget target = retainedTarget.orElseThrow();
                desiredWaterTop = target.datum();
                preferenceWeight = retainedJunctionPreferenceWeight(target.distanceBlocks());
            }
            desiredWaterTop = Math.max(
                    ownerMinimumWaterTop,
                    Math.min(maximumWaterTop, desiredWaterTop));

            carrierCandidates.put(
                    column,
                    new ChannelCarrierCandidate(
                            column,
                            projection.distance(),
                            projection.fraction(),
                            ownerMinimumWaterTop,
                            drySurfaceY,
                            maximumWaterTop,
                            desiredWaterTop,
                            preferenceWeight));
        }

        // A semantic wet corridor that is mostly physical void is not a conditioning problem:
        // there is no credible Minecraft carrier to preserve. Fail closed before endpoint/path
        // relaxation so historical atlas specimens cannot be rescued by a few isolated columns.
        if (solidCarrierCandidates * 2 < wetCorridorCandidates) {
            throw channelProjectionFailure(
                    volume,
                    reach.path(),
                    "authored wet corridor is mostly physical void; wetCorridorCandidates="
                            + wetCorridorCandidates
                            + ", solidCarrierCandidates=" + solidCarrierCandidates
                            + ", bankableCarrierCandidates=" + bankableCarrierCandidates);
        }

        if (carrierCandidates.isEmpty()) {
            throw channelProjectionFailure(
                    volume,
                    reach.path(),
                    "no bankable raster carrier; wetCorridorCandidates=" + wetCorridorCandidates
                            + ", solidCarrierCandidates=" + solidCarrierCandidates
                            + ", bankableCarrierCandidates=" + bankableCarrierCandidates);
        }

        Optional<List<ChannelCarrierCandidate>> connectedSpine =
                connectedChannelCarrierSpine(volume, fluvial, reach, carrierCandidates);
        if (connectedSpine.isEmpty()) {
            var physicalDescriptor = volume.compiledVolume().descriptor();
            double endpointTolerance = Math.max(
                    fluvial.wetHalfWidthAt(reach, 0.0),
                    fluvial.wetHalfWidthAt(reach, 1.0)) + 1.0;
            SkyIslandLocalPosition upstream = reach.path().points().getFirst();
            SkyIslandLocalPosition downstream = reach.path().points().getLast();
            long upstreamCandidates = carrierCandidates.values().stream()
                    .filter(candidate -> Math.hypot(
                                    candidate.column().x() - physicalDescriptor.centerX() - upstream.x(),
                                    candidate.column().z() - physicalDescriptor.centerZ() - upstream.z())
                            <= endpointTolerance)
                    .count();
            long downstreamCandidates = carrierCandidates.values().stream()
                    .filter(candidate -> Math.hypot(
                                    candidate.column().x() - physicalDescriptor.centerX() - downstream.x(),
                                    candidate.column().z() - physicalDescriptor.centerZ() - downstream.z())
                            <= endpointTolerance)
                    .count();
            throw channelProjectionFailure(
                    volume,
                    reach.path(),
                    "bankable wet carrier has no four-connected upstream/downstream spine; "
                            + "bankableCarrierCandidates=" + carrierCandidates.size()
                            + ", upstreamCandidates=" + upstreamCandidates
                            + ", downstreamCandidates=" + downstreamCandidates
                            + ", wetCorridorCandidates=" + wetCorridorCandidates
                            + ", solidCarrierCandidates=" + solidCarrierCandidates
                            + ", carrierRejections=" + carrierRejections);
        }
        List<ChannelCarrierCandidate> spine = connectedSpine.orElseThrow();

        for (int reconciliationDepth = 0;
                reconciliationDepth <= MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS;
                reconciliationDepth++) {
            List<ChannelGradeSample> samples = new ArrayList<>(spine.size());
            boolean feasibleBounds = true;
            double previousFraction = 0.0;
            for (int index = 0; index < spine.size(); index++) {
                ChannelCarrierCandidate candidate = spine.get(index);
                int minimumWaterTop = Math.max(
                        candidate.ownerMinimumWaterTop(),
                        candidate.drySurfaceY() + 1 - reconciliationDepth);
                int crossSectionMaximumWaterTop = crossSectionMaximumWaterTop(
                        reach,
                        candidate,
                        carrierCandidates.values());
                if (minimumWaterTop > crossSectionMaximumWaterTop) {
                    feasibleBounds = false;
                    break;
                }
                double fraction = index == 0
                        ? candidate.fraction()
                        : Math.max(previousFraction, candidate.fraction());
                samples.add(new ChannelGradeSample(
                        fraction,
                        minimumWaterTop,
                        crossSectionMaximumWaterTop,
                        Math.min(candidate.desiredWaterTop(), crossSectionMaximumWaterTop),
                        candidate.preferenceWeight()));
                previousFraction = fraction;
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
            Set<Column> spineColumns = spine.stream()
                    .map(ChannelCarrierCandidate::column)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return Optional.of(new PhysicalChannelGrade(
                    fractions,
                    solved.orElseThrow(),
                    reconciliationDepth,
                    spineColumns));
        }
        throw channelProjectionFailure(
                volume,
                reach.path(),
                "four-connected raster spine remains isotonic-infeasible through reconciliationDepth="
                        + MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS
                        + ", spineColumns=" + spine.size()
                        + ", bankableCarrierCandidates=" + carrierCandidates.size());
    }

    /**
     * Lowest real lateral-bank ceiling represented by the wet raster around one longitudinal
     * spine sample.
     *
     * <p>The connected spine chooses where the river travels, but the free surface belongs to the
     * whole cross-section. Constraining the isotonic solve only with the selected spine lets a
     * nearby wet pixel discover a lower real bank later and get pruned, which can break authored
     * centerline coverage. A one-and-a-half-voxel longitudinal window folds those lateral carrier
     * constraints into the grade solve without changing routing or manufacturing solid support.
     */
    private static int crossSectionMaximumWaterTop(
            SkyIslandFluvialReachGeometry reach,
            ChannelCarrierCandidate center,
            java.util.Collection<ChannelCarrierCandidate> candidates) {
        // A retained-water junction is an authored hydraulic opening, not a closed river
        // cross-section. Its exact local carrier ceiling remains authoritative while the weighted
        // grade solve converges on the retained datum; importing a neighboring dry-bank ceiling
        // here would recreate a separate terrace immediately before the lake.
        if (center.preferenceWeight() > 1) {
            return center.maximumWaterTop();
        }

        double pathLength = Math.max(1.0, reach.path().pathLength());
        double fractionRadius = 1.5 / pathLength;
        int maximum = center.maximumWaterTop();
        for (ChannelCarrierCandidate candidate : candidates) {
            if (Math.abs(candidate.fraction() - center.fraction()) <= fractionRadius + 1.0e-12) {
                maximum = Math.min(maximum, candidate.maximumWaterTop());
            }
        }
        return maximum;
    }

    /**
     * Chooses a deterministic four-neighbor carrier path through one authored wet corridor.
     *
     * <p>The old per-bin selection could pick individually excellent columns that belonged to
     * different raster components. This spatial pass instead minimizes centerline deviation while
     * requiring actual Minecraft face connectivity from the authored upstream endpoint to the
     * downstream endpoint. A small longitudinal tolerance permits voxel turns without allowing the
     * path to reverse materially upstream.
     */
    private static Optional<List<ChannelCarrierCandidate>> connectedChannelCarrierSpine(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach,
            Map<Column, ChannelCarrierCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        var physical = volume.compiledVolume().descriptor();
        SkyIslandLocalPosition upstream = reach.path().points().getFirst();
        SkyIslandLocalPosition downstream = reach.path().points().getLast();

        // Raster quantization can make the individually nearest endpoint samples belong to
        // different otherwise-credible carrier components. Search from every candidate that still
        // represents the authored endpoint within the same tolerance enforced by the qualification
        // surface, and accept any equivalently bounded downstream endpoint. This keeps connectivity
        // authoritative without turning one unlucky rounded pixel into a false no-carrier result.
        double endpointTolerance = Math.max(
                fluvial.wetHalfWidthAt(reach, 0.0),
                fluvial.wetHalfWidthAt(reach, 1.0)) + 1.0;
        List<ChannelCarrierCandidate> starts = candidates.values().stream()
                .filter(candidate -> Math.hypot(
                                candidate.column().x() - physical.centerX() - upstream.x(),
                                candidate.column().z() - physical.centerZ() - upstream.z())
                        <= endpointTolerance)
                .sorted(Comparator
                        .comparingDouble((ChannelCarrierCandidate candidate) -> Math.hypot(
                                candidate.column().x() - physical.centerX() - upstream.x(),
                                candidate.column().z() - physical.centerZ() - upstream.z()))
                        .thenComparingDouble(ChannelCarrierCandidate::distance)
                        .thenComparingInt(candidate -> candidate.column().z())
                        .thenComparingInt(candidate -> candidate.column().x()))
                .toList();
        Set<Column> goals = candidates.values().stream()
                .filter(candidate -> Math.hypot(
                                candidate.column().x() - physical.centerX() - downstream.x(),
                                candidate.column().z() - physical.centerZ() - downstream.z())
                        <= endpointTolerance)
                .map(ChannelCarrierCandidate::column)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (starts.isEmpty() || goals.isEmpty()) {
            return Optional.empty();
        }

        record QueueEntry(Column column, double cost) {}
        java.util.PriorityQueue<QueueEntry> queue = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::cost)
                        .thenComparingInt(entry -> entry.column().z())
                        .thenComparingInt(entry -> entry.column().x()));
        Map<Column, Double> cost = new HashMap<>();
        Map<Column, Column> predecessor = new HashMap<>();
        Set<Column> startColumns = new LinkedHashSet<>();
        for (ChannelCarrierCandidate startCandidate : starts) {
            Column column = startCandidate.column();
            double upstreamDistance = Math.hypot(
                    column.x() - physical.centerX() - upstream.x(),
                    column.z() - physical.centerZ() - upstream.z());
            double startCost = upstreamDistance * upstreamDistance
                    + startCandidate.distance() * startCandidate.distance();
            double previousCost = cost.getOrDefault(column, Double.POSITIVE_INFINITY);
            if (startCost + 1.0e-12 < previousCost) {
                cost.put(column, startCost);
                predecessor.remove(column);
                queue.add(new QueueEntry(column, startCost));
            }
            startColumns.add(column);
        }

        double pathLength = Math.max(1.0, reach.path().pathLength());
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        ChannelCarrierCandidate bestGoal = null;
        double bestGoalCost = Double.POSITIVE_INFINITY;
        while (!queue.isEmpty()) {
            QueueEntry currentEntry = queue.remove();
            double knownCost = cost.getOrDefault(currentEntry.column(), Double.POSITIVE_INFINITY);
            if (currentEntry.cost() > knownCost + 1.0e-12) {
                continue;
            }
            if (knownCost > bestGoalCost + 1.0e-12) {
                break;
            }

            ChannelCarrierCandidate current = candidates.get(currentEntry.column());
            if (goals.contains(currentEntry.column())) {
                double downstreamDistance = Math.hypot(
                        current.column().x() - physical.centerX() - downstream.x(),
                        current.column().z() - physical.centerZ() - downstream.z());
                double terminalCost = knownCost + downstreamDistance * downstreamDistance;
                if (terminalCost + 1.0e-12 < bestGoalCost
                        || (Math.abs(terminalCost - bestGoalCost) <= 1.0e-12
                                && (bestGoal == null
                                        || current.column().z() < bestGoal.column().z()
                                        || (current.column().z() == bestGoal.column().z()
                                                && current.column().x() < bestGoal.column().x())))) {
                    bestGoal = current;
                    bestGoalCost = terminalCost;
                }
            }

            for (int[] direction : directions) {
                Column neighborColumn = new Column(
                        currentEntry.column().x() + direction[0],
                        currentEntry.column().z() + direction[1]);
                ChannelCarrierCandidate neighbor = candidates.get(neighborColumn);
                if (neighbor == null) {
                    continue;
                }

                // Nearest-path fractions are not guaranteed to be monotone on a four-neighbor
                // voxelization of a diagonal or meandering curve. Treat a local reverse step as a
                // search cost, not as a forbidden edge; the reconstructed grade samples are still
                // clamped nondecreasing in fraction before the isotonic Y solve.
                double longitudinalPenalty =
                        Math.max(0.0, current.fraction() - neighbor.fraction()) * 24.0;
                double centerlinePenalty = neighbor.distance() * neighbor.distance();
                double nextCost = knownCost + 1.0 + centerlinePenalty + longitudinalPenalty;
                double previousCost = cost.getOrDefault(neighborColumn, Double.POSITIVE_INFINITY);
                if (nextCost + 1.0e-12 < previousCost) {
                    cost.put(neighborColumn, nextCost);
                    predecessor.put(neighborColumn, currentEntry.column());
                    queue.add(new QueueEntry(neighborColumn, nextCost));
                }
            }
        }

        if (bestGoal == null) {
            return Optional.empty();
        }

        ArrayDeque<ChannelCarrierCandidate> reversed = new ArrayDeque<>();
        Column cursor = bestGoal.column();
        reversed.addFirst(candidates.get(cursor));
        while (!startColumns.contains(cursor) || predecessor.containsKey(cursor)) {
            Column previous = predecessor.get(cursor);
            if (previous == null) {
                break;
            }
            cursor = previous;
            reversed.addFirst(candidates.get(cursor));
        }
        if (!startColumns.contains(cursor)) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(reversed));
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
            previous[y - minimumY] = first.preferenceWeight() * delta * delta;
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
                current[offset] = suffixCost[offset]
                        + sample.preferenceWeight() * delta * delta;
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
            int candidateDrySurfaceY,
            Optional<SkyIslandLocalPosition> routedOutlet,
            Map<Column, Integer> retainedWaterTopByColumn,
            RetainedApproachWindow retainedApproach,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache,
            Map<Column, Double> basePotentialCache,
            Map<Column, Double> dryPotentialCache) {
        int ceiling = Integer.MAX_VALUE;
        int outletBreaches = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] direction : directions) {
            Column bank = new Column(wet.x() + direction[0], wet.z() + direction[1]);
            if (retainedWaterTopByColumn.containsKey(bank)) {
                // The basin supplies water at this face. Its submerged support is not a dry-bank
                // ceiling and must not drag the adjoining channel down to the lake bed.
                continue;
            }
            ChannelPathProjection bankProjection = candidateProjections.get(bank);
            var optionalRange = solidRangeCache.computeIfAbsent(
                    bank,
                    ignored -> terrain.integerSolidRange(
                            volume.id(), bank.x(), bank.z()));

            // A physically supported neighbor inside the authored wet corridor is expected to be
            // another wet carrier cell, so it is not a lateral bank constraint. A geometric wet
            // neighbor with no physical carrier is different: it leaves an actual voxel-side gap
            // and must be handled by the same bounded shelf rule as an absent dry bank.
            if (bankProjection != null
                    && bankProjection.distance() <= effectiveWetHalfWidth(
                            fluvial,
                            reach,
                            bankProjection,
                            bank,
                            retainedWaterTopByColumn,
                            retainedApproach)
                    && optionalRange.isPresent()) {
                continue;
            }

            boolean outletBreach = routedOutlet.isPresent()
                    && outletBreaches == 0
                    && channelOutletBreachAllowed(
                            volume,
                            wet,
                            bank,
                            reach,
                            reach.path(),
                            routedOutlet.orElseThrow());
            if (outletBreach) {
                outletBreaches++;
                continue;
            }
            if (optionalRange.isEmpty()) {
                // Do not invent a lateral shelf over exterior void. The exact authored downstream
                // outlet was handled above; every other missing side carrier fails closed.
                return OptionalInt.empty();
            }

            var range = optionalRange.orElseThrow();
            int bankTopY = range.maximumY();
            if (bankProjection != null) {
                SkyIslandLocalPosition bankLocal = localPosition(volume, bank);
                double basePotential = basePotentialCache.computeIfAbsent(
                        bank,
                        ignored -> fluvial.baseTerrain().baseElevation(bankLocal));
                double dryPotential = dryPotentialCache.computeIfAbsent(
                        bank,
                        ignored -> fluvial.sample(bankLocal));
                bankTopY = projectedTerrainSurfaceY(
                        descriptor,
                        range.maximumY(),
                        range.minimumY(),
                        basePotential,
                        dryPotential);
                if (!authoredSurfaceExtensionFeasible(
                        volume,
                        terrain,
                        bank,
                        range.maximumY(),
                        bankTopY)) {
                    return OptionalInt.empty();
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

    private static boolean channelEndpointCapAllowed(
            SkyIslandWorldVolume volume,
            Column wet,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path) {
        SkyIslandLocalPosition local = localPosition(volume, wet);
        double tolerance = Math.max(1.5, reach.wetHalfWidth() * 0.75);
        SkyIslandLocalPosition upstream = path.points().getFirst();
        SkyIslandLocalPosition downstream = path.points().getLast();
        return Math.hypot(local.x() - upstream.x(), local.z() - upstream.z()) <= tolerance
                || Math.hypot(local.x() - downstream.x(), local.z() - downstream.z()) <= tolerance;
    }

    private static boolean channelOutletBreachAllowed(
            SkyIslandWorldVolume volume,
            Column wet,
            Column bank,
            SkyIslandFluvialReachGeometry reach,
            SkyIslandNaturalizedChannelPath path,
            SkyIslandLocalPosition outlet) {
        SkyIslandLocalPosition local = localPosition(volume, wet);
        if (Math.hypot(local.x() - outlet.x(), local.z() - outlet.z())
                > Math.max(1.5, reach.wetHalfWidth() * 0.75)) {
            return false;
        }
        if (path.points().size() < 2) {
            return false;
        }

        double bestDistance = Double.POSITIVE_INFINITY;
        double downstreamX = 0.0;
        double downstreamZ = 0.0;
        for (int index = 1; index < path.points().size(); index++) {
            SkyIslandLocalPosition a = path.points().get(index - 1);
            SkyIslandLocalPosition b = path.points().get(index);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            if (!(lengthSquared > 0.0)) {
                continue;
            }
            double t = Math.max(0.0, Math.min(
                    1.0,
                    ((outlet.x() - a.x()) * dx + (outlet.z() - a.z()) * dz)
                            / lengthSquared));
            double qx = a.x() + t * dx;
            double qz = a.z() + t * dz;
            double distance = Math.hypot(outlet.x() - qx, outlet.z() - qz);
            if (distance < bestDistance) {
                bestDistance = distance;
                downstreamX = dx;
                downstreamZ = dz;
            }
        }

        int expectedX;
        int expectedZ;
        if (Math.abs(downstreamX) >= Math.abs(downstreamZ)) {
            expectedX = downstreamX >= 0.0 ? 1 : -1;
            expectedZ = 0;
        } else {
            expectedX = 0;
            expectedZ = downstreamZ >= 0.0 ? 1 : -1;
        }
        return bank.x() - wet.x() == expectedX
                && bank.z() - wet.z() == expectedZ;
    }

    private static Map<Column, ChannelPathProjection> candidateColumnProjections(
            SkyIslandWorldVolume volume,
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach) {
        var points = reach.path().points();
        if (points.size() < 2 || !(reach.path().pathLength() > 0.0)) {
            return Map.of();
        }

        double margin = Math.max(
                fluvial.valleyHalfWidthAt(reach, 0.0),
                fluvial.valleyHalfWidthAt(reach, 1.0));
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
            SkyIslandWaterbodyMargin margin,
            SkyIslandContinuousHydrologicTerrainField hydrologicTerrain,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(watershed, "watershed");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(margin, "margin");
        Objects.requireNonNull(hydrologicTerrain, "hydrologicTerrain");
        Objects.requireNonNull(solidRangeCache, "solidRangeCache");
        if (!margin.footprint().equals(footprint)) {
            throw new IllegalArgumentException(
                    "retained-water margin must reference the exact projected footprint");
        }
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

            Map<Column, RetainedColumnPlan> gradedCandidate =
                    gradeRetainedLittoral(connectedCandidate, candidateWaterTopY);
            Optional<List<BlockPos>> bankFill = retainedBankFillPositions(
                    volume,
                    terrain,
                    solidRangeCache,
                    gradedCandidate.keySet(),
                    candidateWaterTopY);
            if (bankFill.isEmpty()) {
                candidatesRejectedByContainment++;
                continue;
            }

            realizable = gradedCandidate;
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

        RetainedMarginConditioning marginConditioning = retainedMarginConditioning(
                descriptor,
                volume,
                terrain,
                watershed,
                margin,
                hydrologicTerrain,
                realizable.keySet(),
                waterTopY,
                solidRangeCache);

        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> carved =
                new LinkedHashSet<>(marginConditioning.carvedPositions());
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>(retainedBankFill);
        surface.addAll(marginConditioning.surfacePositions());
        LinkedHashSet<BlockPos> forcedSurface = new LinkedHashSet<>(retainedBankFill);
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
                new ArrayList<>(surface),
                new ArrayList<>(forcedSurface)));
    }

    static int maximumRetainedInteriorCutBlocks(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return Math.max(
                MAX_RETAINED_BASIN_CUT_BLOCKS,
                physicalLoweringBlocks(
                        descriptor,
                        SkyIslandHydrologicTerrainSurfacePlanner.MAX_LOWERING));
    }

    private static RetainedMarginConditioning retainedMarginConditioning(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyMargin margin,
            SkyIslandContinuousHydrologicTerrainField hydrologicTerrain,
            Set<Column> wetColumns,
            int waterTopY,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
        if (margin.cells().isEmpty()) {
            return new RetainedMarginConditioning(List.of(), List.of());
        }

        Map<Integer, SkyIslandWaterbodyMarginCell> marginByIndex = new HashMap<>();
        for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
            marginByIndex.put(cell.watershedCellIndex(), cell);
        }

        double halfSpacing = watershed.spacing() * 0.5;
        double minimumLocalX = margin.cells().stream()
                .mapToDouble(cell -> cell.position().x())
                .min()
                .orElseThrow() - halfSpacing;
        double maximumLocalX = margin.cells().stream()
                .mapToDouble(cell -> cell.position().x())
                .max()
                .orElseThrow() + halfSpacing;
        double minimumLocalZ = margin.cells().stream()
                .mapToDouble(cell -> cell.position().z())
                .min()
                .orElseThrow() - halfSpacing;
        double maximumLocalZ = margin.cells().stream()
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

        LinkedHashSet<BlockPos> carved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> surface = new LinkedHashSet<>();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                Column column = new Column(x, z);
                if (wetColumns.contains(column)) {
                    continue;
                }
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - physical.centerX(), z - physical.centerZ());
                int cellIndex = nearestWatershedCellIndex(descriptor, watershed, local);
                if (!marginByIndex.containsKey(cellIndex)) {
                    continue;
                }

                var optionalRange = solidRangeCache.computeIfAbsent(
                        column,
                        ignored -> terrain.integerSolidRange(
                                volume.id(), column.x(), column.z()));
                if (optionalRange.isEmpty()) {
                    continue;
                }
                var range = optionalRange.orElseThrow();
                if (!uncontestedOwnedRangeCell(
                        terrain,
                        volume.id(),
                        column.x(),
                        range.maximumY(),
                        column.z())) {
                    continue;
                }

                double lowering = Math.max(
                        0.0,
                        hydrologicTerrain.baseElevation(local)
                                - hydrologicTerrain.sample(local));
                if (lowering <= 1.0e-12) {
                    continue;
                }
                int targetSurfaceY = Math.max(
                        waterTopY,
                        Math.max(
                                range.minimumY(),
                                range.maximumY()
                                        - physicalLoweringBlocks(descriptor, lowering)));
                if (targetSurfaceY >= range.maximumY()) {
                    continue;
                }

                boolean uncontested = true;
                for (int y = targetSurfaceY; y <= range.maximumY(); y++) {
                    if (terrain.isSolidOwnedByOtherVolume(
                            volume.id(), column.x(), y, column.z())) {
                        uncontested = false;
                        break;
                    }
                }
                if (!uncontested) {
                    continue;
                }

                surface.add(new BlockPos(column.x(), targetSurfaceY, column.z()));
                for (int y = targetSurfaceY + 1; y <= range.maximumY(); y++) {
                    carved.add(new BlockPos(column.x(), y, column.z()));
                }
            }
        }
        return new RetainedMarginConditioning(
                List.copyOf(carved),
                List.copyOf(surface));
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

            // The accepted footprint owns inundation, but it is still coarse semantic intent rather
            // than permission to excavate an arbitrarily tall physical ridge. Visible perimeter
            // conditioning remains very strict; submerged interior conditioning may use the neutral
            // authored hydrologic-relief budget, but never an unbounded cut.
            int maximumCut = retainedFootprintBoundary(intendedFootprint, column.column())
                    ? MAX_RETAINED_BASIN_CUT_BLOCKS
                    : maximumRetainedInteriorCutBlocks(descriptor);
            if (cutAboveWater > maximumCut) {
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

    static Map<Column, RetainedColumnPlan> gradeRetainedLittoral(
            Map<Column, RetainedColumnPlan> basin,
            int waterTopY) {
        Objects.requireNonNull(basin, "basin");
        if (basin.isEmpty()) {
            return Map.of();
        }

        Map<Column, Integer> inwardDistance = new HashMap<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        for (Column column : basin.keySet()) {
            if (retainedFootprintBoundary(basin.keySet(), column)) {
                inwardDistance.put(column, 0);
                queue.addLast(column);
            }
        }

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Column current = queue.removeFirst();
            int nextDistance = inwardDistance.get(current) + 1;
            if (nextDistance >= RETAINED_LITTORAL_GRADE_RINGS) {
                continue;
            }
            for (int[] direction : directions) {
                Column neighbor = new Column(
                        current.x() + direction[0],
                        current.z() + direction[1]);
                if (!basin.containsKey(neighbor)
                        || inwardDistance.containsKey(neighbor)) {
                    continue;
                }
                inwardDistance.put(neighbor, nextDistance);
                queue.addLast(neighbor);
            }
        }

        Map<Column, RetainedColumnPlan> graded = new LinkedHashMap<>();
        for (var entry : basin.entrySet()) {
            RetainedColumnPlan plan = entry.getValue();
            Integer ring = inwardDistance.get(entry.getKey());
            int bedY = plan.bedY();
            if (ring != null && ring < RETAINED_LITTORAL_GRADE_RINGS) {
                int maximumDepth = ring + 1;
                // Littoral grading may preserve more of the existing carrier by reducing a cut,
                // but it is not terrain-fill authority. Never raise the submerged bed above the
                // compiled surface; any genuine bank fill remains explicit/provenanced through the
                // retained-bank reconciliation path.
                bedY = Math.min(
                        plan.baseSurfaceY(),
                        Math.max(bedY, waterTopY - maximumDepth));
            }
            graded.put(
                    entry.getKey(),
                    new RetainedColumnPlan(
                            plan.column(),
                            plan.sourceCell(),
                            plan.minimumY(),
                            plan.baseSurfaceY(),
                            bedY));
        }
        return Collections.unmodifiableMap(graded);
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
                addRetainedCandidateColumn(
                        result,
                        volume,
                        terrain,
                        sourceCell,
                        new Column(x, z),
                        solidRangeCache);
            }
        }

        // Close only tiny voxel pinholes that are already inside an accepted shoreline watershed
        // cell. This repairs square one-/two-column notches without expanding the lake into an
        // unauthored coarse cell or changing its hydraulic datum.
        for (int pass = 0; pass < 2; pass++) {
            Map<Column, RetainedColumnPlan> additions = new LinkedHashMap<>();
            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int x = minimumX; x <= maximumX; x++) {
                    Column column = new Column(x, z);
                    if (result.containsKey(column)
                            || retainedRasterNeighborCount(result.keySet(), column) < 3) {
                        continue;
                    }
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                            x - physical.centerX(), z - physical.centerZ());
                    int cellIndex = nearestWatershedCellIndex(descriptor, watershed, local);
                    SkyIslandWaterbodyFootprintCell sourceCell = cellsByIndex.get(cellIndex);
                    if (sourceCell == null || !sourceCell.shoreline()) {
                        continue;
                    }
                    addRetainedCandidateColumn(
                            additions,
                            volume,
                            terrain,
                            sourceCell,
                            column,
                            solidRangeCache);
                }
            }
            if (additions.isEmpty()) {
                break;
            }
            result.putAll(additions);
        }
        return result;
    }

    private static void addRetainedCandidateColumn(
            Map<Column, RetainedColumnPlan> target,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandWaterbodyFootprintCell sourceCell,
            Column column,
            Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache) {
        var optionalRange = solidRangeCache.computeIfAbsent(
                column,
                ignored -> terrain.integerSolidRange(
                        volume.id(), column.x(), column.z()));
        if (optionalRange.isEmpty()) {
            return;
        }
        var range = optionalRange.orElseThrow();
        if (!uncontestedOwnedRangeCell(
                terrain,
                volume.id(),
                column.x(),
                range.maximumY(),
                column.z())) {
            return;
        }
        target.put(
                column,
                new RetainedColumnPlan(
                        column,
                        sourceCell,
                        range.minimumY(),
                        range.maximumY(),
                        Integer.MIN_VALUE));
    }

    static int retainedRasterNeighborCount(
            Set<Column> footprint,
            Column candidate) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(candidate, "candidate");
        int count = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            if (footprint.contains(new Column(
                    candidate.x() + direction[0],
                    candidate.z() + direction[1]))) {
                count++;
            }
        }
        return count;
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
        return io.github.nidaba.skyforge.world.SkyIslandRetainedWaterFootprintGeometry
                .shorelineContains(local, sourceCell, cellsByIndex, watershed, halfSpacing);
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

    /**
     * Projects a semantically explicit channel whose two endpoints belong to the same retained
     * basin without giving it an independent submerged river grade.
     *
     * <p>The channel remains one-for-one authored evidence, but its physical water role is only the
     * retained basin water already occupying the channel's own wet corridor. It contributes no
     * carve or surface authority, so standing water remains the sole physical owner.
     */
    private static RawDeployment submergedChannelDeployment(
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandWorldVolume volume,
            SkyIslandNaturalizedChannelPath path,
            Map<Column, Integer> retainedWaterTopByColumn,
            List<RawDeployment> retainedDeployments) {
        Objects.requireNonNull(fluvial, "fluvial");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(retainedWaterTopByColumn, "retainedWaterTopByColumn");
        Objects.requireNonNull(retainedDeployments, "retainedDeployments");

        SkyIslandFluvialReachGeometry reach = fluvial.reaches().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "AUTH-0105 fluvial field lost accepted visible channel reach"));
        Map<Column, ChannelPathProjection> projections =
                candidateColumnProjections(volume, fluvial, reach);
        LinkedHashSet<BlockPos> sharedWater = new LinkedHashSet<>();
        for (RawDeployment retained : retainedDeployments) {
            if (retained.feature() != Feature.RETAINED_WATER) {
                continue;
            }
            for (BlockPos position : retained.positions()) {
                Column column = new Column(position.getX(), position.getZ());
                if (!retainedWaterTopByColumn.containsKey(column)) {
                    continue;
                }
                ChannelPathProjection projection = projections.get(column);
                if (projection == null
                        || projection.distance()
                                > fluvial.wetHalfWidthAt(reach, projection.fraction()) + 1.0e-12) {
                    continue;
                }
                sharedWater.add(position);
            }
        }
        if (sharedWater.isEmpty()) {
            throw channelProjectionFailure(
                    volume,
                    path,
                    "same-basin retained channel has no shared physical wet corridor");
        }
        return rawDeployment(
                volume.id(),
                Feature.CHANNEL,
                List.copyOf(sharedWater),
                List.of(),
                List.of(),
                List.of());
    }

    private static RawDeployment rawDeployment(
            SkyIslandWorldVolumeId volumeId,
            Feature feature,
            List<BlockPos> positions,
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions,
            List<BlockPos> forcedSurfacePositions) {
        Comparator<BlockPos> canonicalPositionOrder = Comparator
                .comparingInt((BlockPos position) -> position.getZ())
                .thenComparingInt(position -> position.getX())
                .thenComparingInt(position -> position.getY());
        List<BlockPos> canonicalPositions = new ArrayList<>(new LinkedHashSet<>(positions));
        List<BlockPos> canonicalCarved = new ArrayList<>(new LinkedHashSet<>(carvedPositions));
        List<BlockPos> canonicalSurface = new ArrayList<>(new LinkedHashSet<>(surfacePositions));
        List<BlockPos> canonicalForcedSurface =
                new ArrayList<>(new LinkedHashSet<>(forcedSurfacePositions));
        canonicalPositions.sort(canonicalPositionOrder);
        canonicalCarved.sort(canonicalPositionOrder);
        canonicalSurface.sort(canonicalPositionOrder);
        canonicalForcedSurface.sort(canonicalPositionOrder);
        return new RawDeployment(
                volumeId,
                feature,
                canonicalPositions,
                canonicalCarved,
                canonicalSurface,
                canonicalForcedSurface);
    }

    private static List<List<BlockPos>> closeVerticalWaterGaps(
            List<List<BlockPos>> waterByDeployment,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        Objects.requireNonNull(waterByDeployment, "waterByDeployment");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");

        List<LinkedHashSet<BlockPos>> closed = new ArrayList<>(waterByDeployment.size());
        Map<Column, java.util.TreeMap<Integer, Integer>> ownerByHeightByColumn =
                new LinkedHashMap<>();
        for (int deploymentIndex = 0; deploymentIndex < waterByDeployment.size(); deploymentIndex++) {
            LinkedHashSet<BlockPos> positions =
                    new LinkedHashSet<>(waterByDeployment.get(deploymentIndex));
            closed.add(positions);
            for (BlockPos position : positions) {
                ownerByHeightByColumn
                        .computeIfAbsent(
                                new Column(position.getX(), position.getZ()),
                                ignored -> new java.util.TreeMap<>())
                        .putIfAbsent(position.getY(), deploymentIndex);
            }
        }

        for (var entry : ownerByHeightByColumn.entrySet()) {
            var heights = entry.getValue();
            if (heights.size() < 2) {
                continue;
            }
            int minimumY = heights.firstKey();
            int maximumY = heights.lastKey();
            for (int y = minimumY + 1; y < maximumY; y++) {
                if (heights.containsKey(y)) {
                    continue;
                }
                Column column = entry.getKey();
                if (!volume.bounds().contains(column.x(), y, column.z())
                        || terrain.isSolidOwnedByOtherVolume(
                                volume.id(), column.x(), y, column.z())) {
                    continue;
                }
                var upper = heights.ceilingEntry(y);
                if (upper == null) {
                    throw new IllegalStateException(
                            "bounded hydrology water gap has no authored upper interval");
                }
                closed.get(upper.getValue())
                        .add(new BlockPos(column.x(), y, column.z()));
            }
        }

        Comparator<BlockPos> canonicalPositionOrder = Comparator
                .comparingInt((BlockPos position) -> position.getZ())
                .thenComparingInt(position -> position.getX())
                .thenComparingInt(position -> position.getY());
        List<List<BlockPos>> result = new ArrayList<>(closed.size());
        for (LinkedHashSet<BlockPos> positions : closed) {
            var canonical = new ArrayList<>(positions);
            canonical.sort(canonicalPositionOrder);
            result.add(List.copyOf(canonical));
        }
        return List.copyOf(result);
    }

    private static RetainedHydraulicBoundaryMatch retainedHydraulicBoundaryFor(
            SkyIslandNaturalizedChannelPath path,
            List<RetainedHydraulicBoundary> boundaries) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(boundaries, "boundaries");
        int sourceCell = path.profile().segment().sourceCellIndex();
        int downstreamCell = path.profile().segment().downstreamCellIndex();

        RetainedHydraulicBoundary match = null;
        boolean sourceConnected = false;
        boolean downstreamConnected = false;
        for (RetainedHydraulicBoundary boundary : boundaries) {
            boolean source = boundary.watershedCellIndices().contains(sourceCell);
            boolean downstream = boundary.watershedCellIndices().contains(downstreamCell);
            if (!source && !downstream) {
                continue;
            }
            if (match != null && match != boundary) {
                throw new IllegalStateException(
                        "one authored channel endpoint belongs to multiple retained-water bodies");
            }
            match = boundary;
            sourceConnected |= source;
            downstreamConnected |= downstream;
        }
        return match == null
                ? RetainedHydraulicBoundaryMatch.none()
                : new RetainedHydraulicBoundaryMatch(
                        match.waterTopByColumn(), sourceConnected, downstreamConnected);
    }

    private static Map<Column, Integer> retainedWaterTopByColumn(
            List<RawDeployment> retainedDeployments) {
        Objects.requireNonNull(retainedDeployments, "retainedDeployments");
        Map<Column, Integer> tops = new LinkedHashMap<>();
        for (RawDeployment deployment : retainedDeployments) {
            if (deployment.feature() != Feature.RETAINED_WATER) {
                throw new IllegalArgumentException(
                        "retained water-top index received non-retained deployment");
            }
            Map<Column, Integer> deploymentTops = new LinkedHashMap<>();
            for (BlockPos position : deployment.positions()) {
                Column column = new Column(position.getX(), position.getZ());
                deploymentTops.merge(column, position.getY(), Math::max);
            }
            for (var entry : deploymentTops.entrySet()) {
                Integer previous = tops.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && previous.intValue() != entry.getValue().intValue()) {
                    throw new IllegalStateException(
                            "connected retained-water column exposes conflicting physical datums at "
                                    + entry.getKey() + ": " + previous + " vs " + entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(tops);
    }

    static OptionalInt retainedHydraulicTarget(
            Column column,
            Map<Column, Integer> retainedWaterTopByColumn) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(retainedWaterTopByColumn, "retainedWaterTopByColumn");
        Integer datum = retainedWaterTopByColumn.get(column);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            Integer neighbor = retainedWaterTopByColumn.get(new Column(
                    column.x() + direction[0],
                    column.z() + direction[1]));
            if (neighbor == null) {
                continue;
            }
            if (datum == null) {
                datum = neighbor;
            } else if (datum.intValue() != neighbor.intValue()) {
                throw new IllegalStateException(
                        "channel contact touches retained water with conflicting physical datums");
            }
        }
        return datum == null ? OptionalInt.empty() : OptionalInt.of(datum);
    }

    static Optional<RetainedJunctionTarget> retainedHydraulicBlendTarget(
            Column column,
            Map<Column, Integer> retainedWaterTopByColumn) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(retainedWaterTopByColumn, "retainedWaterTopByColumn");

        Integer datum = null;
        int bestDistance = RETAINED_JUNCTION_BLEND_BLOCKS + 1;
        for (int dx = -RETAINED_JUNCTION_BLEND_BLOCKS;
                dx <= RETAINED_JUNCTION_BLEND_BLOCKS;
                dx++) {
            for (int dz = -RETAINED_JUNCTION_BLEND_BLOCKS;
                    dz <= RETAINED_JUNCTION_BLEND_BLOCKS;
                    dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance > RETAINED_JUNCTION_BLEND_BLOCKS || distance > bestDistance) {
                    continue;
                }
                Integer candidate = retainedWaterTopByColumn.get(
                        new Column(column.x() + dx, column.z() + dz));
                if (candidate == null) {
                    continue;
                }
                if (distance < bestDistance) {
                    datum = candidate;
                    bestDistance = distance;
                } else if (datum != null && datum.intValue() != candidate.intValue()) {
                    throw new IllegalStateException(
                            "channel transition is equally close to retained water with conflicting datums");
                }
            }
        }
        return datum == null
                ? Optional.empty()
                : Optional.of(new RetainedJunctionTarget(datum, bestDistance));
    }

    static Map<Column, Integer> qualifiedRetainedApproachTargets(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(path, "path");

        SkyIslandVisibleHydrologicRealizationPlan intent =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
        SkyIslandFluvialTerrainField fluvial =
                SkyIslandFluvialTerrainField.create(descriptor, intent.coherentHydrology());
        SkyIslandFluvialReachGeometry reach = fluvial.reaches().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow();

        Map<Column, Optional<SkyforgeExactVoxelSupportBounds.ColumnRange>> solidRangeCache =
                new HashMap<>();
        List<RetainedHydraulicBoundary> boundaries = new ArrayList<>();
        if (!intent.retainedWater().isEmpty()) {
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            for (var retained : intent.retainedWater()) {
                Optional<RawDeployment> projected = atFootprint(
                        descriptor,
                        volume,
                        terrain,
                        watershed,
                        retained.footprint(),
                        retained.margin(),
                        fluvial.baseTerrain(),
                        solidRangeCache);
                if (projected.isEmpty()) {
                    continue;
                }
                RawDeployment deployment = projected.orElseThrow();
                Set<Integer> watershedCells = retained.footprint().cells().stream()
                        .map(SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                boundaries.add(new RetainedHydraulicBoundary(
                        watershedCells,
                        retainedWaterTopByColumn(List.of(deployment))));
            }
        }

        RetainedHydraulicBoundaryMatch boundary =
                retainedHydraulicBoundaryFor(path, boundaries);
        if (boundary.waterTopByColumn().isEmpty()) {
            return Map.of();
        }

        Map<Column, ChannelPathProjection> projections =
                candidateColumnProjections(volume, fluvial, reach);
        RetainedApproachWindow window = retainedApproachWindow(
                fluvial,
                reach,
                projections,
                boundary.waterTopByColumn(),
                boundary.sourceConnected(),
                boundary.downstreamConnected());

        Map<Column, Integer> result = new LinkedHashMap<>();
        for (var entry : projections.entrySet()) {
            Column column = entry.getKey();
            if (boundary.waterTopByColumn().containsKey(column)) {
                continue;
            }
            ChannelPathProjection projection = entry.getValue();
            if (!window.authorizes(projection.fraction(), reach.path().pathLength())) {
                continue;
            }
            if (projection.distance() > effectiveWetHalfWidth(
                    fluvial,
                    reach,
                    projection,
                    column,
                    boundary.waterTopByColumn(),
                    window)) {
                continue;
            }
            retainedHydraulicTarget(column, boundary.waterTopByColumn())
                    .ifPresent(datum -> result.put(column, datum));
        }
        return Map.copyOf(result);
    }

    private static RetainedApproachWindow retainedApproachWindow(
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach,
            Map<Column, ChannelPathProjection> candidateProjections,
            Map<Column, Integer> retainedWaterTopByColumn,
            boolean sourceConnectedToRetained,
            boolean downstreamConnectedToRetained) {
        Objects.requireNonNull(fluvial, "fluvial");
        Objects.requireNonNull(reach, "reach");
        if (retainedWaterTopByColumn.isEmpty()
                || (!sourceConnectedToRetained && !downstreamConnectedToRetained)) {
            return RetainedApproachWindow.none();
        }

        double firstOutsideContact = Double.POSITIVE_INFINITY;
        double lastOutsideContact = Double.NEGATIVE_INFINITY;
        for (var entry : candidateProjections.entrySet()) {
            if (retainedWaterTopByColumn.containsKey(entry.getKey())) {
                continue;
            }
            ChannelPathProjection projection = entry.getValue();
            // The hydraulic mouth belongs to authored wet flow, not the surrounding valley.
            // A dry valley flank may approach a basin earlier/later than the river centerline;
            // allowing that flank to anchor the contact window can move all retained-datum
            // authorization off the actual wet corridor.
            if (projection.distance()
                    > fluvial.wetHalfWidthAt(reach, projection.fraction()) + 1.0e-12) {
                continue;
            }
            if (retainedHydraulicTarget(entry.getKey(), retainedWaterTopByColumn).isEmpty()) {
                continue;
            }
            double fraction = projection.fraction();
            firstOutsideContact = Math.min(firstOutsideContact, fraction);
            lastOutsideContact = Math.max(lastOutsideContact, fraction);
        }

        double sourceContact =
                sourceConnectedToRetained && Double.isFinite(firstOutsideContact)
                        ? firstOutsideContact
                        : Double.NaN;
        double downstreamContact =
                downstreamConnectedToRetained && Double.isFinite(lastOutsideContact)
                        ? lastOutsideContact
                        : Double.NaN;
        return new RetainedApproachWindow(sourceContact, downstreamContact);
    }

    private static double effectiveWetHalfWidth(
            SkyIslandFluvialTerrainField fluvial,
            SkyIslandFluvialReachGeometry reach,
            ChannelPathProjection projection,
            Column column,
            Map<Column, Integer> retainedWaterTopByColumn,
            RetainedApproachWindow retainedApproach) {
        double base = fluvial.wetHalfWidthAt(reach, projection.fraction());
        if (!retainedApproach.authorizes(
                projection.fraction(), reach.path().pathLength())) {
            return base;
        }
        Optional<RetainedJunctionTarget> target =
                retainedHydraulicBlendTarget(column, retainedWaterTopByColumn);
        if (target.isEmpty()) {
            return base;
        }

        // A reach that approaches standing water should broaden into that boundary rather than
        // remain a one-width trench touching a broad basin. Keep the flare inside the bankfull
        // corridor and within the same short datum-blend envelope used by the hydraulic grade.
        double blend = 1.0 - target.orElseThrow().distanceBlocks()
                / (double) (RETAINED_JUNCTION_BLEND_BLOCKS + 1);
        double boundedBlend = Math.max(0.0, Math.min(1.0, blend));
        double flared = base * (1.0 + 0.55 * boundedBlend);
        double bankfullLimit =
                fluvial.bankfullHalfWidthAt(reach, projection.fraction()) * 0.92;
        return Math.min(bankfullLimit, flared);
    }

    private static int retainedJunctionPreferenceWeight(int distanceBlocks) {
        if (distanceBlocks < 0 || distanceBlocks > RETAINED_JUNCTION_BLEND_BLOCKS) {
            throw new IllegalArgumentException("retained junction distance outside blend envelope");
        }
        int remaining = RETAINED_JUNCTION_BLEND_BLOCKS + 1 - distanceBlocks;
        return Math.max(
                2,
                RETAINED_JUNCTION_GRADE_WEIGHT
                        * remaining
                        / (RETAINED_JUNCTION_BLEND_BLOCKS + 1));
    }

    record RetainedJunctionTarget(int datum, int distanceBlocks) {
        RetainedJunctionTarget {
            if (distanceBlocks < 0 || distanceBlocks > RETAINED_JUNCTION_BLEND_BLOCKS) {
                throw new IllegalArgumentException("retained junction distance outside blend envelope");
            }
        }
    }

    private static boolean touchesRetainedColumn(
            Map<Long, Set<Column>> retainedColumnsByChunk,
            BlockPos position) {
        Objects.requireNonNull(retainedColumnsByChunk, "retainedColumnsByChunk");
        Objects.requireNonNull(position, "position");
        int[][] directions = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            BlockPos probe = position.offset(direction[0], 0, direction[1]);
            if (containsColumnByChunk(retainedColumnsByChunk, probe)) {
                return true;
            }
        }
        return false;
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

    private static void addColumnMembershipByChunk(
            Map<Long, Set<Column>> byChunk,
            BlockPos position) {
        Objects.requireNonNull(byChunk, "byChunk");
        Objects.requireNonNull(position, "position");
        long chunkKey = new ChunkPos(position).toLong();
        byChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                .add(new Column(position.getX(), position.getZ()));
    }

    private static boolean containsColumnByChunk(
            Map<Long, Set<Column>> byChunk,
            BlockPos position) {
        Objects.requireNonNull(byChunk, "byChunk");
        Objects.requireNonNull(position, "position");
        Set<Column> columns = byChunk.get(new ChunkPos(position).toLong());
        return columns != null && columns.contains(new Column(position.getX(), position.getZ()));
    }

    static Map<Long, ChunkProjection> indexByChunk(List<Deployment> deployments) {
        Objects.requireNonNull(deployments, "deployments");
        Map<Long, LinkedHashMap<BlockPos, BlockState>> mutable = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<BlockPos>> surfaceOwnership = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<BlockPos>> forcedSurfaceOwnership = new LinkedHashMap<>();
        for (Deployment deployment : deployments) {
            indexSurfaceOwnership(surfaceOwnership, deployment.surfacePositions());
            indexSurfaceOwnership(forcedSurfaceOwnership, deployment.forcedSurfacePositions());
            indexStates(mutable, deployment.carvedPositions(), Blocks.AIR.defaultBlockState());
            indexStates(mutable, deployment.positions(), Blocks.WATER.defaultBlockState());
        }

        LinkedHashSet<Long> chunkKeys = new LinkedHashSet<>();
        chunkKeys.addAll(mutable.keySet());
        chunkKeys.addAll(surfaceOwnership.keySet());
        chunkKeys.addAll(forcedSurfaceOwnership.keySet());
        Map<Long, ChunkProjection> result = new LinkedHashMap<>();
        for (long chunkKey : chunkKeys) {
            result.put(
                    chunkKey,
                    new ChunkProjection(
                            mutable.getOrDefault(chunkKey, new LinkedHashMap<>()),
                            surfaceOwnership.getOrDefault(chunkKey, new LinkedHashSet<>()),
                            forcedSurfaceOwnership.getOrDefault(chunkKey, new LinkedHashSet<>())));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void indexSurfaceOwnership(
            Map<Long, LinkedHashSet<BlockPos>> byChunk,
            Iterable<BlockPos> positions) {
        for (BlockPos position : positions) {
            long chunkKey = new ChunkPos(position).toLong();
            byChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>()).add(position);
        }
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

        // Surface ownership also includes preserved bed/bank cells that must never be filled merely
        // because this replay chunk currently contains AIR (for example after a carve). Only the
        // explicit bounded geometry-reconciliation subset is allowed to construct support.
        for (BlockPos position : bottomUp(projection.forcedSurfacePositions())) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                throw new IllegalArgumentException(
                        "authored hydrology chunk projection contains a foreign surface position");
            }
            BlockState current = chunk.getBlockState(position);
            if (!current.isAir()) {
                continue;
            }
            BlockState support = structuralSurfaceExtensionState(chunk, position);
            writeState(chunk, position, support);
            written++;
        }

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
                    writeState(chunk, position, desired);
                    written++;
                }
            } else if (desired.isAir()) {
                if (!current.isAir()) {
                    writeState(chunk, position, desired);
                    written++;
                }
            } else if (isHydrologySurfaceMaterial(desired)) {
                if (!current.equals(desired)) {
                    writeState(chunk, position, desired);
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
        for (BlockPos position : bottomUp(deployment.forcedSurfacePositions())) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            if (chunk.getBlockState(position).isAir()) {
                writeState(
                        chunk,
                        position,
                        structuralSurfaceExtensionState(chunk, position));
                written++;
            }
        }
        for (BlockPos position : deployment.carvedPositions()) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            if (!chunk.getBlockState(position).isAir()) {
                writeState(chunk, position, Blocks.AIR.defaultBlockState());
                written++;
            }
        }
        for (BlockPos position : deployment.positions()) {
            if (!chunk.getPos().equals(new ChunkPos(position))) {
                continue;
            }
            if (!isWaterBearing(chunk.getBlockState(position))) {
                writeState(chunk, position, Blocks.WATER.defaultBlockState());
                written++;
            }
        }
        return written;
    }

    /**
     * Writes one authored hydrology state and participates in the deferred stable-chunk lifecycle
     * when that lifecycle is active. Generation-time ProtoChunk writes remain ordinary chunk
     * mutations; late catch-up additionally invalidates lighting and notifies tracking clients.
     */
    private static void writeState(
            ChunkAccess chunk,
            BlockPos position,
            BlockState desired) {
        BlockState previous = chunk.getBlockState(position);
        if (previous.equals(desired)) {
            return;
        }
        chunk.setBlockState(position, desired, false);
        BlockState stored = chunk.getBlockState(position);
        if (!stored.equals(desired)) {
            throw new IllegalStateException(
                    "ChunkAccess did not retain authored hydrology state at " + position);
        }
        SkyforgeDeferredChunkMutationLifecycle.afterWrite(
                chunk,
                position,
                previous,
                stored);
    }

    /**
     * Runtime invariant for authored visible hydrology after Minecraft fluid simulation settles.
     *
     * <p>Literal WATER, FLOWING_WATER, and water-bearing states such as BUBBLE_COLUMN are all valid
     * physical realizations of the same authored wet cell. Air, lava, and unrelated blocks are not.
     */
    private static List<BlockPos> bottomUp(Iterable<BlockPos> positions) {
        List<BlockPos> ordered = new ArrayList<>();
        for (BlockPos position : positions) {
            ordered.add(position);
        }
        ordered.sort(Comparator
                .comparingInt((BlockPos position) -> position.getY())
                .thenComparingInt(position -> position.getZ())
                .thenComparingInt(position -> position.getX()));
        return ordered;
    }

    /**
     * Chooses representation for bounded bank geometry without authoring a hydrology palette.
     *
     * <p>Every permitted extension must grow upward from a real dry bank carrier. Copying that
     * carrier's live state means vanilla/native surface realization remains the material authority.
     * True void, fluids, or unsupported falling blocks fail closed.
     */
    private static BlockState structuralSurfaceExtensionState(
            ChunkAccess chunk,
            BlockPos position) {
        BlockPos supportPosition = position.below();
        BlockState support = chunk.getBlockState(supportPosition);
        if (support.isAir() || !support.getFluidState().isEmpty()) {
            throw new IllegalStateException(
                    "hydrology bank extension lost its native dry support at " + supportPosition);
        }
        return support;
    }

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
            int desiredWaterTop,
            int preferenceWeight) {
        private ChannelCarrierCandidate {
            if (preferenceWeight < 1) {
                throw new IllegalArgumentException("channel grade preference weight must be positive");
            }
        }
    }

    private record ChannelGradeSample(
            double fraction,
            int minimumWaterTop,
            int maximumWaterTop,
            int desiredWaterTop,
            int preferenceWeight) {
        private ChannelGradeSample {
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
                throw new IllegalArgumentException("channel grade fraction must be finite and in [0, 1]");
            }
            if (minimumWaterTop > maximumWaterTop) {
                throw new IllegalArgumentException("invalid channel grade sample bounds");
            }
            if (preferenceWeight < 1) {
                throw new IllegalArgumentException("channel grade preference weight must be positive");
            }
        }
    }

    private record PhysicalChannelGrade(
            List<Double> fractions,
            List<Integer> waterTops,
            int reconciliationDepth,
            Set<Column> spineColumns) {
        private PhysicalChannelGrade {
            fractions = List.copyOf(fractions);
            waterTops = List.copyOf(waterTops);
            spineColumns = Collections.unmodifiableSet(new LinkedHashSet<>(spineColumns));
            if (fractions.isEmpty() || fractions.size() != waterTops.size() || spineColumns.isEmpty()) {
                throw new IllegalArgumentException(
                        "physical channel grade requires matching nonempty samples and spine");
            }
            if (reconciliationDepth < 0
                    || reconciliationDepth > MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS) {
                throw new IllegalArgumentException("invalid channel reconciliation depth");
            }
        }

        private boolean contains(Column column) {
            return spineColumns.contains(column);
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

    private record RetainedApproachWindow(
            double sourceContactFraction,
            double downstreamContactFraction) {
        static RetainedApproachWindow none() {
            return new RetainedApproachWindow(Double.NaN, Double.NaN);
        }

        boolean authorizes(double fraction, double pathLength) {
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
                throw new IllegalArgumentException("fraction must be finite and in [0,1]");
            }
            double blendFraction = Math.min(
                    1.0,
                    RETAINED_JUNCTION_BLEND_BLOCKS / Math.max(1.0, pathLength));
            return (Double.isFinite(sourceContactFraction)
                            && Math.abs(fraction - sourceContactFraction)
                                    <= blendFraction + 1.0e-12)
                    || (Double.isFinite(downstreamContactFraction)
                            && Math.abs(fraction - downstreamContactFraction)
                                    <= blendFraction + 1.0e-12);
        }
    }

    private record RetainedHydraulicBoundaryMatch(
            Map<Column, Integer> waterTopByColumn,
            boolean sourceConnected,
            boolean downstreamConnected) {
        private RetainedHydraulicBoundaryMatch {
            waterTopByColumn = Map.copyOf(waterTopByColumn);
        }

        static RetainedHydraulicBoundaryMatch none() {
            return new RetainedHydraulicBoundaryMatch(Map.of(), false, false);
        }
    }

    private record RetainedHydraulicBoundary(
            Set<Integer> watershedCellIndices,
            Map<Column, Integer> waterTopByColumn) {
        private RetainedHydraulicBoundary {
            watershedCellIndices = Set.copyOf(watershedCellIndices);
            waterTopByColumn = Map.copyOf(waterTopByColumn);
        }
    }

    private record RetainedMarginConditioning(
            List<BlockPos> carvedPositions,
            List<BlockPos> surfacePositions) {
        private RetainedMarginConditioning {
            carvedPositions = List.copyOf(carvedPositions);
            surfacePositions = List.copyOf(surfacePositions);
        }
    }

    record Column(int x, int z) {}
}
