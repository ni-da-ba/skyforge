package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral continuous geometry for an accepted retained-water footprint.
 *
 * <p>Coarse footprint cells own finite areas, but shoreline cells are rounded according to authored
 * depth and connected to retained cardinal neighbors by rounded corridors. Backends and downstream
 * geomorphic planners must share this exact boundary so a channel drop localized at a lake edge is
 * realized at the same edge rather than several blocks upstream or downstream.
 */
public final class SkyIslandRetainedWaterFootprintGeometry {
    private static final double EPSILON = 1.0e-12;
    private static final int BOUNDARY_BISECTION_STEPS = 48;

    private SkyIslandRetainedWaterFootprintGeometry() {}

    public static boolean contains(
            SkyIslandDescriptor descriptor,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyFootprint footprint,
            SkyIslandLocalPosition local) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(watershed, "watershed");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(local, "local");
        if (!watershed.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException("watershed descriptor must match retained geometry descriptor");
        }

        Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex = cellsByIndex(footprint);
        SkyIslandWaterbodyFootprintCell source =
                cellsByIndex.get(nearestWatershedCellIndex(descriptor, watershed, local));
        if (source == null) {
            return false;
        }
        return shorelineContains(
                local,
                source,
                cellsByIndex,
                watershed,
                watershed.spacing() * 0.5);
    }

    public static boolean shorelineContains(
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

        double sourceRadius = shorelineRadius(sourceCell, halfSpacing);
        if (Math.hypot(
                        local.x() - sourceCell.position().x(),
                        local.z() - sourceCell.position().z())
                <= sourceRadius + EPSILON) {
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
                    ? shorelineRadius(neighbor, halfSpacing)
                    : halfSpacing;
            double corridorRadius = Math.min(sourceRadius, neighborRadius);
            if (distanceToSegment(local, sourceCell.position(), neighbor.position())
                    <= corridorRadius + EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the exact continuous boundary crossing for a path with exactly one retained endpoint.
     */
    public static Optional<SkyIslandLocalPosition> endpointBoundaryCrossing(
            SkyIslandDescriptor descriptor,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyFootprint footprint,
            SkyIslandNaturalizedChannelPath path) {
        Objects.requireNonNull(path, "path");
        Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex = cellsByIndex(footprint);
        int sourceCell = path.profile().segment().sourceCellIndex();
        int downstreamCell = path.profile().segment().downstreamCellIndex();
        boolean sourceRetained = cellsByIndex.containsKey(sourceCell);
        boolean downstreamRetained = cellsByIndex.containsKey(downstreamCell);
        if (sourceRetained == downstreamRetained) {
            return Optional.empty();
        }

        List<SkyIslandLocalPosition> points = path.points();
        if (points.size() < 2) {
            return Optional.empty();
        }
        boolean previousInside = contains(descriptor, watershed, footprint, points.getFirst());
        for (int index = 1; index < points.size(); index++) {
            SkyIslandLocalPosition previous = points.get(index - 1);
            SkyIslandLocalPosition current = points.get(index);
            boolean currentInside = contains(descriptor, watershed, footprint, current);
            if (previousInside != currentInside) {
                if ((sourceRetained && previousInside && !currentInside)
                        || (downstreamRetained && !previousInside && currentInside)) {
                    return Optional.of(boundaryPoint(
                            descriptor,
                            watershed,
                            footprint,
                            previous,
                            current,
                            previousInside));
                }
            }
            previousInside = currentInside;
        }
        return Optional.empty();
    }

    private static SkyIslandLocalPosition boundaryPoint(
            SkyIslandDescriptor descriptor,
            SkyIslandWatershedPlan watershed,
            SkyIslandWaterbodyFootprint footprint,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            boolean aInside) {
        double low = 0.0;
        double high = 1.0;
        for (int iteration = 0; iteration < BOUNDARY_BISECTION_STEPS; iteration++) {
            double middle = 0.5 * (low + high);
            SkyIslandLocalPosition probe = lerp(a, b, middle);
            if (contains(descriptor, watershed, footprint, probe) == aInside) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return lerp(a, b, 0.5 * (low + high));
    }

    private static SkyIslandLocalPosition lerp(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            double fraction) {
        return new SkyIslandLocalPosition(
                a.x() + (b.x() - a.x()) * fraction,
                a.z() + (b.z() - a.z()) * fraction);
    }

    private static Map<Integer, SkyIslandWaterbodyFootprintCell> cellsByIndex(
            SkyIslandWaterbodyFootprint footprint) {
        Map<Integer, SkyIslandWaterbodyFootprintCell> result = new LinkedHashMap<>();
        for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
            SkyIslandWaterbodyFootprintCell previous =
                    result.put(cell.watershedCellIndex(), cell);
            if (previous != null) {
                throw new IllegalStateException("retained footprint contains duplicate watershed cell");
            }
        }
        return Map.copyOf(result);
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

    private static double shorelineRadius(
            SkyIslandWaterbodyFootprintCell cell,
            double halfSpacing) {
        double depth = Math.sqrt(Math.max(0.0, cell.waterDepthPotential()));
        return halfSpacing * (0.40 + 0.60 * depth);
    }

    private static double distanceToSegment(
            SkyIslandLocalPosition position,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= EPSILON) {
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
}
