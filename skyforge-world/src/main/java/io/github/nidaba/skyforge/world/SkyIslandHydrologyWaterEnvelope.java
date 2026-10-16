package io.github.nidaba.skyforge.world;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finite river water intervals derived from qualified bed/stage geometry, not original solid
 * occupancy. Only face-connected intervals attached to an accepted river centerline survive.
 * This grants no excavation authority and carries no backend permission to overwrite foreign blocks.
 */
public final class SkyIslandHydrologyWaterEnvelope {
    private SkyIslandHydrologyWaterEnvelope() {}

    public record Column(int worldX, int worldZ, int minimumY, int maximumY) {
        public Column {
            if (maximumY < minimumY) {
                throw new IllegalArgumentException("empty water interval");
            }
        }
    }

    public static List<Column> columns(SkyIslandFluvialVoxelQuantizationPlan plan) {
        plan.candidatePlan().requireAssociation(plan.association());
        boolean associated = plan.candidatePlan().worldAssociation().isPresent();
        Map<Long, Column> bands = new LinkedHashMap<>();
        for (var column : plan.authorizedColumns()) {
            var projection = column.projection();
            var semantic = projection.semanticSample();
            if (!semantic.wet() || (!associated && !column.mutatesTerrain())) {
                continue;
            }
            double stage = projection.originalUpperSurfaceWorldY()
                    + (semantic.waterSurfacePotential() - semantic.originalTerrainPotential())
                            * plan.candidatePlan().descriptor().reliefBudget();
            if (!Double.isFinite(stage) || stage <= Integer.MIN_VALUE
                    || stage > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("water stage outside integer coordinates");
            }
            int top = (int) Math.ceil(stage) - 1;
            if (!associated) {
                top = Math.min(top, column.originalSupport().maximumSolidY());
            }
            int bottom = Math.addExact(column.targetMaximumSolidY(), 1);
            if (top >= bottom) {
                bands.put(key(column.worldX(), column.worldZ()),
                        new Column(column.worldX(), column.worldZ(), bottom, top));
            }
        }
        if (!associated) {
            return List.copyOf(bands.values());
        }

        Map<Long, Integer> labels = new LinkedHashMap<>();
        int nextLabel = 0;
        for (long start : bands.keySet()) {
            if (labels.containsKey(start)) {
                continue;
            }
            int label = ++nextLabel;
            var queue = new ArrayDeque<Long>();
            labels.put(start, label);
            queue.add(start);
            while (!queue.isEmpty()) {
                Column current = bands.get(queue.removeFirst());
                for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long neighbor = key(Math.addExact(current.worldX(), direction[0]),
                            Math.addExact(current.worldZ(), direction[1]));
                    Column other = bands.get(neighbor);
                    if (other != null && !labels.containsKey(neighbor)
                            && Math.max(current.minimumY(), other.minimumY())
                                    <= Math.min(current.maximumY(), other.maximumY())) {
                        labels.put(neighbor, label);
                        queue.add(neighbor);
                    }
                }
            }
        }
        var riverLabels = new java.util.HashSet<Integer>();
        double centerX = plan.association().realizedVolume().compiledVolume().descriptor().centerX();
        double centerZ = plan.association().realizedVolume().compiledVolume().descriptor().centerZ();
        for (var reach : plan.candidatePlan().terrainField().acceptedReaches()) {
            Integer routeLabel = null;
            for (var point : reach.centerline().points()) {
                Integer label = labels.get(key(Math.toIntExact(Math.round(centerX + point.x())),
                        Math.toIntExact(Math.round(centerZ + point.z()))));
                if (label == null || (routeLabel != null && !routeLabel.equals(label))) {
                    throw new IllegalArgumentException("river centerline lacks connected integer water");
                }
                routeLabel = label;
            }
            if (routeLabel != null) {
                riverLabels.add(routeLabel);
            }
        }
        // Disconnected peripheral low pockets are not authored lakes and acquire no authority.
        return bands.entrySet().stream()
                .filter(entry -> riverLabels.contains(labels.get(entry.getKey())))
                .map(Map.Entry::getValue).toList();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }
}
