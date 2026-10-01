package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0088 backend-neutral surface-ecology sample from one exact published authored realization.
 *
 * <p>Surface ecology exists only where the associated compiled realization has a physical vertical
 * column and the current native-authored island domain owns the same recovered local position.
 * Backend biome identity and vertical presentation envelopes remain downstream concerns.
 */
public record SkyIslandPublishedSurfaceEcologySample(
        SkyIslandAuthoredRealizationAssociation association,
        Coordinate2 worldPosition,
        SkyIslandLocalPosition localPosition,
        boolean physicalColumnPresent,
        double authoredInteriority,
        SkyIslandEcologySample ecology) {

    public SkyIslandPublishedSurfaceEcologySample {
        association = Objects.requireNonNull(association, "association");
        worldPosition = Objects.requireNonNull(worldPosition, "worldPosition");
        localPosition = Objects.requireNonNull(localPosition, "localPosition");
        if (!Double.isFinite(authoredInteriority)
                || authoredInteriority < 0.0
                || authoredInteriority > 1.0) {
            throw new IllegalArgumentException("authoredInteriority must be finite and in [0, 1]");
        }

        var realized = association.realizedVolume().compiledVolume().descriptor();
        double expectedLocalX = worldPosition.x() - realized.centerX();
        double expectedLocalZ = worldPosition.z() - realized.centerZ();
        if (Double.doubleToLongBits(localPosition.x()) != Double.doubleToLongBits(expectedLocalX)
                || Double.doubleToLongBits(localPosition.z()) != Double.doubleToLongBits(expectedLocalZ)) {
            throw new IllegalArgumentException(
                    "surface-ecology sample must retain the exact recovered publication-local position");
        }

        boolean expectedEcology = physicalColumnPresent && authoredInteriority > 0.0;
        if ((ecology != null) != expectedEcology) {
            throw new IllegalArgumentException(
                    "surface ecology must exist exactly where physical support and authored ownership coincide");
        }
        if (ecology != null) {
            SkyIslandEcologySample expected = SkyIslandEcologyField
                    .create(association.authoredDescriptor())
                    .sample(localPosition);
            if (!ecology.equals(expected)) {
                throw new IllegalArgumentException(
                        "surface ecology must equal the exact AUTH-0003 sample at the recovered local position");
            }
        }
    }

    public SkyIslandWorldVolumeId volumeId() {
        return association.realizedVolumeId();
    }

    public boolean authoredSurfacePresent() {
        return ecology != null;
    }

    public Optional<SkyIslandEcologySample> ecologySample() {
        return Optional.ofNullable(ecology);
    }
}
