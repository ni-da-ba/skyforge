package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;

/** Canonical recipe-layer blend between two distinct accepted built-in morphology families. */
public record MorphologyBlend(
        MorphologyFamily first,
        MorphologyFamily second,
        double secondWeight) {
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
            secondWeight = 1.0 - secondWeight;
        }
        if (secondWeight == 0.0) {
            secondWeight = 0.0;
        } else if (secondWeight == 1.0) {
            secondWeight = 1.0;
        }
    }

    /** Weight of the canonical first family. */
    public double firstWeight() {
        return 1.0 - secondWeight;
    }

    /** Stable pair identifier independent of weight. */
    public String pairIdentifier() {
        return first.identifier() + "+" + second.identifier();
    }
}
