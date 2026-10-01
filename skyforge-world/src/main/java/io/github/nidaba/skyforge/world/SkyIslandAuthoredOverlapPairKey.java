package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Canonical unordered AUTH-0046 association pair identity used by AUTH-0050 policy. */
public record SkyIslandAuthoredOverlapPairKey(
        String firstAssociationToken,
        String secondAssociationToken) {

    public SkyIslandAuthoredOverlapPairKey {
        firstAssociationToken =
                Objects.requireNonNull(firstAssociationToken, "firstAssociationToken");
        secondAssociationToken =
                Objects.requireNonNull(secondAssociationToken, "secondAssociationToken");
        if (firstAssociationToken.equals(secondAssociationToken)) {
            throw new IllegalArgumentException(
                    "overlap pair requires two distinct associations");
        }
        if (firstAssociationToken.compareTo(secondAssociationToken) > 0) {
            String swap = firstAssociationToken;
            firstAssociationToken = secondAssociationToken;
            secondAssociationToken = swap;
        }
    }

    public static SkyIslandAuthoredOverlapPairKey of(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return new SkyIslandAuthoredOverlapPairKey(
                first.canonicalToken(), second.canonicalToken());
    }
}
