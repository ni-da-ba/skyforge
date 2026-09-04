package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts AUTH-0022 continuous geological fields into connected mesoscale geological systems.
 *
 * <p>Fracture corridors are supported by a small deterministic family of oblique structural planes.
 * Aquifer bodies require both groundwater and connected permeability. Void-prone domains require
 * geological void suitability plus support from fractures, aquifers, and mid-depth stability.
 */
public final class SkyIslandGeologicRegionPlanner {
    public static final int GRID_SIZE = 25;
    public static final int DEPTH_SAMPLES = 13;
    public static final int MIN_REGION_CELLS = 5;

    private static final long CORRIDOR_DOMAIN = 0x47454F434F525249L;
    private static final long CORRIDOR_STEP = 0x9E3779B97F4A7C15L;

    private SkyIslandGeologicRegionPlanner() {}

    public static SkyIslandGeologicRegionPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double spacing = 2.0 * radius / (GRID_SIZE - 1.0);
        double depthSpacing = 1.0 / (DEPTH_SAMPLES - 1.0);
        int total = GRID_SIZE * DEPTH_SAMPLES * GRID_SIZE;

        boolean[] active = new boolean[total];
        double[] fracture = new double[total];
        double[] aquifer = new double[total];
        double[] voidProne = new double[total];

        int corridorCount = structuralCorridorCount(descriptor);
        double fractureThreshold = clamp(
                0.55
                        + 0.07 * descriptor.rockCompetence()
                        - 0.05 * descriptor.erosionMaturity(),
                0.50,
                0.61);
        double aquiferThreshold = clamp(
                0.55
                        - 0.04 * descriptor.hydrologicalPotential()
                        - 0.03 * descriptor.permeability(),
                0.49,
                0.55);
        double voidThreshold = clamp(
                0.57
                        + 0.03 * descriptor.rockCompetence()
                        - 0.03 * descriptor.erosionMaturity(),
                0.53,
                0.60);

        for (int iz = 0; iz < GRID_SIZE; iz++) {
            double z = -radius + iz * spacing;
            for (int id = 0; id < DEPTH_SAMPLES; id++) {
                double depth = id * depthSpacing;
                for (int ix = 0; ix < GRID_SIZE; ix++) {
                    double x = -radius + ix * spacing;
                    int index = index(ix, id, iz);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandGeologySample sample = geology.sample(position);
                    if (!sample.owned()) {
                        continue;
                    }
                    active[index] = true;

                    double nx = x / radius;
                    double nz = z / radius;
                    double corridor = structuralCorridorSupport(
                            descriptor,
                            corridorCount,
                            nx,
                            nz,
                            depth);
                    double fractureMembership = clamp01(
                            0.62 * sample.fractureIntensity()
                                    + 0.38 * corridor);

                    double depthWaterBand = smoothstep(0.18, 0.62, depth);
                    double aquiferMembership = clamp01(
                            0.58 * sample.groundwaterPotential()
                                    + 0.30 * sample.connectedPermeability()
                                    + 0.12 * depthWaterBand);

                    double midDepthBand =
                            clamp01(1.0 - Math.abs(depth - 0.53) / 0.53);
                    double voidMembership = clamp01(
                            0.58 * sample.voidFormationPotential()
                                    + 0.20 * fractureMembership
                                    + 0.14 * aquiferMembership
                                    + 0.08 * midDepthBand);

                    if (fractureMembership >= fractureThreshold) {
                        fracture[index] = fractureMembership;
                    }
                    if (aquiferMembership >= aquiferThreshold) {
                        aquifer[index] = aquiferMembership;
                    }
                    if (voidMembership >= voidThreshold) {
                        voidProne[index] = voidMembership;
                    }
                }
            }
        }

        List<SkyIslandGeologicRegion> regions = new ArrayList<>();
        appendRegions(
                regions,
                SkyIslandGeologicRegionKind.FRACTURE_CORRIDOR,
                active,
                fracture,
                radius,
                spacing,
                depthSpacing);
        appendRegions(
                regions,
                SkyIslandGeologicRegionKind.AQUIFER_BODY,
                active,
                aquifer,
                radius,
                spacing,
                depthSpacing);
        appendRegions(
                regions,
                SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN,
                active,
                voidProne,
                radius,
                spacing,
                depthSpacing);

