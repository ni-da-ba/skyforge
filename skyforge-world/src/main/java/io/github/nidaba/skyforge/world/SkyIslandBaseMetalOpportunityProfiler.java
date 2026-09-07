package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Objects;

/**
 * AUTH-0093 producer for normalized Iron/Copper/Zinc geological opportunity.
 *
 * <p>Every value remains subordinate to accepted AUTH-0033 mineral-bearing structural support.
 * Distinct broad elemental affinity fields provide regional differentiation only after that common
 * geological support exists.
 */
public final class SkyIslandBaseMetalOpportunityProfiler {
    private static final long IRON_DOMAIN = 0x415554393349524FL;
    private static final long COPPER_DOMAIN = 0x4155543933435550L;
    private static final long ZINC_DOMAIN = 0x41555439335A494EL;

    public SkyIslandBaseMetalOpportunityProfile profile(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandMaterialFamilyPlan source = SkyIslandMaterialFamilyPlanner.plan(descriptor);
        ArrayList<SkyIslandBaseMetalOpportunityCell> cells =
                new ArrayList<>(source.cells().size());

        for (SkyIslandMaterialFamilyCell cell : source.cells()) {
            double mineralSupport = cell.mineralBearingStructuralHost();
            if (mineralSupport == 0.0) {
                cells.add(new SkyIslandBaseMetalOpportunityCell(cell, 0.0, 0.0, 0.0));
                continue;
            }

            double ironHost = clamp01(
                    0.52 * cell.coherentMassiveHost()
                            + 0.24 * cell.layeredFabricRichHost()
                            + 0.14 * (1.0 - cell.stronglyAlteredHost())
                            + 0.10 * (1.0 - cell.waterConditionedHost()));
            double copperHost = clamp01(
                    0.48 * cell.stronglyAlteredHost()
                            + 0.30 * cell.waterConditionedHost()
                            + 0.14 * cell.layeredFabricRichHost()
                            + 0.08 * cell.coherentMassiveHost());
            double zincHost = clamp01(
                    0.38 * cell.stronglyAlteredHost()
                            + 0.30 * cell.waterConditionedHost()
                            + 0.24 * cell.layeredFabricRichHost()
                            + 0.08 * cell.coherentMassiveHost());

            double iron = opportunity(
                    mineralSupport,
                    ironHost,
                    affinity(descriptor, cell.position(), IRON_DOMAIN));
            double copper = opportunity(
                    mineralSupport,
                    copperHost,
                    affinity(descriptor, cell.position(), COPPER_DOMAIN));
            double zinc = opportunity(
                    mineralSupport,
                    zincHost,
                    affinity(descriptor, cell.position(), ZINC_DOMAIN));

            cells.add(new SkyIslandBaseMetalOpportunityCell(cell, iron, copper, zinc));
        }

        return new SkyIslandBaseMetalOpportunityProfile(source, cells);
    }

    private static double opportunity(
            double mineralSupport,
            double hostCompatibility,
            double elementalAffinity) {
        return mineralSupport
                * clamp01(0.68 * hostCompatibility + 0.32 * elementalAffinity);
    }

    /**
     * Broad coherent element-specific affinity in normalized island/depth coordinates.
     *
     * <p>This term cannot create opportunity without accepted mineral-bearing structural support.
     */
    private static double affinity(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfacePosition position,
            long domain) {
        double radius = descriptor.nominalRadius();
        double x = position.x() / radius;
        double z = position.z() / radius;
        double depth = position.depthFraction();

        long seed = descriptor.authorshipSeed() ^ domain;
        double angle = phase(seed);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = x * cos - z * sin + depth * 0.17;
        double rz = x * sin + z * cos - depth * 0.11;
        double rd = depth + x * 0.09 - z * 0.07;

        return valueNoise3(seed, rx / 0.68, rd / 0.52, rz / 0.68);
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
