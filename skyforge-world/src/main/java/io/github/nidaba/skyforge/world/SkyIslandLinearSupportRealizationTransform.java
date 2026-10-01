package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit linear structural-realization transform.
 *
 * <p>Each supplied structural capacity is multiplied by resolved #1206 assembly support. Missing
 * assembly support remains unresolved rather than being interpreted as zero or full structure.
 */
public enum SkyIslandLinearSupportRealizationTransform
        implements SkyIslandCommunityRealizationTransform {
    INSTANCE;

    @Override
    public Optional<SkyIslandCommunityStructureRealization> realize(
            SkyIslandCommunityAssemblyEvaluation assemblyEvaluation,
            SkyIslandCommunityStructureCapacity capacity) {
        Objects.requireNonNull(assemblyEvaluation, "assemblyEvaluation");
        Objects.requireNonNull(capacity, "capacity");

        if (assemblyEvaluation.assemblySupport().isEmpty()) {
            return Optional.empty();
        }

        double support = requireNormalized(
                "assemblySupport",
                assemblyEvaluation.assemblySupport().orElseThrow());

        return Optional.of(new SkyIslandCommunityStructureRealization(
                capacity.vegetationDensityCapacity() * support,
                capacity.canopyCoverCapacity() * support,
                capacity.canopyHeightPotential() * support,
                capacity.understoryDensityCapacity() * support,
                capacity.groundCoverCapacity() * support,
                capacity.biomassPotential() * support,
                capacity.patchinessPotential() * support,
                capacity.organicSurfaceAccumulationPotential() * support,
                capacity.deadwoodPotential() * support));
    }

    private static double requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
        return value;
    }
}
