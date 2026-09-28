package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Patchiness-controlled deterministic retention transform.
 *
 * <p>For upstream functional-group support {@code s}, aggregate ecological patchiness {@code p},
 * and normalized signal {@code n = (raw + 1) / 2}:
 *
 * <pre>
 * retention = (1 - p) + p * n
 * spatialSupport = s * retention
 * </pre>
 *
 * <p>The transform can attenuate existing support into coherent patches and clearings but cannot
 * create support where upstream ecology supplies zero.
 */
public enum SkyIslandPatchinessRetentionSpatialTransform
        implements SkyIslandFunctionalGroupSpatialPatchTransform {
    INSTANCE;

    @Override
    public Optional<SkyIslandFunctionalGroupSpatialPatchState> spatialize(
            SkyIslandVegetationFunctionalGroupEvaluation evaluation,
            double rawSignal) {
        Objects.requireNonNull(evaluation, "evaluation");
        if (!Double.isFinite(rawSignal) || rawSignal < -1.0 || rawSignal > 1.0) {
            throw new IllegalArgumentException("rawSignal must be finite and in [-1, 1]");
        }
        if (evaluation.structuralNicheSupport().isEmpty()
                || evaluation.composition().aggregateRealization().isEmpty()) {
            return Optional.empty();
        }

        double support = evaluation.structuralNicheSupport().orElseThrow();
        SkyIslandCommunityStructureRealization structure =
                evaluation.composition().aggregateRealization().orElseThrow();
        double patchiness = structure.patchinessPotential();
        double normalizedSignal = 0.5 * (rawSignal + 1.0);
        double retention = (1.0 - patchiness) + patchiness * normalizedSignal;
        double spatialSupport = support * retention;

        if (spatialSupport > support + 1.0e-12) {
            throw new IllegalStateException(
                    "spatial patch transform must not amplify upstream niche support");
        }

        return Optional.of(new SkyIslandFunctionalGroupSpatialPatchState(
                clampNormalized(normalizedSignal),
                clampNormalized(retention),
                clampNormalized(spatialSupport)));
    }

    private static double clampNormalized(double value) {
        if (!Double.isFinite(value) || value < -1.0e-12 || value > 1.0 + 1.0e-12) {
            throw new IllegalStateException("spatial patch value must remain normalized");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
