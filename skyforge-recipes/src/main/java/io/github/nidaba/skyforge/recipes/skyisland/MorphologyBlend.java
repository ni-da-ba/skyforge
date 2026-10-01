package io.github.nidaba.skyforge.recipes.skyisland;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/** Canonical recipe-layer blend between two distinct accepted built-in morphology families. */
public record MorphologyBlend(
        MorphologyFamily first,
        MorphologyFamily second,
        double secondWeight) {
    private static final MathContext CANONICAL_WEIGHT_CONTEXT =
            new MathContext(16, RoundingMode.HALF_EVEN);

    /** Validates and canonicalizes pair order by stable family identifier. */
    public MorphologyBlend {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first == second) {
            throw new IllegalArgumentException("hybrid morphology requires two distinct families");
        }
        if (!Double.isFinite(secondWeight) || secondWeight < 0.0 || secondWeight > 1.0) {
            throw new IllegalArgumentException("secondWeight must be finite and in [0, 1]");
        }
        if (first.identifier().compareTo(second.identifier()) > 0) {
            MorphologyFamily swap = first;
            first = second;
            second = swap;
            secondWeight = BigDecimal.ONE
                    .subtract(BigDecimal.valueOf(secondWeight), CANONICAL_WEIGHT_CONTEXT)
                    .doubleValue();
        }
        secondWeight = canonicalWeight(secondWeight);
    }

    /** Weight of the canonical first family. */
    public double firstWeight() {
        return canonicalWeight(1.0 - secondWeight);
    }

    /** Stable pair identifier independent of weight. */
    public String pairIdentifier() {
        return first.identifier() + "+" + second.identifier();
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
