package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/** AUTH-0055 provenance-rich scalar proposal value. */
public record SkyIslandSupportReplanValue(
        double originalValue,
        OptionalDouble proofMinimum,
        double authorMargin,
        double proposedValue) {

    public SkyIslandSupportReplanValue {
        requireNonNegative("originalValue", originalValue);
        proofMinimum = Objects.requireNonNull(proofMinimum, "proofMinimum");
        proofMinimum.ifPresent(value -> requireNonNegative("proofMinimum", value));
        requireNonNegative("authorMargin", authorMargin);
        requireNonNegative("proposedValue", proposedValue);

        double baseline =
                proofMinimum.isPresent()
                        ? Math.max(originalValue, proofMinimum.orElseThrow())
                        : originalValue;
        double minimumProposed =
                authorMargin == 0.0
                        ? baseline
                        : Math.nextUp(baseline + authorMargin);
        if (proposedValue < minimumProposed) {
            throw new IllegalArgumentException(
                    "proposedValue must preserve original/proof baseline and include author margin");
        }
    }

    public static SkyIslandSupportReplanValue propose(
            double originalValue,
            OptionalDouble proofMinimum,
            double authorMargin) {
        requireNonNegative("originalValue", originalValue);
        Objects.requireNonNull(proofMinimum, "proofMinimum");
        proofMinimum.ifPresent(value -> requireNonNegative("proofMinimum", value));
        requireNonNegative("authorMargin", authorMargin);

        double baseline =
                proofMinimum.isPresent()
                        ? Math.max(originalValue, proofMinimum.orElseThrow())
                        : originalValue;
        double proposed =
                authorMargin == 0.0
                        ? baseline
                        : Math.nextUp(baseline + authorMargin);
        return new SkyIslandSupportReplanValue(
                originalValue, proofMinimum, authorMargin, proposed);
    }

    public boolean proofAvailable() {
        return proofMinimum.isPresent();
    }

    public boolean raisedByProof() {
        return proofMinimum.isPresent() && proofMinimum.orElseThrow() > originalValue;
    }

    public boolean raisedByAuthorMargin() {
        return authorMargin > 0.0;
    }

    public boolean raisedByProofOrMargin() {
        return proposedValue > originalValue;
    }

    public double proofDrivenIncreaseBeforeMargin() {
        return proofMinimum.isPresent()
                ? Math.max(0.0, proofMinimum.orElseThrow() - originalValue)
                : Double.NaN;
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
