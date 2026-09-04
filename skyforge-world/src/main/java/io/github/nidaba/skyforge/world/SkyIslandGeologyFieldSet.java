package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Deterministic backend-neutral geological fields for one authored island interior.
 *
 * <p>AUTH-0022 promotes the existing descriptor-scale {@code rockCompetence} and
 * {@code permeability} priors into coherent subsurface structure. The layer intentionally authors
 * continuous geological tendencies rather than block materials, named rock taxonomies, or cave
 * geometry.
 */
public final class SkyIslandGeologyFieldSet {
    private static final long STRUCTURE_DOMAIN = 0x47454F5354525543L;
    private static final long FRACTURE_DOMAIN = 0x47454F4652414354L;
    private static final long CONNECTIVITY_DOMAIN = 0x47454F434F4E4E45L;
    private static final long WATER_DOMAIN = 0x47454F5741544552L;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticFieldSet surfaceFields;

    private SkyIslandGeologyFieldSet(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.surfaceFields = SkyIslandSemanticFieldSet.create(descriptor);
    }

    public static SkyIslandGeologyFieldSet create(SkyIslandDescriptor descriptor) {
        return new SkyIslandGeologyFieldSet(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Samples current geological meaning at one island-local subsurface position.
     *
     * <p>The fields are coherent in x/z/depth space. Edge weathering is strongest near the surface,
     * groundwater generally strengthens with depth, and void suitability is derived from fractures,
     * connected permeability, groundwater, and a stable-rock competence band rather than from an
     * independent random cave mask.
     */
    public SkyIslandGeologySample sample(SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandLocalPosition surface = position.surfacePosition();
        double interiority = surfaceFields.interiority().sample(surface);
        if (interiority <= 0.0) {
            return SkyIslandGeologySample.outside();
        }

        double radius = descriptor.nominalRadius();
        double x = surface.x() / radius;
        double z = surface.z() / radius;
        double depth = position.depthFraction();

        double elevation = surfaceFields.elevationTendency().sample(surface);
        double exposure = surfaceFields.exposure().sample(surface);
        double moisture = surfaceFields.moisture().sample(surface);
        double edge = 1.0 - interiority;

        double structure = valueNoise3(
                descriptor.authorshipSeed() ^ STRUCTURE_DOMAIN,
                x / 0.34,
                depth / 0.22,
                z / 0.34);
        double fractureTexture = valueNoise3(
                descriptor.authorshipSeed() ^ FRACTURE_DOMAIN,
                x / 0.22,
                depth / 0.16,
                z / 0.22);
        double connectivityTexture = valueNoise3(
                descriptor.authorshipSeed() ^ CONNECTIVITY_DOMAIN,
                x / 0.46,
                depth / 0.28,
                z / 0.46);
        double waterTexture = valueNoise3(
                descriptor.authorshipSeed() ^ WATER_DOMAIN,
                x / 0.55,
                depth / 0.34,
                z / 0.55);

        double shallowWeathering = (1.0 - depth) * (0.55 * edge + 0.45 * exposure);
        double structuralStress = clamp01(
                0.34 * elevation
                        + 0.28 * descriptor.erosionMaturity()
                        + 0.22 * shallowWeathering
                        + 0.16 * structure);

        double fractureIntensity = clamp01(
                0.20 * (1.0 - descriptor.rockCompetence())
                        + 0.27 * structuralStress
                        + 0.38 * fractureTexture
                        + 0.15 * shallowWeathering);

        double bulkCompetence = clamp01(
                0.68 * descriptor.rockCompetence()
                        + 0.18 * depth
                        + 0.14 * structure
                        - 0.20 * fractureIntensity);

        double connectedPermeability = clamp01(
                0.52 * descriptor.permeability()
                        + 0.30 * fractureIntensity
                        + 0.18 * connectivityTexture);

        double groundwaterPotential = clamp01(
                0.36 * descriptor.hydrologicalPotential()
                        + 0.20 * moisture
                        + 0.20 * connectedPermeability
                        + 0.18 * depth
                        + 0.10 * waterTexture
                        - 0.08 * exposure
                        - 0.06 * edge);

        double competenceBand =
                clamp01(1.0 - Math.abs(bulkCompetence - 0.62) / 0.62);
        double depthBand =
                clamp01(1.0 - Math.abs(depth - 0.52) / 0.52);
        double voidFormationPotential = clamp01(
                0.29 * fractureIntensity
                        + 0.22 * connectedPermeability
                        + 0.21 * groundwaterPotential
                        + 0.18 * competenceBand
                        + 0.10 * depthBand);

        return new SkyIslandGeologySample(
                true,
                bulkCompetence,
                fractureIntensity,
                connectedPermeability,
                groundwaterPotential,
                voidFormationPotential);
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
        double y0v = lerp(x00, x10, sy);
        double y1v = lerp(x01, x11, sy);
        return lerp(y0v, y1v, sz);
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

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
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
