package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Objects;

/**
 * AUTH-0098 producer for normalized petroleum-system geological opportunity.
 *
 * <p>The profiler adds no oil deposit objects or block-scale noise. It interprets exact accepted
 * AUTH-0033 host cells through existing geology/material fields plus one broad subordinate
 * organic-source affinity, then requires vertical source/reservoir/seal juxtaposition.
 */
public final class SkyIslandPetroleumSystemOpportunityProfiler {
    private static final long ORGANIC_SOURCE_DOMAIN = 0x4155543938504554L;

    public SkyIslandPetroleumSystemOpportunityProfile profile(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        SkyIslandMaterialFamilyPlan source = SkyIslandMaterialFamilyPlanner.plan(descriptor);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);

        int total = source.gridSize() * source.depthSamples() * source.gridSize();
        double[] sourcePotential = new double[total];
        double[] reservoirPotential = new double[total];
        double[] sealPotential = new double[total];

        for (SkyIslandMaterialFamilyCell cell : source.cells()) {
            SkyIslandGeologySample geologic = geology.sample(cell.position());
            SkyIslandSubsurfaceMaterialSample materialSample = material.sample(cell.position());
            if (!geologic.owned() || !materialSample.materialPresent()) {
                throw new IllegalStateException(
                        "AUTH-0098 exact AUTH-0033 host cell lost accepted geology/material support");
            }

            double maturity = clamp01(
                    0.58 * cell.position().depthFraction()
                            + 0.42 * descriptor.temperatureTendency());
            double sourceCompatibility = clamp01(
                    0.24 * cell.waterConditionedHost()
                            + 0.20 * materialSample.matrixIntegrity()
                            + 0.30 * organicAffinity(descriptor, cell.position())
                            + 0.26 * maturity);
            double cellSource =
                    cell.layeredFabricRichHost() * sourceCompatibility;

            double reservoirCompatibility = clamp01(
                    0.50 * materialSample.matrixIntegrity()
                            + 0.28 * cell.strongestHostFabric()
                            + 0.22 * cell.waterConditionedHost());
            double cellReservoir =
                    geologic.connectedPermeability() * reservoirCompatibility;

            double lowPermeability = 1.0 - geologic.connectedPermeability();
            double sealCompatibility = clamp01(
                    0.56 * cell.coherentMassiveHost()
                            + 0.44 * geologic.bulkCompetence());
            double cellSeal = lowPermeability * sealCompatibility;

            sourcePotential[cell.index()] = clamp01(cellSource);
            reservoirPotential[cell.index()] = clamp01(cellReservoir);
            sealPotential[cell.index()] = clamp01(cellSeal);
        }

        ArrayList<SkyIslandPetroleumSystemOpportunityCell> cells =
                new ArrayList<>(source.cells().size());
        for (SkyIslandMaterialFamilyCell cell : source.cells()) {
            double deeperSource = deeperSupport(
                    sourcePotential,
                    cell.xIndex(),
                    cell.depthIndex(),
                    cell.zIndex(),
                    source.gridSize(),
                    source.depthSamples());
            double shallowerSeal = shallowerSupport(
                    sealPotential,
                    cell.xIndex(),
                    cell.depthIndex(),
                    cell.zIndex(),
                    source.gridSize(),
                    source.depthSamples());
            double reservoir = reservoirPotential[cell.index()];
            double system = reservoir * deeperSource * shallowerSeal;

            cells.add(new SkyIslandPetroleumSystemOpportunityCell(
                    cell,
                    sourcePotential[cell.index()],
                    reservoir,
                    sealPotential[cell.index()],
                    deeperSource,
                    shallowerSeal,
                    system));
        }

        return new SkyIslandPetroleumSystemOpportunityProfile(source, cells);
    }

    private static double deeperSupport(
            double[] values,
            int x,
            int depth,
            int z,
            int gridSize,
            int depthSamples) {
        double support = 0.0;
        for (int candidateDepth = depth + 1; candidateDepth < depthSamples; candidateDepth++) {
            int distance = candidateDepth - depth;
            double weighted = values[index(x, candidateDepth, z, gridSize, depthSamples)]
                    / distance;
            support = Math.max(support, weighted);
        }
        return support;
    }

    private static double shallowerSupport(
            double[] values,
            int x,
            int depth,
            int z,
            int gridSize,
            int depthSamples) {
        double support = 0.0;
        for (int candidateDepth = depth - 1; candidateDepth >= 0; candidateDepth--) {
            int distance = depth - candidateDepth;
            double weighted = values[index(x, candidateDepth, z, gridSize, depthSamples)]
                    / distance;
            support = Math.max(support, weighted);
        }
        return support;
    }

    private static int index(
            int x,
            int depth,
            int z,
            int gridSize,
            int depthSamples) {
        return (z * depthSamples + depth) * gridSize + x;
    }

    /**
     * Broad coherent organic-source affinity in normalized island/depth coordinates.
     *
     * <p>This term is subordinate to accepted layered host support and cannot create source or
     * system opportunity by itself.
     */
    private static double organicAffinity(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfacePosition position) {
        double radius = descriptor.nominalRadius();
        double x = position.x() / radius;
        double z = position.z() / radius;
        double depth = position.depthFraction();

        long seed = descriptor.authorshipSeed() ^ ORGANIC_SOURCE_DOMAIN;
        double angle = phase(seed);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = x * cos - z * sin + depth * 0.13;
        double rz = x * sin + z * cos - depth * 0.19;
        double rd = depth + x * 0.08 - z * 0.10;

        return valueNoise3(seed, rx / 0.84, rd / 0.62, rz / 0.84);
    }

    private static double phase(long seed) {
        long bits = mix64(seed);
        return (bits >>> 11) * 0x1.0p-53 * 2.0 * Math.PI;
    }

    private static double valueNoise3(long seed, double x, double y, double z) {
        long x0 = fastFloor(x);
        long y0 = fastFloor(y);
        long z0 = fastFloor(z);
        double sx = fade(x - x0);
        double sy = fade(y - y0);
        double sz = fade(z - z0);

        double c000 = lattice(seed, x0, y0, z0);
        double c100 = lattice(seed, x0 + 1, y0, z0);
        double c010 = lattice(seed, x0, y0 + 1, z0);
        double c110 = lattice(seed, x0 + 1, y0 + 1, z0);
        double c001 = lattice(seed, x0, y0, z0 + 1);
        double c101 = lattice(seed, x0 + 1, y0, z0 + 1);
        double c011 = lattice(seed, x0, y0 + 1, z0 + 1);
        double c111 = lattice(seed, x0 + 1, y0 + 1, z0 + 1);

        double x00 = lerp(c000, c100, sx);
        double x10 = lerp(c010, c110, sx);
        double x01 = lerp(c001, c101, sx);
        double x11 = lerp(c011, c111, sx);
        return lerp(lerp(x00, x10, sy), lerp(x01, x11, sy), sz);
    }

    private static double lattice(long seed, long x, long y, long z) {
        long value = seed;
        value ^= mix64(x * 0x632BE59BD9B4E019L);
        value ^= mix64(y * 0xD1B54A32D192ED03L);
        value ^= mix64(z * 0x9E3779B97F4A7C15L);
        long bits = mix64(value);
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static long fastFloor(double value) {
        long truncated = (long) value;
        return value < truncated ? truncated - 1L : truncated;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
