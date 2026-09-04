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

        double structure = structuralNoise(
                descriptor.authorshipSeed() ^ STRUCTURE_DOMAIN,
                x,
                depth,
                z,
                0.38);
        double fractureTexture = structuralNoise(
                descriptor.authorshipSeed() ^ FRACTURE_DOMAIN,
                x,
                depth,
                z,
                0.26);
        double connectivityTexture = structuralNoise(
                descriptor.authorshipSeed() ^ CONNECTIVITY_DOMAIN,
                x,
                depth,
                z,
                0.48);
        double waterTexture = structuralNoise(
                descriptor.authorshipSeed() ^ WATER_DOMAIN,
                x,
                depth,
                z,
                0.58);

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
                0.30 * descriptor.hydrologicalPotential()
                        + 0.16 * moisture
                        + 0.24 * connectedPermeability
                        + 0.16 * depth
                        + 0.18 * waterTexture
                        - 0.06 * exposure
                        - 0.05 * edge);

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

    /**
     * Produces low-frequency geological texture without exposing the lattice axes as geological
     * structure. Two differently rotated/sheared smooth value fields are blended so continuity is
     * retained while rectangular planning cells do not become visible subsurface features.
     */
    private static double structuralNoise(
            long seed,
            double x,
            double depth,
            double z,
            double scale) {
        double angleA = phase(seed ^ 0x47524F5441544531L);
        double cosA = Math.cos(angleA);
        double sinA = Math.sin(angleA);
        double ax = x * cosA - z * sinA + depth * 0.31;
        double ay = depth + x * 0.19 - z * 0.11;
        double az = x * sinA + z * cosA - depth * 0.23;
        double broad = valueNoise3(
                seed,
                ax / scale,
                ay / (scale * 0.78),
                az / scale);

        double angleB = phase(seed ^ 0x47524F5441544532L);
        double cosB = Math.cos(angleB);
        double sinB = Math.sin(angleB);
        double bx = x * cosB - z * sinB - depth * 0.17;
        double by = depth - x * 0.13 + z * 0.21;
        double bz = x * sinB + z * cosB + depth * 0.29;
        double detail = valueNoise3(
                seed ^ 0x47454F4445544149L,
                bx / (scale * 0.56),
                by / (scale * 0.62),
                bz / (scale * 0.56));

        return clamp01(0.68 * broad + 0.32 * detail);
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