        return new SkyIslandGeologicRegionPlan(
                descriptor,
                GRID_SIZE,
                DEPTH_SAMPLES,
                spacing,
                depthSpacing,
                corridorCount,
                regions);
    }

    public static int structuralCorridorCount(SkyIslandDescriptor descriptor) {
        double structuralActivity = clamp01(
                0.55 * descriptor.erosionMaturity()
                        + 0.45 * (1.0 - descriptor.rockCompetence()));
        return 1 + (int) Math.round(2.0 * structuralActivity);
    }

    private static double structuralCorridorSupport(
            SkyIslandDescriptor descriptor,
            int corridorCount,
            double x,
            double z,
            double depth) {
        double support = 0.0;
        for (int corridor = 0; corridor < corridorCount; corridor++) {
            long seed = descriptor.authorshipSeed()
                    ^ CORRIDOR_DOMAIN
                    ^ (CORRIDOR_STEP * (corridor + 1L));
            double angle = phase(seed);
            double normalX = Math.cos(angle);
            double normalZ = Math.sin(angle);
            double offset = signedUnit(seed ^ 0x4F464653455431L) * 0.30;
            double drift = signedUnit(seed ^ 0x44524946543031L) * 0.32;
            double width = 0.065
                    + 0.035 * (1.0 - descriptor.rockCompetence())
                    + 0.030 * descriptor.erosionMaturity()
                    + 0.010 * unit(seed ^ 0x57494454483131L);
            double distance =
                    normalX * x + normalZ * z - offset - (depth - 0.5) * drift;
            double normalized = distance / width;
            double local = Math.exp(-0.5 * normalized * normalized);
            support = Math.max(support, local);
        }
        return support;
    }

    private static void appendRegions(
            List<SkyIslandGeologicRegion> destination,
            SkyIslandGeologicRegionKind kind,
            boolean[] active,
            double[] membership,
            double radius,
            double spacing,
            double depthSpacing) {
        boolean[] visited = new boolean[membership.length];
        int nextRegionId = (int) destination.stream()
                .filter(region -> region.kind() == kind)
                .count();

        for (int seed = 0; seed < membership.length; seed++) {
            if (visited[seed] || !active[seed] || membership[seed] <= 0.0) {
                continue;
            }
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            List<Integer> component = new ArrayList<>();
            queue.add(seed);
            visited[seed] = true;

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                component.add(current);
                int ix = xIndex(current);
                int id = depthIndex(current);
                int iz = zIndex(current);
                addNeighbor(queue, visited, active, membership, ix - 1, id, iz);
                addNeighbor(queue, visited, active, membership, ix + 1, id, iz);
                addNeighbor(queue, visited, active, membership, ix, id - 1, iz);
                addNeighbor(queue, visited, active, membership, ix, id + 1, iz);
                addNeighbor(queue, visited, active, membership, ix, id, iz - 1);
                addNeighbor(queue, visited, active, membership, ix, id, iz + 1);
            }

            if (component.size() < MIN_REGION_CELLS) {
                continue;
            }

            List<SkyIslandGeologicRegionCell> cells = new ArrayList<>(component.size());
            component.sort(Integer::compareTo);
            for (int current : component) {
                int ix = xIndex(current);
                int id = depthIndex(current);
                int iz = zIndex(current);
                double x = -radius + ix * spacing;
                double z = -radius + iz * spacing;
                double depth = id * depthSpacing;
                cells.add(new SkyIslandGeologicRegionCell(
                        current,
                        ix,
                        id,
                        iz,
                        new SkyIslandSubsurfacePosition(x, z, depth),
                        membership[current]));
            }
            destination.add(new SkyIslandGeologicRegion(nextRegionId++, kind, cells));
        }
    }

    private static void addNeighbor(
            ArrayDeque<Integer> queue,
            boolean[] visited,
            boolean[] active,
            double[] membership,
            int ix,
            int id,
            int iz) {
        if (ix < 0 || id < 0 || iz < 0
                || ix >= GRID_SIZE || id >= DEPTH_SAMPLES || iz >= GRID_SIZE) {
            return;
        }
        int neighbor = index(ix, id, iz);
        if (visited[neighbor] || !active[neighbor] || membership[neighbor] <= 0.0) {
            return;
        }
        visited[neighbor] = true;
        queue.addLast(neighbor);
    }

    private static int index(int ix, int id, int iz) {
        return (iz * DEPTH_SAMPLES + id) * GRID_SIZE + ix;
    }

    private static int xIndex(int index) {
        return index % GRID_SIZE;
    }

    private static int depthIndex(int index) {
        return (index / GRID_SIZE) % DEPTH_SAMPLES;
    }

    private static int zIndex(int index) {
        return index / (GRID_SIZE * DEPTH_SAMPLES);
    }

    private static double phase(long seed) {
        return unit(seed) * 2.0 * Math.PI;
    }

    private static double signedUnit(long seed) {
        return unit(seed) * 2.0 - 1.0;
    }

    private static double unit(long seed) {
        long bits = mix64(seed);
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
