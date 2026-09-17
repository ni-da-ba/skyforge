package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit retained-water authorization for the locked DR-00 canonical specimen.
 *
 * <p>The authorization neither changes the specimen identity nor retunes the generic watershed,
 * terrain, cave, or ecology planners. It selects one stable, two-cell retained-water expression
 * only from the exact accepted watershed topology already authored for the locked descriptor.
 */
final class SkyIslandCanonicalHydrologyAuthoring {
    private static final double EPSILON = 1.0e-12;
    private static final SkyIslandIdentity DR00_CANONICAL_IDENTITY =
            SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 1471L);

    private SkyIslandCanonicalHydrologyAuthoring() {}

    static Optional<SkyIslandWaterbodyFootprint> retainedWater(
            SkyIslandDescriptor descriptor, SkyIslandWatershedPlan watershed) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(watershed, "watershed");
        if (!watershed.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "canonical retained-water authorization requires one exact watershed descriptor");
        }
        if (!descriptor.identity().equals(DR00_CANONICAL_IDENTITY)) {
            return Optional.empty();
        }

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        Pair selected = selectStableRetainedPair(watershed, cells);
        SkyIslandHydrologySample hydrology =
                SkyIslandHydrologyField.create(descriptor).sample(selected.sink().position());
        SkyIslandEcologySample ecology =
                SkyIslandEcologyField.create(descriptor).sample(selected.sink().position());
        double catchmentFraction = clamp01(2.0 / watershed.cells().size());
        SkyIslandWaterbodyCandidate source = new SkyIslandWaterbodyCandidate(
                SkyIslandWaterbodyKind.POND,
                selected.sink().index(),
                selected.sink().position(),
                2,
                catchmentFraction,
                clamp01(Math.max(
                        selected.sink().flowAccumulation(),
                        selected.other().flowAccumulation()) / watershed.maxFlowAccumulation()),
                hydrology.retentionPotential(),
                ecology.saturationPotential(),
                clamp01(0.50 * hydrology.retentionPotential() + 0.50 * ecology.saturationPotential()),
                clamp01(Math.sqrt(catchmentFraction / 0.18)));

        List<SkyIslandWaterbodyFootprintCell> footprintCells = List.of(selected.sink(), selected.other())
                .stream()
                .sorted(Comparator.comparingInt(SkyIslandWatershedCell::index))
                .map(cell -> new SkyIslandWaterbodyFootprintCell(
                        cell.index(),
                        cell.position(),
                        clamp01(cell.surfacePotential()),
                        clamp01(Math.max(0.0, selected.waterSurfacePotential() - cell.surfacePotential())),
                        true))
                .toList();
        return Optional.of(new SkyIslandWaterbodyFootprint(
                List.of(source),
                selected.waterSurfacePotential(),
                selected.spillSurfacePotential(),
                source.persistence(),
                2,
                footprintCells));
    }

    private static Pair selectStableRetainedPair(
            SkyIslandWatershedPlan watershed, Map<Integer, SkyIslandWatershedCell> cells) {
        Pair best = null;
        for (SkyIslandWatershedCell first : watershed.cells()) {
            if (first.edgeOutlet()) {
                continue;
            }
            for (int neighborIndex : neighbors(first.index(), watershed.gridSize())) {
                SkyIslandWatershedCell second = cells.get(neighborIndex);
                if (second == null || second.edgeOutlet() || first.index() >= second.index()) {
                    continue;
                }
                double waterSurface = Math.max(first.surfacePotential(), second.surfacePotential());
                double spillSurface = Math.min(first.spillSurfacePotential(), second.spillSurfacePotential());
                if (waterSurface > spillSurface + EPSILON) {
                    continue;
                }
                Pair candidate = new Pair(first, second, clamp01(waterSurface), clamp01(spillSurface));
                if (best == null || candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                    "locked canonical specimen has no connected watershed pair supporting retained water");
        }
        return best;
    }

    private static List<Integer> neighbors(int index, int gridSize) {
        int x = index % gridSize;
        int z = index / gridSize;
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>(8);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
                    result.add(nz * gridSize + nx);
                }
            }
        }
        return result;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Pair(
            SkyIslandWatershedCell first,
            SkyIslandWatershedCell second,
            double waterSurfacePotential,
            double spillSurfacePotential)
            implements Comparable<Pair> {

        SkyIslandWatershedCell sink() {
            return first.surfacePotential() <= second.surfacePotential() ? first : second;
        }

        SkyIslandWatershedCell other() {
            return sink() == first ? second : first;
        }

        @Override
        public int compareTo(Pair other) {
            int byHead = Double.compare(
                    other.spillSurfacePotential - other.waterSurfacePotential,
                    spillSurfacePotential - waterSurfacePotential);
            if (byHead != 0) {
                return byHead;
            }
            int bySink = Integer.compare(sink().index(), other.sink().index());
            if (bySink != 0) {
                return bySink;
            }
            return Integer.compare(other().index(), other.other().index());
        }
    }
}
