package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0049 backend-neutral composition of AUTH-0048 spatial ownership and AUTH-0047 material
 * sampling.
 *
 * <p>Ownership is always resolved first. Material decisions are consulted only after AUTH-0048
 * returns exactly one native authored owner. Material availability therefore cannot influence
 * spatial ownership.
 */
public final class SkyIslandWorldAuthoredMaterialComposer {
    private final SkyIslandAuthoredRealizationOwnershipResolver ownershipResolver;
    private final Map<String, SkyIslandWorldAuthoredMaterialSampler> samplersByAssociationToken;

    public SkyIslandWorldAuthoredMaterialComposer(
            SkyIslandAuthoredRealizationCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        this.ownershipResolver =
                new SkyIslandAuthoredRealizationOwnershipResolver(catalog);

        Map<String, SkyIslandWorldAuthoredMaterialSampler> samplers = new HashMap<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                catalog.associations()) {
            String token = association.canonicalToken();
            SkyIslandWorldAuthoredMaterialSampler previous =
                    samplers.put(
                            token,
                            new SkyIslandWorldAuthoredMaterialSampler(association));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "AUTH-0049 catalog contains duplicate association token");
            }
        }
        this.samplersByAssociationToken = Map.copyOf(samplers);
    }

    public SkyIslandAuthoredRealizationCatalog catalog() {
        return ownershipResolver.catalog();
    }

    public SkyIslandWorldAuthoredMaterialComposition compose(
            Coordinate3 worldPosition,
            SkyIslandMaterialResolutionDecisionProvider decisionProvider) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(decisionProvider, "decisionProvider");

        SkyIslandAuthoredRealizationOwnershipSelection ownership =
                ownershipResolver.resolve(worldPosition);
        if (ownership.status()
                != SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE) {
            return new SkyIslandWorldAuthoredMaterialComposition(
                    ownership, null);
        }

        SkyIslandAuthoredRealizationAssociation association =
                ownership.uniqueOwner().orElseThrow().association();
        SkyIslandWorldAuthoredMaterialSampler sampler =
                samplersByAssociationToken.get(association.canonicalToken());
        if (sampler == null) {
            throw new IllegalStateException(
                    "AUTH-0048 unique owner is absent from AUTH-0049 sampler catalog");
        }

        SkyIslandWorldAuthoredMaterialSample sample =
                sampler.sample(worldPosition, decisionProvider);
        return new SkyIslandWorldAuthoredMaterialComposition(
                ownership, sample);
    }
}
