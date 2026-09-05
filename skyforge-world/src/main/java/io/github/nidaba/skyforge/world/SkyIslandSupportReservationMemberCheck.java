package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0053 seed-aware reservation proof for one exact planned island member. */
public record SkyIslandSupportReservationMemberCheck(
        int groupOrdinal,
        String groupIdentifier,
        int memberOrdinal,
        long descriptorSeed,
        String morphologyIdentifier,
        double reservedHorizontalRadius,
        double reservedBelowSuspension,
        double reservedAboveSuspension,
        Optional<CertifiedSkyIslandSupportEnvelope> supportEnvelope) {

    public SkyIslandSupportReservationMemberCheck {
        if (groupOrdinal < 0 || memberOrdinal < 0) {
            throw new IllegalArgumentException("group/member ordinals must be non-negative");
        }
        groupIdentifier = Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        morphologyIdentifier =
                Objects.requireNonNull(morphologyIdentifier, "morphologyIdentifier");
        requirePositive("reservedHorizontalRadius", reservedHorizontalRadius);
        requireNonNegative("reservedBelowSuspension", reservedBelowSuspension);
        requireNonNegative("reservedAboveSuspension", reservedAboveSuspension);
        supportEnvelope = Objects.requireNonNull(supportEnvelope, "supportEnvelope");
    }

    public boolean certified() {
        return supportEnvelope.isPresent();
    }

    public double requiredHorizontalRadius() {
        return supportEnvelope
                .map(CertifiedSkyIslandSupportEnvelope::maximumHorizontalRadius)
                .orElse(Double.NaN);
    }

    public double requiredBelowSuspension() {
        return supportEnvelope
                .map(CertifiedSkyIslandSupportEnvelope::maximumUndersideDepth)
                .orElse(Double.NaN);
    }

    public double requiredAboveSuspension() {
        return supportEnvelope
                .map(CertifiedSkyIslandSupportEnvelope::maximumUpperOffset)
                .orElse(Double.NaN);
    }

    public boolean horizontalReservationAdequate() {
        return certified() && requiredHorizontalRadius() <= reservedHorizontalRadius;
    }

    public boolean belowReservationAdequate() {
        return certified() && requiredBelowSuspension() <= reservedBelowSuspension;
    }

    public boolean aboveReservationAdequate() {
        return certified() && requiredAboveSuspension() <= reservedAboveSuspension;
    }

    public boolean verticalReservationAdequate() {
        return belowReservationAdequate() && aboveReservationAdequate();
    }

    /** Whether this exact member is fully proof-backed by its existing query reservations. */
    public boolean admitted() {
        return horizontalReservationAdequate() && verticalReservationAdequate();
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
