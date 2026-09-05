package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0050 deterministic proof/admission result for one association pair. */
public record SkyIslandAuthoredOverlapPairAudit(
        SkyIslandAuthoredRealizationAssociation first,
        SkyIslandAuthoredRealizationAssociation second,
        SkyIslandAuthoredOverlapPairRule rule,
        SkyIslandAuthoredOverlapPairStatus status,
        boolean conservativeBoundsIntersect,
        boolean nativeSupportDiscsDisjoint,
        double conservativeVerticalGap,
        Coordinate3 overlapWitness) {

    public SkyIslandAuthoredOverlapPairAudit {
        first = Objects.requireNonNull(first, "first");
        second = Objects.requireNonNull(second, "second");
        rule = Objects.requireNonNull(rule, "rule");
        status = Objects.requireNonNull(status, "status");

        SkyIslandAuthoredOverlapPairKey expected =
                SkyIslandAuthoredOverlapPairKey.of(first, second);
        if (!rule.pair().equals(expected)) {
            throw new IllegalArgumentException(
                    "AUTH-0050 rule pair does not match audited associations");
        }
        if (!Double.isFinite(conservativeVerticalGap)) {
            throw new IllegalArgumentException(
                    "conservativeVerticalGap must be finite");
        }
        if (status == SkyIslandAuthoredOverlapPairStatus.REJECTED_WITNESSED_OVERLAP
                && overlapWitness == null) {
            throw new IllegalArgumentException(
                    "witnessed-overlap rejection requires an exact witness");
        }
    }

    public boolean admitted() {
        return switch (status) {
            case CERTIFIED_SEPARATE,
                    CERTIFIED_STACKED,
                    ACCEPTED_EXPLICIT_COMPOSITION -> true;
            case REJECTED_WITNESSED_OVERLAP,
                    REJECTED_UNCERTIFIED_SEPARATION,
                    REJECTED_STACK_REQUIREMENT -> false;
        };
    }

    public Optional<Coordinate3> witness() {
        return Optional.ofNullable(overlapWitness);
    }

    public SkyIslandAuthoredOverlapPairKey pair() {
        return rule.pair();
    }
}
