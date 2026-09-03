package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Integrates AUTH-0004 local hydrology into a deterministic coarse drainage graph. */
public final class SkyIslandWatershedPlanner {
    private static final int GRID_SIZE = 49;

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
        int[] downstream = new int[total];
        Arrays.fill(downstream, -1);

        for (int gz = 0; gz < GRID_SIZE; gz++) {
            double z = -extent + gz * spacing;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int i = index(gx, gz);
                double x = -extent + gx * spacing;
                SkyIslandLocalPosition p = new SkyIslandLocalPosition(x, z);
                double interiority = fields.interiority().sample(p);
                if (interiority <= 0.025) continue;
                active[i] = true;
                SkyIslandHydrologySample h = hydrology.sample(p);
                runoff[i] = h.runoffPotential();
                retention[i] = h.retentionPotential();
                // A slight interiority term guarantees an authored preference toward edge discharge
                // while preserving local relief as the dominant routing cause.
                surface[i] = fields.elevationTendency().sample(p) + 0.035 * interiority;
            }
        }

        for (int gz = 0; gz < GRID_SIZE; gz++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int i = index(gx, gz);
                if (!active[i]) continue;
                int best = -1;
                double bestSurface = surface[i];
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = gx + dx, nz = gz + dz;
                    if (nx < 0 || nz < 0 || nx >= GRID_SIZE || nz >= GRID_SIZE) continue;
                    int n = index(nx, nz);
                    if (!active[n]) continue;
                    double candidate = surface[n] + n * 1.0e-12;
                    if (candidate < bestSurface - 1.0e-10) {
                        bestSurface = candidate;
                        best = n;
                    }
                }
                downstream[i] = best;
            }
        }

        double[] accumulation = Arrays.copyOf(runoff, total);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < total; i++) if (active[i]) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> surface[i]).reversed().thenComparingInt(Integer::intValue));
        for (int i : order) {
            int d = downstream[i];
            if (d >= 0) accumulation[d] += accumulation[i];
        }

        double max = 0.0;
        for (int i : order) max = Math.max(max, accumulation[i]);
        List<SkyIslandWatershedCell> cells = new ArrayList<>(order.size());
        for (int gz = 0; gz < GRID_SIZE; gz++) {
            double z = -extent + gz * spacing;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int i = index(gx, gz);
                if (!active[i]) continue;
                double x = -extent + gx * spacing;
                boolean terminal = downstream[i] < 0;
                boolean edge = terminal && (gx <= 2 || gz <= 2 || gx >= GRID_SIZE - 3 || gz >= GRID_SIZE - 3
                        || fields.interiority().sample(new SkyIslandLocalPosition(x, z)) < 0.18);
                boolean retained = terminal && !edge && retention[i] >= 0.42;
                cells.add(new SkyIslandWatershedCell(i, new SkyIslandLocalPosition(x, z), surface[i], runoff[i], accumulation[i], downstream[i], retained, edge));
            }
        }
        return new SkyIslandWatershedPlan(descriptor, GRID_SIZE, spacing, cells, max);
    }

    private static int index(int x, int z) {
        return z * GRID_SIZE + x;
    }
}
