package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0049 composed multi-island world material result.
 *
 * <p>Spatial ownership remains entirely authoritative from AUTH-0048. AUTH-0047 material sampling
 * is present only when that ownership result is UNIQUE.
 */
public record SkyIslandWorldAuthoredMaterialComposition(
        SkyIslandAuthoredRealizationOwnershipSelection ownership,
        SkyIslandWorldAuthoredMaterialSample sample) {

    public SkyIslandWorldAuthoredMaterialComposition {
        ownership = Objects.requireNonNull(ownership, "ownership");

        if (ownership.status() == SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE) {
            sample = Objects.requireNonNull(sample, "sample");
            SkyIslandAuthoredRealizationOwnershipCandidate owner =
                    ownership.uniqueOwner().orElseThrow();

            if (!sample.worldPosition().equals(ownership.worldPosition())) {
                throw new IllegalArgumentException(
                        "AUTH-0049 sample must retain the AUTH-0048 world position");
            }
            if (!sample.association()
                    .canonicalToken()
                    .equals(owner.association().canonicalToken())) {
                throw new IllegalArgumentException(
                        "AUTH-0049 sample must use the exact AUTH-0048 unique owner association");
            }
            if (!sample.physicalInterior() || !sample.authoredOwned()) {
                throw new IllegalArgumentException(
                        "AUTH-0049 UNIQUE ownership requires an AUTH-0047 native-owned physical sample");
            }
            if (!owner.semantic().orElseThrow().equals(sample.semantic().orElseThrow())) {
                throw new IllegalArgumentException(
                        "AUTH-0049 ownership and material sample must retain the same recovered semantic point");
            }
        } else if (sample != null) {
            throw new IllegalArgumentException(
                    "AUTH-0049 NONE/AMBIGUOUS ownership cannot carry a material sample");
        }
    }

    public Coordinate3 worldPosition() {
        return ownership.worldPosition();
    }

    public SkyIslandAuthoredRealizationOwnershipStatus status() {
        return ownership.status();
    }

    public Optional<SkyIslandWorldAuthoredMaterialSample> authoredSample() {
        return Optional.ofNullable(sample);
    }

    public boolean materialPresent() {
        return sample != null && sample.materialPresent();
    }

    public boolean authoredVoid() {
        return sample != null && sample.authoredVoid();
    }

    public Optional<SkyIslandMaterialBindingApplication> materialApplication() {
        return sample == null
                ? Optional.empty()
                : sample.materialApplication();
    }

    public Optional<SkyIslandSemanticPaletteBindingKey> applicationKey() {
        return sample == null
                ? Optional.empty()
                : sample.applicationKey();
    }
}
