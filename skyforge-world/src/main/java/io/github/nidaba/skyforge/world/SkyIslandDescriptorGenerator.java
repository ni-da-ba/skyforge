package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.util.Objects;

/**
 * Pure deterministic authorship policy for the first placement-free Skyforge island descriptor.
 *
 * <p>This generator intentionally authors only stable island-scale semantic priors. It does not
 * construct terrain fields, choose backend biomes, or assign world coordinates. Hierarchical keys
 * are mixed in Province -> Cluster -> Island order so later parent-level semantic descriptors can
 * be introduced without redefining stable island identity.
 */
public final class SkyIslandDescriptorGenerator {
    /** Lower spatial budget emitted by the initial authorship policy, in Skyforge world units. */
    public static final double MIN_NOMINAL_RADIUS = 96.0;

    /** Upper spatial budget emitted by the initial authorship policy, in Skyforge world units. */
    public static final double MAX_NOMINAL_RADIUS = 640.0;

    /** Lower vertical relief budget emitted by the initial authorship policy. */
    public static final double MIN_RELIEF_BUDGET = 24.0;

    /** Upper vertical relief budget emitted by the initial authorship policy. */
    public static final double MAX_RELIEF_BUDGET = 192.0;

    private static final long WORLD_DOMAIN = 0x534B59464F524745L;
    private static final long PROVINCE_DOMAIN = 0x50524F56494E4345L;
    private static final long CLUSTER_DOMAIN = 0x434C555354455221L;
    private static final long ISLAND_DOMAIN = 0x49534C414E442121L;
    private static final long STREAM_GAMMA = 0x9E3779B97F4A7C15L;

    private SkyIslandDescriptorGenerator() {}

    /** Derives one immutable descriptor solely from stable hierarchical island identity. */
    public static SkyIslandDescriptor derive(SkyIslandIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        long authorshipSeed = authorshipSeed(identity);

        SkyIslandMorphologyFamily morphology = morphology(unitSample(authorshipSeed, 0));
        double scaleSample = unitSample(authorshipSeed, 1);
        double scaleControl = 0.35 * scaleSample + 0.65 * scaleSample * scaleSample;
        double nominalRadius = lerp(MIN_NOMINAL_RADIUS, MAX_NOMINAL_RADIUS, scaleControl);

        double reliefSample = unitSample(authorshipSeed, 2);
        double reliefControl = clamp01(0.70 * reliefSample + 0.30 * morphologyReliefBias(morphology));
        double reliefBudget = lerp(MIN_RELIEF_BUDGET, MAX_RELIEF_BUDGET, reliefControl);

        double rockCompetence = unitSample(authorshipSeed, 3);
        double permeability = unitSample(authorshipSeed, 4);
        double temperatureTendency = unitSample(authorshipSeed, 5);
        double moistureTendency = unitSample(authorshipSeed, 6);
        double exposureTendency = unitSample(authorshipSeed, 7);
        double erosionMaturity = unitSample(authorshipSeed, 8);

        double hydrologicalPotential = clamp01(
                0.52 * moistureTendency
                        + 0.23 * (1.0 - permeability)
                        + 0.15 * reliefControl
                        + 0.10 * (1.0 - exposureTendency));

        double temperatureSuitability = clamp01(
                1.0 - Math.abs(temperatureTendency - 0.55) / 0.55);
        double ecologicalPotential = clamp01(
                0.46 * moistureTendency
                        + 0.32 * temperatureSuitability
                        + 0.14 * (1.0 - exposureTendency)
                        + 0.08 * (1.0 - erosionMaturity));

        return new SkyIslandDescriptor(
                SkyIslandDescriptor.SCHEMA_VERSION,
                identity,
                authorshipSeed,
                morphology,
                nominalRadius,
                reliefBudget,
                rockCompetence,
                permeability,
                temperatureTendency,
                moistureTendency,
                exposureTendency,
                erosionMaturity,
                hydrologicalPotential,
                ecologicalPotential);
    }

    private static long authorshipSeed(SkyIslandIdentity identity) {
        long state = mix64(identity.worldSeed() ^ WORLD_DOMAIN);
        state = mix64(state ^ mix64(identity.provinceKey() ^ PROVINCE_DOMAIN));
        state = mix64(state ^ mix64(identity.clusterKey() ^ CLUSTER_DOMAIN));
        return mix64(state ^ mix64(identity.islandKey() ^ ISLAND_DOMAIN));
    }

    private static double unitSample(long seed, int stream) {
        long bits = mix64(seed + STREAM_GAMMA * (stream + 1L));
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static SkyIslandMorphologyFamily morphology(double selector) {
        if (selector < 0.34) {
            return SkyIslandMorphologyFamily.MASSIF;
        }
        if (selector < 0.64) {
            return SkyIslandMorphologyFamily.TABLELAND;
        }
        if (selector < 0.79) {
            return SkyIslandMorphologyFamily.SPINE;
        }
        if (selector < 0.90) {
            return SkyIslandMorphologyFamily.LOBED;
        }
        return SkyIslandMorphologyFamily.BASIN;
    }

    private static double morphologyReliefBias(SkyIslandMorphologyFamily morphology) {
        return switch (morphology) {
            case MASSIF -> 0.85;
            case TABLELAND -> 0.25;
            case SPINE -> 0.65;
            case BASIN -> 0.40;
            case LOBED -> 0.50;
        };
    }

    private static double lerp(double minimum, double maximum, double fraction) {
        return minimum + (maximum - minimum) * fraction;
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
