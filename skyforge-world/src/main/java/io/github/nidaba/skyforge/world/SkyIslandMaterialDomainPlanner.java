package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts AUTH-0031 continuous material character into connected overlapping mesoscale domains.
 *
 * <p>The planning lattice is semantic evidence/planning structure, not a backend voxel contract.
 * Host-material cells may participate in several domains simultaneously. Authored cave void and
 * unowned positions never participate.
 */
public final class SkyIslandMaterialDomainPlanner {
    public static final int GRID_SIZE = 25;
    public static final int DEPTH_SAMPLES = 13;
    public static final int MIN_DOMAIN_CELLS = 5;

    private static final long MINERAL_CARRIER_DOMAIN = 0x4D41544D494E4552L;
    private static final long MINERAL_CARRIER_STEP = 0x9E3779B97F4A7C15L;
    private static final long FABRIC_CARRIER_DOMAIN = 0x4D41544641425249L;
    private static final long FABRIC_CARRIER_STEP = 0xD1B54A32D192ED03L;

    private SkyIslandMaterialDomainPlanner() {}

    public static SkyIslandMaterialDomainPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        SkyIslandGeologyFieldSet geology =
                SkyIslandGeologyFieldSet.create(descriptor);

        double radius = descriptor.nominalRadius();
        double horizontalSpacing = 2.0 * radius / (GRID_SIZE - 1.0);
        double depthSpacing = 1.0 / (DEPTH_SAMPLES - 1.0);
        int total = GRID_SIZE * DEPTH_SAMPLES * GRID_SIZE;

        boolean[] active = new boolean[total];
        double[] altered = new double[total];
        double[] saturated = new double[total];
        double[] mineralized = new double[total];
        double[] fabric = new double[total];

        int mineralCarrierCount = mineralCarrierCount(descriptor);
        int fabricCarrierCount = fabricCarrierCount(descriptor);
        int activeHostCells = 0;

        double alteredThreshold = clamp(
                0.51
                        + 0.05 * descriptor.rockCompetence()
                        - 0.06 * descriptor.erosionMaturity(),
                0.44,
                0.55);
        double saturatedThreshold = clamp(
                0.57
                        - 0.07 * descriptor.hydrologicalPotential()
                        - 0.04 * descriptor.permeability(),
                0.48,
                0.57);
        double mineralizedThreshold = clamp(
                0.46
                        + 0.02 * descriptor.rockCompetence()
                        - 0.03 * descriptor.erosionMaturity(),
                0.42,
                0.48);
        double fabricThreshold = clamp(
                0.62
                        - 0.04 * descriptor.rockCompetence(),
                0.57,
                0.62);

        for (int iz = 0; iz < GRID_SIZE; iz++) {
            double z = -radius + iz * horizontalSpacing;
            for (int id = 0; id < DEPTH_SAMPLES; id++) {
                double depth = id * depthSpacing;
                for (int ix = 0; ix < GRID_SIZE; ix++) {
                    double x = -radius + ix * horizontalSpacing;
                    int index = index(ix, id, iz);
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandSubsurfaceMaterialSample sample = material.sample(position);
                    if (!sample.materialPresent()) {
                        continue;
                    }
                    active[index] = true;
                    activeHostCells++;

                    SkyIslandGeologySample geologic = geology.sample(position);
                    double shallowBand = 1.0 - depth;

                    double alteredMembership = clamp01(
                            0.70 * sample.alteration()
                                    + 0.18 * (1.0 - sample.matrixIntegrity())
                                    + 0.12 * shallowBand);
                    if (alteredMembership >= alteredThreshold) {
                        altered[index] = alteredMembership;
                    }

                    double saturatedMembership = clamp01(
                            0.72 * sample.saturation()
                                    + 0.16 * geologic.groundwaterPotential()
                                    + 0.12 * geologic.connectedPermeability());
                    if (saturatedMembership >= saturatedThreshold) {
                        saturated[index] = saturatedMembership;
                    }

                    double nx = x / radius;
                    double nz = z / radius;
                    double mineralCarrier = mineralCarrierSupport(
                            descriptor,
                            mineralCarrierCount,
                            nx,
                            nz,
                            depth);
                    double mineralBase = clamp01(
                            0.65 * sample.mineralizationTendency()
                                    + 0.20 * geologic.fractureIntensity()
                                    + 0.15 * sample.alteration());
                    double mineralizedMembership =
                            mineralBase * (0.58 + 0.42 * mineralCarrier);
                    if (mineralizedMembership >= mineralizedThreshold) {
                        mineralized[index] = mineralizedMembership;
                    }

                    double fabricCarrier = fabricCarrierSupport(
                            descriptor,
                            fabricCarrierCount,
                            nx,
                            nz,
                            depth);
                    double fabricMembership = clamp01(
                            0.58 * sample.matrixIntegrity()
                                    + 0.18 * (1.0 - sample.alteration())
                                    + 0.24 * fabricCarrier);
                    if (fabricMembership >= fabricThreshold) {
                        fabric[index] = fabricMembership;
                    }
                }
            }
        }

