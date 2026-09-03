package io.github.nidaba.skyforge.model.skyisland;

import java.util.Objects;

/**
 * Placement-free semantic description of one authored Skyforge island.
 *
 * <p>The descriptor records stable common causes for downstream geography. It deliberately contains
 * no backend coordinates, graph nodes, noise algorithms, biome registry identifiers, blocks, or
 * mutable runtime state. Geological character is represented initially by continuous physical
 * tendencies rather than a premature rock-type taxonomy.
 */
public record SkyIslandDescriptor(
        int schemaVersion,
        SkyIslandIdentity identity,
        long authorshipSeed,
        SkyIslandMorphologyFamily morphologyFamily,
        double nominalRadius,
        double reliefBudget,
        double rockCompetence,
        double permeability,
        double temperatureTendency,
        double moistureTendency,
        double exposureTendency,
        double erosionMaturity,
        double hydrologicalPotential,
        double ecologicalPotential) {
    /** The only authored-island descriptor schema supported by AUTH-0001. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates one semantic island descriptor. */
    public SkyIslandDescriptor {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported authored sky-island descriptor schema: " + schemaVersion);
        }
        identity = Objects.requireNonNull(identity, "identity");
        morphologyFamily = Objects.requireNonNull(morphologyFamily, "morphologyFamily");
        requirePositive("nominalRadius", nominalRadius);
        requirePositive("reliefBudget", reliefBudget);
        requireNormalized("rockCompetence", rockCompetence);
        requireNormalized("permeability", permeability);
        requireNormalized("temperatureTendency", temperatureTendency);
        requireNormalized("moistureTendency", moistureTendency);
        requireNormalized("exposureTendency", exposureTendency);
        requireNormalized("erosionMaturity", erosionMaturity);
        requireNormalized("hydrologicalPotential", hydrologicalPotential);
        requireNormalized("ecologicalPotential", ecologicalPotential);
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and greater than zero");
        }
    }

    private static void requireNormalized(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be finite and in [0, 1]");
        }
    }
}
