package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Integrates AUTH-0004 local hydrology into a deterministic coarse drainage graph. */
public final class SkyIslandWatershedPlanner {
    private static final int GRID_SIZE = 49;
    private static final double ACTIVE_THRESHOLD = 0.025;
    private static final double BASIN_RETENTION_THRESHOLD = 0.48;
    private static final double BASIN_FILL_DEPTH_THRESHOLD = 0.008;

    private SkyIslandWatershedPlanner() {}

    public static SkyIslandWatershedPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandHydrologyField hydrology = SkyIslandHydrologyField.create(descriptor);
        double radius = descriptor.nominalRadius();
        double extent = radius;
        double spacing = 2.0 * extent / (GRID_SIZE - 1.0);
        int total = GRID_SIZE * GRID_SIZE;
        boolean[] active = new boolean[total];
        double[] surface = new double[total];
        double[] runoff = new double[total];
        double[] retention = new double[total];

        for (int gz = 0; gz < GRID_SIZE; gz++) {
            double z = -extent + gz * spacing;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int i = index(gx, gz);
                double x = -extent + gx * spacing;
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(x, z);
                double interiority = fields.interiority().sample(p);
                if (interiority <= ACTIVE_THRESHOLD) {
                    continue;
                }
                active[i] = true;
                SkyIslandHydrologySample h = hydrology.sample(p);
                runoff[i] = h.runoffPotential();
                retention[i] = h.retentionPotential();
                surface[i] = fields.elevationTendency().sample(p);
            }
        }

        SpillRouting spill = spillRouting(active, surface);
        boolean[] retained = selectRetainedBasins(active, surface, retention, spill.spillLevel());
        int[] downstream = Arrays.copyOf(spill.parent(), spill.parent().length);
        for (int i = 0; i < total; i++) {
            if (retained[i]) {
                downstream[i] = -1;
            }
        }

        double[] accumulation = Arrays.copyOf(runoff, total);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (active[i]) {
                order.add(i);
            }
        }
        order.sort(Comparator.comparingInt((Integer i) -> spill.rank()[i]).reversed());
        for (int i : order) {
            int d = downstream[i];
            if (d >= 0) {
                accumulation[d] += accumulation[i];
            }
        }

        double max = 0.0;
        for (int i : order) {
            max = Math.max(max, accumulation[i]);
        }
        boolean[] outlets = selectSignificantOutlets(active, downstream, accumulation, max);

        List<SkyIslandWatershedCell> cells = new ArrayList<>(order.size());
        for (int gz = 0; gz < GRID_SIZE; gz++) {
            double z = -extent + gz * spacing;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int i = index(gx, gz);
                if (!active[i]) {
                    continue;
                }
                double x = -extent + gx * spacing;
                double fillDepth = Math.max(0.0, spill.spillLevel()[i] - surface[i]);
                cells.add(new SkyIslandWatershedCell(
                        i,
                        new SkyIslandLocalPosition(x, z),
                        surface[i],
                        spill.spillLevel()[i],
                        fillDepth,
                        runoff[i],
                        accumulation[i],
                        downstream[i],
                        retained[i],
                        outlets[i]));
            }
        }
        return new SkyIslandWatershedPlan(descriptor, GRID_SIZE, spacing, cells, max);
    }

    /**
     * Priority-flood style spill routing. Every ordinary cell receives a deterministic path toward
     * the island edge. Local minima therefore do not become hundreds of artificial lakes merely
     * because the coarse planning lattice sampled a shallow depression.
     */
    private static SpillRouting spillRouting(boolean[] active, double[] surface) {
        int total = active.length;
        double[] spillLevel = new double[total];
        Arrays.fill(spillLevel, Double.POSITIVE_INFINITY);
        int[] parent = new int[total];
        int[] rank = new int[total];
        Arrays.fill(parent, -1);
        Arrays.fill(rank, Integer.MAX_VALUE);
        boolean[] visited = new boolean[total];
        PriorityQueue<QueueCell> queue = new PriorityQueue<>(Comparator
                .comparingDouble(QueueCell::level)
                .thenComparingInt(QueueCell::index));

        for (int z = 0; z < GRID_SIZE; z++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int i = index(x, z);
                if (active[i] && isDomainBoundary(x, z, active)) {
                    spillLevel[i] = surface[i];
                    visited[i] = true;
                    queue.add(new QueueCell(i, surface[i]));
                }
            }
        }

        int nextRank = 0;
        while (!queue.isEmpty()) {
            QueueCell current = queue.remove();
            int i = current.index();
            if (rank[i] != Integer.MAX_VALUE) {
                continue;
            }
            rank[i] = nextRank++;
            int x = i % GRID_SIZE;
            int z = i / GRID_SIZE;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= GRID_SIZE || nz >= GRID_SIZE) {
                        continue;
                    }
                    int n = index(nx, nz);
                    if (!active[n] || visited[n]) {
                        continue;
                    }
                    visited[n] = true;
                    parent[n] = i;
                    spillLevel[n] = Math.max(surface[n], current.level());
                    queue.add(new QueueCell(n, spillLevel[n]));
                }
            }
        }
        return new SpillRouting(parent, rank, spillLevel);
    }

    private static boolean[] selectRetainedBasins(
            boolean[] active, double[] surface, double[] retention, double[] spillLevel) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < active.length; i++) {
            if (!active[i]) {
                continue;
            }
            double fillDepth = spillLevel[i] - surface[i];
            if (retention[i] >= BASIN_RETENTION_THRESHOLD && fillDepth >= BASIN_FILL_DEPTH_THRESHOLD) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((Integer i) -> (spillLevel[i] - surface[i]) * retention[i])
                .reversed()
                .thenComparingInt(Integer::intValue));

        boolean[] retained = new boolean[active.length];
        int accepted = 0;
        for (int candidate : candidates) {
            if (accepted >= 4 || nearRetained(candidate, retained, 6)) {
                continue;
            }
            retained[candidate] = true;
            accepted++;
        }
        return retained;
    }

    private static boolean nearRetained(int candidate, boolean[] retained, int radius) {
        int x = candidate % GRID_SIZE;
        int z = candidate / GRID_SIZE;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= GRID_SIZE || nz >= GRID_SIZE) {
                    continue;
                }
                if (retained[index(nx, nz)]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean[] selectSignificantOutlets(
            boolean[] active, int[] downstream, double[] accumulation, double max) {
        boolean[] outlets = new boolean[active.length];
        double threshold = max * 0.055;
        for (int i = 0; i < active.length; i++) {
            if (!active[i] || downstream[i] >= 0 || accumulation[i] < threshold) {
                continue;
            }
            int x = i % GRID_SIZE;
            int z = i / GRID_SIZE;
            if (!isDomainBoundary(x, z, active)) {
                continue;
            }
            boolean localMaximum = true;
            for (int dz = -3; dz <= 3 && localMaximum; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int nx = x + dx;
                    int nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= GRID_SIZE || nz >= GRID_SIZE) {
                        continue;
                    }
                    int n = index(nx, nz);
                    if (active[n] && downstream[n] < 0 && accumulation[n] > accumulation[i] + 1.0e-12) {
                        localMaximum = false;
                        break;
                    }
                }
            }
            outlets[i] = localMaximum;
        }
        return outlets;
    }

    private static boolean isDomainBoundary(int x, int z, boolean[] active) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= GRID_SIZE || nz >= GRID_SIZE || !active[index(nx, nz)]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int index(int x, int z) {
        return z * GRID_SIZE + x;
    }

    private record QueueCell(int index, double level) {}
    private record SpillRouting(int[] parent, int[] rank, double[] spillLevel) {}
}
