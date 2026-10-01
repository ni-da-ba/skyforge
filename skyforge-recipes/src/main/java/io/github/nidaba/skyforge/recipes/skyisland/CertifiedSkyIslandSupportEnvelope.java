package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;

/**
 * AUTH-0051 proof-grade conservative support envelope for one fully compiled suspended volume.
 *
 * <p>The certificate is relative to the compiled descriptor's world center and suspension
 * elevation. It is intentionally distinct from backend query/reservation bounds.
 */
public record CertifiedSkyIslandSupportEnvelope(
        double maximumHorizontalRadius,
        double maximumUpperOffset,
        double maximumUndersideDepth,
        String certificateKind) {

    public CertifiedSkyIslandSupportEnvelope {
        requirePositive("maximumHorizontalRadius", maximumHorizontalRadius);
        requirePositive("maximumUpperOffset", maximumUpperOffset);
        requirePositive("maximumUndersideDepth", maximumUndersideDepth);
        certificateKind = Objects.requireNonNull(certificateKind, "certificateKind");
        if (certificateKind.isBlank() || !certificateKind.equals(certificateKind.strip())) {
            throw new IllegalArgumentException(
                    "certificateKind must be nonblank with no surrounding whitespace");
        }
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }
}
