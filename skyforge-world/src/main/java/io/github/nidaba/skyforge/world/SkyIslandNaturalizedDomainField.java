package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.util.Objects;

/**
 * Morphology-aware naturalized island-local ownership tendency.
 *
 * <p>AUTH-0020 replaces the diagnostic assumption of a perfectly circular island boundary with a
 * deterministic, connected, star-shaped semantic domain. The field is intentionally not yet wired
 * into the historical AUTH-0002 semantic field set; downstream migration remains separately
 * reviewable.
 */
public final class SkyIslandNaturalizedDomainField implements SkyIslandSemanticField {
    private static final long PHASE_THREE_DOMAIN = 0x444F4D41494E3331L;
    private static final long PHASE_FIVE_DOMAIN = 0x444F4D41494E3531L;
    private static final double TWO_PI = 2.0 * Math.PI;

    private final SkyIslandDescriptor descriptor;
    private final double phaseThree;
    private final double phaseFive;
    private final double irregularity;

    private SkyIslandNaturalizedDomainField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.phaseThree = phase(descriptor.authorshipSeed() ^ PHASE_THREE_DOMAIN);
        this.phaseFive = phase(descriptor.authorshipSeed() ^ PHASE_FIVE_DOMAIN);
        this.irregularity = 0.025
                + 0.045 * descriptor.erosionMaturity()
                + 0.025 * (1.0 - descriptor.rockCompetence());
    }

    /** Creates one immutable naturalized domain field for an authored island. */
    public static SkyIslandNaturalizedDomainField create(SkyIslandDescriptor descriptor) {
        return new SkyIslandNaturalizedDomainField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    /** Returns the deterministic semantic boundary radius at an island-local polar angle. */
    public double boundaryRadius(double angle) {
        double normalized = morphologyEnvelope(descriptor.morphologyFamily(), angle);
        double bay = clamp01(
                0.50
                        + 0.30 * Math.cos(3.0 * angle + phaseThree)
                        + 0.20 * Math.cos(5.0 * angle + phaseFive));
        double naturalized = normalized * (1.0 - irregularity * bay);
        return descriptor.nominalRadius() * clamp(naturalized, 0.52, 1.0);
    }

    /** Returns radial distance divided by the local naturalized boundary radius. */
    public double normalizedRadialCoordinate(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double radial = Math.hypot(position.x(), position.z());
        if (radial == 0.0) {
            return 0.0;
        }
        double angle = Math.atan2(position.z(), position.x());
        return radial / boundaryRadius(angle);
    }

    /**
     * Returns interior-to-edge influence for the naturalized domain.
     *
     * <p>The transition intentionally preserves AUTH-0002's broad 0.70-to-1.00 edge fade while
     * replacing only the underlying boundary geometry.
     */
    @Override
    public double sample(SkyIslandLocalPosition position) {
        double normalized = normalizedRadialCoordinate(position);
        return 1.0 - smoothstep(0.70, 1.0, normalized);
    }

    private static double morphologyEnvelope(SkyIslandMorphologyFamily family, double angle) {
        return switch (family) {
            case MASSIF -> ellipseRadius(0.995, 0.91, angle);
            case TABLELAND -> ellipseRadius(0.995, 0.94, angle);
            case SPINE -> ellipseRadius(0.995, 0.62, angle);
            case BASIN -> ellipseRadius(0.995, 0.95, angle);
            case LOBED -> {
                double carrier = ellipseRadius(0.985, 0.91, angle);
                double shoulder = 0.90 + 0.10 * (0.5 + 0.5 * Math.cos(5.0 * angle));
                yield carrier * shoulder;
            }
        };
    }

    private static double ellipseRadius(double semiMajor, double semiMinor, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double denominator = Math.sqrt(
                cos * cos / (semiMajor * semiMajor)
                        + sin * sin / (semiMinor * semiMinor));
        return 1.0 / denominator;
    }

    private static double phase(long seed) {
        long bits = mix64(seed);
        double unit = (bits >>> 11) * 0x1.0p-53;
        return unit * TWO_PI;
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
