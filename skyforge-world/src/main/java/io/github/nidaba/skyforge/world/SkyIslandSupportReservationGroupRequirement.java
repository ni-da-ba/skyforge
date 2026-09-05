package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0054 reservation requirements for one exact deterministic group plan.
 *
 * <p>{@code exactPlanRequiredGroupRadius} is valid for the member centers in the supplied plan.
 * Applying a larger member reservation requires a fresh group/archipelago plan and then a fresh
 * synthesis; this value is not a promise about positions produced by that future re-plan.
 */
public record SkyIslandSupportReservationGroupRequirement(
        int groupOrdinal,
        String groupIdentifier,
        double currentMemberHorizontalRadius,
        double currentReservedGroupRadius,
        OptionalDouble requiredMemberHorizontalRadius,
        OptionalDouble exactPlanRequiredGroupRadius,
        boolean allMembersCertified) {

    public SkyIslandSupportReservationGroupRequirement {
        if (groupOrdinal < 0) {
            throw new IllegalArgumentException("groupOrdinal must be non-negative");
        }
        groupIdentifier = Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        requirePositive("currentMemberHorizontalRadius", currentMemberHorizontalRadius);
        requirePositive("currentReservedGroupRadius", currentReservedGroupRadius);
        requiredMemberHorizontalRadius =
                Objects.requireNonNull(
                        requiredMemberHorizontalRadius, "requiredMemberHorizontalRadius");
        exactPlanRequiredGroupRadius =
                Objects.requireNonNull(exactPlanRequiredGroupRadius, "exactPlanRequiredGroupRadius");
        if (requiredMemberHorizontalRadius.isPresent() != allMembersCertified
                || exactPlanRequiredGroupRadius.isPresent() != allMembersCertified) {
            throw new IllegalArgumentException(
                    "group requirements are present exactly when all members are certified");
        }
        requiredMemberHorizontalRadius.ifPresent(
                value -> requirePositive("requiredMemberHorizontalRadius", value));
        exactPlanRequiredGroupRadius.ifPresent(
                value -> requirePositive("exactPlanRequiredGroupRadius", value));
    }

    public boolean currentMemberHorizontalReservationAdequate() {
        return requiredMemberHorizontalRadius.isPresent()
                && requiredMemberHorizontalRadius.orElseThrow()
                        <= currentMemberHorizontalRadius;
    }

    public boolean currentGroupReservationAdequate() {
        return exactPlanRequiredGroupRadius.isPresent()
                && exactPlanRequiredGroupRadius.orElseThrow()
                        <= currentReservedGroupRadius;
    }

    public boolean requiresFreshReplan() {
        return allMembersCertified
                && (!currentMemberHorizontalReservationAdequate()
                        || !currentGroupReservationAdequate());
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }
}
