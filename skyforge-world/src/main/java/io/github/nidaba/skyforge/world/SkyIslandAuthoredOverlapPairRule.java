package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0050 explicit pair rule.
 *
 * <p>Unlisted pairs default to {@link SkyIslandAuthoredOverlapMode#SEPARATE}. STACKED is the only
 * mode that carries a minimum vertical-separation requirement.
 */
public record SkyIslandAuthoredOverlapPairRule(
        SkyIslandAuthoredOverlapPairKey pair,
        SkyIslandAuthoredOverlapMode mode,
        double minimumVerticalSeparation) {

    public SkyIslandAuthoredOverlapPairRule {
        pair = Objects.requireNonNull(pair, "pair");
        mode = Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(minimumVerticalSeparation)
                || minimumVerticalSeparation < 0.0) {
            throw new IllegalArgumentException(
                    "minimumVerticalSeparation must be finite and non-negative");
        }
        if (mode == SkyIslandAuthoredOverlapMode.STACKED) {
            if (!(minimumVerticalSeparation > 0.0)) {
                throw new IllegalArgumentException(
                        "STACKED requires a positive minimum vertical separation");
            }
        } else if (minimumVerticalSeparation != 0.0) {
            throw new IllegalArgumentException(
                    "only STACKED may declare a minimum vertical separation");
        }
    }

    public static SkyIslandAuthoredOverlapPairRule stacked(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second,
            double minimumVerticalSeparation) {
        return new SkyIslandAuthoredOverlapPairRule(
                SkyIslandAuthoredOverlapPairKey.of(first, second),
                SkyIslandAuthoredOverlapMode.STACKED,
                minimumVerticalSeparation);
    }

    public static SkyIslandAuthoredOverlapPairRule compose(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        return new SkyIslandAuthoredOverlapPairRule(
                SkyIslandAuthoredOverlapPairKey.of(first, second),
                SkyIslandAuthoredOverlapMode.COMPOSE,
                0.0);
    }

    public static SkyIslandAuthoredOverlapPairRule separate(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        return new SkyIslandAuthoredOverlapPairRule(
                SkyIslandAuthoredOverlapPairKey.of(first, second),
                SkyIslandAuthoredOverlapMode.SEPARATE,
                0.0);
    }
}
