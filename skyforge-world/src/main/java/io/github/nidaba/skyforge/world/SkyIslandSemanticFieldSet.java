package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.util.Objects;

/**
 * Deterministic island-local semantic fields derived from one authored island descriptor.
 *
 * <p>AUTH-0021 makes the AUTH-0020 morphology-aware naturalized domain the authoritative current
 * ownership geometry. A legacy factory remains available only for historical AUTH-0002 diagnostic
 * reproduction.
 */
public final class SkyIslandSemanticFieldSet {
    private static final long ELEVATION_DOMAIN = 0x454C45564154494FL;
    private static final long TEMPERATURE_DOMAIN = 0x54454D5045524154L;
    private static final long MOISTURE_DOMAIN = 0x4D4F495354555245L;
    private static final long EXPOSURE_DOMAIN = 0x4558504F53555245L;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandNaturalizedDomainField naturalizedDomain;
    private final boolean legacyCircularDomain;
    private final SkyIslandSemanticField interiority;
    private final SkyIslandSemanticField elevationTendency;
    private final SkyIslandSemanticField temperature;
    private final SkyIslandSemanticField moisture;
    private final SkyIslandSemanticField exposure;

    private SkyIslandSemanticFieldSet(
            SkyIslandDescriptor descriptor,
            boolean legacyCircularDomain) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.naturalizedDomain = SkyIslandNaturalizedDomainField.create(descriptor);
        this.legacyCircularDomain = legacyCircularDomain;
        this.interiority = this::sampleInteriority;
        this.elevationTendency = this::sampleElevationTendency;
        this.exposure = this::sampleExposure;
        this.temperature = this::sampleTemperature;
        this.moisture = this::sampleMoisture;
    }

    /**
     * Creates the current semantic field composition using the AUTH-0020 naturalized island domain.
     */
    public static SkyIslandSemanticFieldSet create(SkyIslandDescriptor descriptor) {
        return new SkyIslandSemanticFieldSet(descriptor, false);
    }

    /**
     * Creates the historical AUTH-0002 circular-domain composition for evidence reproduction only.
     */
    public static SkyIslandSemanticFieldSet createLegacyCircular(SkyIslandDescriptor descriptor) {
        return new SkyIslandSemanticFieldSet(descriptor, true);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandSemanticField interiority() {
        return interiority;
    }

    public SkyIslandSemanticField elevationTendency() {
        return elevationTendency;
    }

    public SkyIslandSemanticField temperature() {
        return temperature;
    }

    public SkyIslandSemanticField moisture() {
        return moisture;
    }

    public SkyIslandSemanticField exposure() {
        return exposure;
    }

    /** Returns the explicit AUTH-0020 domain underlying the current field composition. */
    public SkyIslandNaturalizedDomainField naturalizedDomain() {
        return naturalizedDomain;
    }

    /** Whether this field set is the historical circular diagnostic rather than current authorship. */
    public boolean legacyCircularDomain() {
        return legacyCircularDomain;
    }

    private double sampleInteriority(SkyIslandLocalPosition position) {
        if (!legacyCircularDomain) {
            return naturalizedDomain.sample(position);
        }
        double radius = descriptor.nominalRadius();
        double nx = position.x() / radius;
        double nz = position.z() / radius;
        double radial = Math.sqrt(nx * nx + nz * nz);
        return 1.0 - smoothstep(0.70, 1.0, radial);
    }

    private double sampleElevationTendency(SkyIslandLocalPosition position) {
        double radius = descriptor.nominalRadius();
        double nx = position.x() / radius;
        double nz = position.z() / radius;
        double radial = Math.sqrt(nx * nx + nz * nz);
        double inside = sampleInteriority(position);
        if (inside <= 0.0) {
            return 0.0;
        }

        double morphology = morphologyElevation(descriptor.morphologyFamily(), nx, nz, radial);
        double broadNoise = valueNoise(
                descriptor.authorshipSeed() ^ ELEVATION_DOMAIN,
                position.x() / (radius * 0.42),
                position.z() / (radius * 0.42));
        double detail = (broadNoise - 0.5) * 0.22 * descriptor.rockCompetence();
        return clamp01((morphology + detail) * inside);
    }

    private double sampleExposure(SkyIslandLocalPosition position) {
        double radius = descriptor.nominalRadius();
        double edge = 1.0 - sampleInteriority(position);
        double coherent = valueNoise(
                descriptor.authorshipSeed() ^ EXPOSURE_DOMAIN,
                position.x() / (radius * 0.65),
                position.z() / (radius * 0.65));
        double local = descriptor.exposureTendency() * 0.58 + edge * 0.30 + coherent * 0.12;
        return clamp01(local);
    }

    private double sampleTemperature(SkyIslandLocalPosition position) {
        double radius = descriptor.nominalRadius();
        double northSouth = clamp(position.z() / radius, -1.0, 1.0);
        double coherent = valueNoise(
                descriptor.authorshipSeed() ^ TEMPERATURE_DOMAIN,
                position.x() / (radius * 0.90),
                position.z() / (radius * 0.90));
        double elevationCooling = sampleElevationTendency(position) * 0.18;
        double local = descriptor.temperatureTendency()
                - northSouth * 0.12
                + (coherent - 0.5) * 0.18
                - elevationCooling;
        return clamp01(local);
    }

    private double sampleMoisture(SkyIslandLocalPosition position) {
        double radius = descriptor.nominalRadius();
        double coherent = valueNoise(
                descriptor.authorshipSeed() ^ MOISTURE_DOMAIN,
                position.x() / (radius * 0.72),
                position.z() / (radius * 0.72));
        double exposureDrying = sampleExposure(position) * 0.20;
        double basinRetention = (1.0 - sampleElevationTendency(position))
                * sampleInteriority(position)
                * descriptor.hydrologicalPotential()
                * 0.16;
        double local = descriptor.moistureTendency()
                + (coherent - 0.5) * 0.24
                - exposureDrying
                + basinRetention;
        return clamp01(local);
    }

    private static double morphologyElevation(
            SkyIslandMorphologyFamily family,
            double nx,
            double nz,
            double radial) {
        double core = clamp01(1.0 - radial);
        return switch (family) {
            case MASSIF -> Math.pow(core, 0.62);
            case TABLELAND -> smoothstep(0.02, 0.18, core);
            case SPINE -> {
                double crossAxis = Math.abs(nz);
                double spine = clamp01(1.0 - crossAxis / 0.42);
                yield clamp01(0.30 * core + 0.70 * spine * core);
            }
            case BASIN -> {
                double rim = Math.exp(-Math.pow((radial - 0.55) / 0.18, 2.0));
                yield clamp01(0.22 * core + 0.78 * rim);
            }
            case LOBED -> {
                double angle = Math.atan2(nz, nx);
                double lobes = 0.5 + 0.5 * Math.cos(5.0 * angle);
                yield clamp01(core * (0.68 + 0.32 * lobes));
            }
        };
    }

    private static double valueNoise(long seed, double x, double z) {
        long x0 = fastFloor(x);
        long z0 = fastFloor(z);
        double tx = x - x0;
        double tz = z - z0;
        double sx = fade(tx);
        double sz = fade(tz);

        double v00 = lattice(seed, x0, z0);
        double v10 = lattice(seed, x0 + 1L, z0);
        double v01 = lattice(seed, x0, z0 + 1L);
        double v11 = lattice(seed, x0 + 1L, z0 + 1L);

        double a = lerp(v00, v10, sx);
        double b = lerp(v01, v11, sx);
        return lerp(a, b, sz);
    }

    private static double lattice(long seed, long x, long z) {
        long value = seed;
        value ^= mix64(x * 0x632BE59BD9B4E019L);
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

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
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
