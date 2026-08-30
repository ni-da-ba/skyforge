package io.github.nidaba.skyforge.recipes.skyisland;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/** Canonical weighted blend between two distinct registered morphology-provider identities. */
public record MorphologyProviderBlend(
        MorphologyProviderId first,
        MorphologyProviderId second,
        double secondWeight) {
    private static final MathContext CANONICAL_WEIGHT_CONTEXT =
            new MathContext(16, RoundingMode.HALF_EVEN);

    /** Validates identities and canonicalizes pair order and decimal weight identity. */
    public MorphologyProviderBlend {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException("provider hybrid requires two distinct provider ids");
        }
        if (!Double.isFinite(secondWeight) || secondWeight < 0.0 || secondWeight > 1.0) {
            throw new IllegalArgumentException("secondWeight must be finite and in [0, 1]");
        }
        if (first.compareTo(second) > 0) {
            MorphologyProviderId swap = first;
            first = second;
            second = swap;
            secondWeight = BigDecimal.ONE
                    .subtract(BigDecimal.valueOf(secondWeight), CANONICAL_WEIGHT_CONTEXT)
                    .doubleValue();
        }
        secondWeight = canonicalWeight(secondWeight);
    }

    /** Canonical weight of the first provider. */
    public double firstWeight() {
        return canonicalWeight(1.0 - secondWeight);
    }

    /** Stable pair identifier independent of weight. */
    public String pairIdentifier() {
        return first + "+" + second;
    }

    private static double canonicalWeight(double value) {
        if (value == 0.0) {
            return 0.0;
        }
        if (value == 1.0) {
            return 1.0;
        }
        return BigDecimal.valueOf(value).round(CANONICAL_WEIGHT_CONTEXT).doubleValue();
    }
}
