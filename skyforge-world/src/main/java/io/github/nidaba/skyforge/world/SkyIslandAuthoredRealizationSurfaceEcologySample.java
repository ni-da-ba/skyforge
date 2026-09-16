package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral surface-ecology sample from one explicit AUTH-0046 authored-realization
 * association.
 *
 * <p>This is the publication-independent sibling of AUTH-0088's published sample. It preserves the
 * same world/local transform, physical-support gate, authored-domain gate, and exact AUTH-0003
 * ecology value without claiming AUTH-0058/AUTH-0087 publication provenance.
 */
public record SkyIslandAuthoredRealizationSurfaceEcologySample(
        SkyIslandAuthoredRealizationAssociation association,
        Coordinate2 worldPosition,
        SkyIslandLocalPosition localPosition,
        boolean physicalColumnPresent,
        double authoredInteriority,
        SkyIslandEcologySample ecology) {

    public SkyIslandAuthoredRealizationSurfaceEcologySample {
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
                    "surface-ecology sample must retain the exact recovered realization-local position");
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
