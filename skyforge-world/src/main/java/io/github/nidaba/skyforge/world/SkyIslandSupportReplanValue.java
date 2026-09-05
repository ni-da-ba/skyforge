package io.github.nidaba.skyforge.world;

/** AUTH-0055 provenance-rich scalar proposal value. */
public record SkyIslandSupportReplanValue(
        double originalValue,
        double proofMinimum,
        double authorMargin,
        double proposedValue) {

    public SkyIslandSupportReplanValue {
        requireNonNegative("originalValue", originalValue);
        requireNonNegative("proofMinimum", proofMinimum);
        requireNonNegative("authorMargin", authorMargin);
        requireNonNegative("proposedValue", proposedValue);
        double required = Math.nextUp(proofMinimum + authorMargin);
        if (proposedValue < originalValue || proposedValue < required) {
            throw new IllegalArgumentException(
                    "proposedValue must preserve original value and include proof minimum plus margin");
        }
    }

    public static SkyIslandSupportReplanValue propose(
            double originalValue, double proofMinimum, double authorMargin) {
        requireNonNegative("originalValue", originalValue);
        requireNonNegative("proofMinimum", proofMinimum);
        requireNonNegative("authorMargin", authorMargin);
        double proofWithMargin = Math.nextUp(proofMinimum + authorMargin);
        return new SkyIslandSupportReplanValue(
                originalValue,
                proofMinimum,
                authorMargin,
                Math.max(originalValue, proofWithMargin));
    }

    public boolean raisedByProofOrMargin() {
        return proposedValue > originalValue;
    }

    public double proofDrivenIncreaseBeforeMargin() {
        return Math.max(0.0, proofMinimum - originalValue);
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
