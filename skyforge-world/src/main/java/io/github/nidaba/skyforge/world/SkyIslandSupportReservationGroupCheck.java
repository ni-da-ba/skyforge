package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/** AUTH-0053 proof that one planned group's outer reservation contains every certified member. */
public record SkyIslandSupportReservationGroupCheck(
        int groupOrdinal,
        String groupIdentifier,
        double reservedGroupRadius,
        OptionalDouble requiredGroupRadius,
        boolean allMembersCertified,
        boolean allMemberReservationsAdequate) {

    public SkyIslandSupportReservationGroupCheck {
        if (groupOrdinal < 0) {
            throw new IllegalArgumentException("groupOrdinal must be non-negative");
        }
        groupIdentifier = Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        if (!Double.isFinite(reservedGroupRadius) || reservedGroupRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "reservedGroupRadius must be finite and positive");
        }
        requiredGroupRadius =
                Objects.requireNonNull(requiredGroupRadius, "requiredGroupRadius");
        if (requiredGroupRadius.isPresent()
                && (!Double.isFinite(requiredGroupRadius.orElseThrow())
                        || requiredGroupRadius.orElseThrow() <= 0.0)) {
            throw new IllegalArgumentException(
                    "requiredGroupRadius must be finite and positive when present");
        }
        if (requiredGroupRadius.isPresent() != allMembersCertified) {
            throw new IllegalArgumentException(
                    "requiredGroupRadius is available exactly when all members are certified");
        }
    }

    public boolean groupReservationAdequate() {
        return requiredGroupRadius.isPresent()
                && requiredGroupRadius.orElseThrow() <= reservedGroupRadius;
    }

    /** Whether both inner member reservations and the outer group reservation remain valid. */
    public boolean admitted() {
        return allMembersCertified
                && allMemberReservationsAdequate
                && groupReservationAdequate();
    }
}
