package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Generic exponential dispersal response over AUTH-0092/AUTH-0103 nominal radial-gap evidence.
 *
 * <p>The characteristic gap is supplied explicitly by the caller and carries the same world-horizontal
 * distance semantics as the raw nominal radial gap. This profile is a reusable response curve, not a
 * built-in organism category.
 */
public record SkyIslandExponentialDispersalProfile(double characteristicNominalGap)
        implements SkyIslandCommunityDispersalProfile {

    public SkyIslandExponentialDispersalProfile {
        if (!Double.isFinite(characteristicNominalGap) || characteristicNominalGap <= 0.0) {
            throw new IllegalArgumentException(
                    "characteristicNominalGap must be finite and positive");
        }
    }

    @Override
    public OptionalDouble accessibility(SkyIslandCommunityAssemblyEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        OptionalDouble gap = evidence.nearestNominalRadialGap();
        if (gap.isEmpty()) {
            return OptionalDouble.empty();
        }
        double value = Math.exp(-gap.orElseThrow() / characteristicNominalGap);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException("dispersal accessibility must remain finite and normalized");
        }
        return OptionalDouble.of(value);
    }
}
