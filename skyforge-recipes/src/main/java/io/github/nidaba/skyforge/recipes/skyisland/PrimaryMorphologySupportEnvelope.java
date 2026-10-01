package io.github.nidaba.skyforge.recipes.skyisland;

/**
 * Provider-declared conservative support envelope for one signal-free primary morphology.
 *
 * <p>All extents are positive distances from the descriptor's realized center/suspension frame.
 * The envelope is proof metadata: it must contain every positive-inside point emitted by the
 * provider primary.
 */
public record PrimaryMorphologySupportEnvelope(
        double maximumHorizontalRadius,
        double maximumUpperOffset,
        double maximumUndersideDepth) {

    public PrimaryMorphologySupportEnvelope {
        requirePositive("maximumHorizontalRadius", maximumHorizontalRadius);
        requirePositive("maximumUpperOffset", maximumUpperOffset);
        requirePositive("maximumUndersideDepth", maximumUndersideDepth);
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }
}
