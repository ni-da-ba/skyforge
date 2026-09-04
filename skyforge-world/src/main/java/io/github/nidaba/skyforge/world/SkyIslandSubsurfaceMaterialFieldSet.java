package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * AUTH-0031 continuous backend-neutral material character derived from accepted geological and cave
 * authorship.
 *
 * <p>This layer intentionally stops before named rock taxonomies, mineral species, block palettes,
 * or backend realization. It answers how the existing host material tends to behave and appear.
 */
public final class SkyIslandSubsurfaceMaterialFieldSet {
    private static final long CHEMISTRY_DOMAIN = 0x4D41544348454D31L;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticFieldSet surfaceFields;
    private final SkyIslandGeologyFieldSet geology;
    private final SkyIslandExteriorConnectedCaveVolumeField caveField;

    private SkyIslandSubsurfaceMaterialFieldSet(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.surfaceFields = SkyIslandSemanticFieldSet.create(descriptor);
        this.geology = SkyIslandGeologyFieldSet.create(descriptor);
        this.caveField = SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
    }

    public static SkyIslandSubsurfaceMaterialFieldSet create(SkyIslandDescriptor descriptor) {
        return new SkyIslandSubsurfaceMaterialFieldSet(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandExteriorConnectedCaveVolumeField caveField() {
        return caveField;
    }

    public SkyIslandSubsurfaceMaterialSample sample(SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandGeologySample geologySample = geology.sample(position);
        if (!geologySample.owned()) {
            return SkyIslandSubsurfaceMaterialSample.outside();
        }

        SkyIslandExteriorConnectedCaveVolumeSample caveSample = caveField.sample(position);
        if (caveSample.inside()) {
            return SkyIslandSubsurfaceMaterialSample.authoredVoid();
        }

        SkyIslandLocalPosition surface = position.surfacePosition();
        double interiority = surfaceFields.interiority().sample(surface);
        double exposure = surfaceFields.exposure().sample(surface);
        double depth = position.depthFraction();
        double edge = 1.0 - interiority;

        double chemistry = chemicalAffinity(position);
        double shallowWeathering =
                (1.0 - depth) * (0.55 * exposure + 0.45 * edge);

        double alteration = clamp01(
                0.28 * descriptor.erosionMaturity()
                        + 0.24 * shallowWeathering
                        + 0.18 * geologySample.fractureIntensity()
                        + 0.18 * geologySample.groundwaterPotential()
                        + 0.12 * chemistry);

        double matrixIntegrity = clamp01(
                0.72 * geologySample.bulkCompetence()
                        + 0.18 * (1.0 - geologySample.fractureIntensity())
                        + 0.10 * (1.0 - alteration));

        double saturation = clamp01(
                0.56 * geologySample.groundwaterPotential()
                        + 0.22 * geologySample.connectedPermeability()
                        + 0.12 * depth
                        + 0.10 * (1.0 - exposure));

        double fluidRockInteraction = Math.sqrt(
                geologySample.fractureIntensity()
                        * geologySample.groundwaterPotential());
        double hostBand = clamp01(
                1.0 - Math.abs(geologySample.bulkCompetence() - 0.68) / 0.68);
        double depthBand = clamp01(
                1.0 - Math.abs(depth - 0.56) / 0.56);
        double mineralization = clamp01(
                0.40 * fluidRockInteraction
                        + 0.20 * hostBand
                        + 0.14 * depthBand
                        + 0.14 * chemistry
                        + 0.12 * alteration);

        double wallProximity = caveSample.signedClearance() >= 0.0
                ? 0.0
                : clamp01(1.0 - (-caveSample.signedClearance()) / 0.60);
        double caveWallAlteration = clamp01(
                wallProximity
                        * (0.52 * alteration
                                + 0.28 * saturation
                                + 0.20 * geologySample.fractureIntensity()));

        return new SkyIslandSubsurfaceMaterialSample(
                true,
                true,
                matrixIntegrity,
                alteration,
                saturation,
                mineralization,
                caveWallAlteration);
    }

    /**
     * Broad coherent subordinate chemical affinity.
     *
     * <p>AUTH-0031 does not invent ore noise. This term only modulates material realization after
     * fracture, groundwater, host competence, depth, and alteration establish geological support.
     */
    private double chemicalAffinity(SkyIslandSubsurfacePosition position) {
        double radius = descriptor.nominalRadius();
        double x = position.x() / radius;
        double z = position.z() / radius;
        double depth = position.depthFraction();

        double angle = phase(descriptor.authorshipSeed() ^ CHEMISTRY_DOMAIN);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = x * cos - z * sin + depth * 0.21;
        double rz = x * sin + z * cos - depth * 0.17;
        double rd = depth + x * 0.13 - z * 0.09;

        return valueNoise3(
                descriptor.authorshipSeed() ^ CHEMISTRY_DOMAIN,
                rx / 0.52,
                rd / 0.38,
                rz / 0.52);
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
