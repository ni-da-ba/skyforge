package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** AUTH-0054 exact reservation requirement for one deterministic planned member. */
public record SkyIslandSupportReservationMemberRequirement(
        int groupOrdinal,
        String groupIdentifier,
        int memberOrdinal,
        long descriptorSeed,
        String morphologyIdentifier,
        double currentReservedHorizontalRadius,
        Optional<CertifiedSkyIslandSupportEnvelope> supportEnvelope,
        OptionalDouble requiredHorizontalRadius,
        OptionalDouble requiredBelowSuspension,
        OptionalDouble requiredAboveSuspension) {

    public SkyIslandSupportReservationMemberRequirement {
        if (groupOrdinal < 0 || memberOrdinal < 0) {
            throw new IllegalArgumentException("group/member ordinals must be non-negative");
        }
        groupIdentifier = Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        morphologyIdentifier =
                Objects.requireNonNull(morphologyIdentifier, "morphologyIdentifier");
        if (!Double.isFinite(currentReservedHorizontalRadius)
                || currentReservedHorizontalRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "currentReservedHorizontalRadius must be finite and positive");
        }
        supportEnvelope = Objects.requireNonNull(supportEnvelope, "supportEnvelope");
        requiredHorizontalRadius =
                Objects.requireNonNull(requiredHorizontalRadius, "requiredHorizontalRadius");
        requiredBelowSuspension =
                Objects.requireNonNull(requiredBelowSuspension, "requiredBelowSuspension");
        requiredAboveSuspension =
                Objects.requireNonNull(requiredAboveSuspension, "requiredAboveSuspension");

        boolean certified = supportEnvelope.isPresent();
        if (requiredHorizontalRadius.isPresent() != certified
                || requiredBelowSuspension.isPresent() != certified
                || requiredAboveSuspension.isPresent() != certified) {
            throw new IllegalArgumentException(
                    "exact reservation requirements are present exactly when support is certified");
        }
        requiredHorizontalRadius.ifPresent(
                value -> requirePositive("requiredHorizontalRadius", value));
        requiredBelowSuspension.ifPresent(
                value -> requirePositive("requiredBelowSuspension", value));
        requiredAboveSuspension.ifPresent(
                value -> requirePositive("requiredAboveSuspension", value));
    }

    public boolean certified() {
        return supportEnvelope.isPresent();
    }

    public boolean currentHorizontalReservationAdequate() {
        return requiredHorizontalRadius.isPresent()
                && requiredHorizontalRadius.orElseThrow() <= currentReservedHorizontalRadius;
    }

    public String stableMemberIdentifier() {
        return groupIdentifier + "/" + memberOrdinal;
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }
}
