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
        double centerX,
        double centerZ,
        double suspensionElevation,
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
        requireFinite("centerX", centerX);
        requireFinite("centerZ", centerZ);
        requireFinite("suspensionElevation", suspensionElevation);
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
        if (!certified()) {
            return false;
        }
        double support = requiredHorizontalRadius();
        return Math.nextDown(centerX - support) >= centerX - reservedHorizontalRadius
                && Math.nextUp(centerX + support) <= centerX + reservedHorizontalRadius
                && Math.nextDown(centerZ - support) >= centerZ - reservedHorizontalRadius
                && Math.nextUp(centerZ + support) <= centerZ + reservedHorizontalRadius;
    }

    public boolean belowReservationAdequate() {
        if (!certified()) {
            return false;
        }
        return Math.nextDown(suspensionElevation - requiredBelowSuspension())
                >= suspensionElevation - reservedBelowSuspension;
    }

    public boolean aboveReservationAdequate() {
        if (!certified()) {
            return false;
        }
        return Math.nextUp(suspensionElevation + requiredAboveSuspension())
                <= suspensionElevation + reservedAboveSuspension;
    }

    public boolean verticalReservationAdequate() {
        return belowReservationAdequate() && aboveReservationAdequate();
    }

    /** Whether this exact member is fully proof-backed by its existing query reservations. */
    public boolean admitted() {
        return horizontalReservationAdequate() && verticalReservationAdequate();
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
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