        List<SkyIslandMaterialDomain> domains = new ArrayList<>();
        appendDomains(
                domains,
                SkyIslandMaterialDomainKind.ALTERED_ZONE,
                active,
                altered,
                radius,
                horizontalSpacing,
                depthSpacing);
        appendDomains(
                domains,
                SkyIslandMaterialDomainKind.SATURATED_BODY,
                active,
                saturated,
                radius,
                horizontalSpacing,
                depthSpacing);
        appendDomains(
                domains,
                SkyIslandMaterialDomainKind.MINERALIZED_BODY,
                active,
                mineralized,
                radius,
                horizontalSpacing,
                depthSpacing);
        appendDomains(
                domains,
                SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN,
                active,
                fabric,
                radius,
                horizontalSpacing,
                depthSpacing);

        return new SkyIslandMaterialDomainPlan(
                descriptor,
                GRID_SIZE,
                DEPTH_SAMPLES,
                horizontalSpacing,
                depthSpacing,
                mineralCarrierCount,
                fabricCarrierCount,
                activeHostCells,
                domains);
    }

    public static int mineralCarrierCount(SkyIslandDescriptor descriptor) {
        double activity = clamp01(
                0.42 * descriptor.permeability()
                        + 0.33 * descriptor.hydrologicalPotential()
                        + 0.25 * descriptor.erosionMaturity());
        return 1 + (int) Math.round(2.0 * activity);
    }

    public static int fabricCarrierCount(SkyIslandDescriptor descriptor) {
        double coherence = clamp01(
                0.68 * descriptor.rockCompetence()
                        + 0.32 * (1.0 - descriptor.erosionMaturity()));
        return 1 + (int) Math.round(2.0 * coherence);
    }

    private static double mineralCarrierSupport(
            SkyIslandDescriptor descriptor,
            int carrierCount,
            double x,
            double z,
            double depth) {
        double support = 0.0;
        for (int carrier = 0; carrier < carrierCount; carrier++) {
            long seed = descriptor.authorshipSeed()
                    ^ MINERAL_CARRIER_DOMAIN
                    ^ (MINERAL_CARRIER_STEP * (carrier + 1L));
            double angle = phase(seed);
            double normalX = Math.cos(angle);
            double normalZ = Math.sin(angle);
            double offset = signedUnit(seed ^ 0x4D494E4F46465331L) * 0.32;
            double drift = signedUnit(seed ^ 0x4D494E4452494631L) * 0.28;
            double width = 0.055
                    + 0.030 * descriptor.permeability()
                    + 0.025 * descriptor.erosionMaturity()
                    + 0.015 * unit(seed ^ 0x4D494E5749445431L);

            double distance =
                    normalX * x + normalZ * z - offset - (depth - 0.5) * drift;
            double normalized = distance / width;
            double plane = Math.exp(-0.5 * normalized * normalized);

            double centerDepth =
                    0.30 + 0.40 * unit(seed ^ 0x4D494E4445505431L);
            double verticalRadius =
                    0.20 + 0.13 * unit(seed ^ 0x4D494E5652414431L);
            double vertical =
                    Math.exp(-0.5 * square((depth - centerDepth) / verticalRadius));
            support = Math.max(support, plane * vertical);
        }
        return support;
    }

    private static double fabricCarrierSupport(
            SkyIslandDescriptor descriptor,
            int carrierCount,
            double x,
            double z,
            double depth) {
        double support = 0.0;
        for (int carrier = 0; carrier < carrierCount; carrier++) {
            long seed = descriptor.authorshipSeed()
                    ^ FABRIC_CARRIER_DOMAIN
                    ^ (FABRIC_CARRIER_STEP * (carrier + 1L));
            double angle = phase(seed);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double along = x * cos + z * sin;
            double across = -x * sin + z * cos;
            double offset =
                    signedUnit(seed ^ 0x4641424F46465331L) * 0.28;
            double depthSlope =
                    signedUnit(seed ^ 0x464142534C4F5031L) * 0.20;
            double width =
                    0.12 + 0.07 * unit(seed ^ 0x4641425749445431L);

            double distance = across - offset - (depth - 0.5) * depthSlope;
            double band = Math.exp(-0.5 * square(distance / width));
            double longitudinal =
                    0.72 + 0.28 * Math.cos(
                            2.0 * Math.PI
                                    * (along * 0.48
                                            + depth * 0.18
                                            + unit(seed ^ 0x4641425048415331L)));
            support = Math.max(support, clamp01(band * longitudinal));
        }
        return support;
    }

    private static void appendDomains(
            List<SkyIslandMaterialDomain> destination,
            SkyIslandMaterialDomainKind kind,
            boolean[] active,
            double[] membership,
            double radius,
            double horizontalSpacing,
            double depthSpacing) {
        boolean[] visited = new boolean[membership.length];
        int nextDomainId = 0;

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

            if (component.size() < MIN_DOMAIN_CELLS) {
                continue;
            }

            component.sort(Integer::compareTo);
            List<SkyIslandMaterialDomainCell> cells =
                    new ArrayList<>(component.size());
            for (int current : component) {
                int ix = xIndex(current);
                int id = depthIndex(current);
                int iz = zIndex(current);
                double x = -radius + ix * horizontalSpacing;
                double z = -radius + iz * horizontalSpacing;
                double depth = id * depthSpacing;
                cells.add(new SkyIslandMaterialDomainCell(
                        current,
                        ix,
                        id,
                        iz,
                        new SkyIslandSubsurfacePosition(x, z, depth),
                        membership[current]));
            }
            destination.add(
                    new SkyIslandMaterialDomain(nextDomainId++, kind, cells));
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
        if (visited[neighbor]
                || !active[neighbor]
                || membership[neighbor] <= 0.0) {
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

    private static double square(double value) {
        return value * value;
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
