package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import java.util.Objects;

/** Immutable request for deterministic multi-island placement. */
public record SkyIslandGroupRequest(
        long rootSeed,
        SkyIslandVolumeDescriptor memberTemplate,
        double reservedHorizontalRadius,
        double minimumGap,
        double elevationJitter,
        List<SkyIslandMorphologySpec> memberMorphologies,
        SkyIslandGroupLayout layout) {

    /** Conservative first-proof member-count limit for bounded planning/evidence work. */
    public static final int MAXIMUM_MEMBER_COUNT = 128;

    /** Validates the proof descriptor and the explicit placement reservation contract. */
    public SkyIslandGroupRequest {
        Objects.requireNonNull(memberTemplate, "memberTemplate");
        Objects.requireNonNull(memberMorphologies, "memberMorphologies");
        Objects.requireNonNull(layout, "layout");
        if (memberTemplate.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_1) {
            throw new IllegalArgumentException("group planning currently requires descriptor schema 1");
        }
        if (memberTemplate.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException("group member template must have zero embedded detail amplitude");
        }
        requirePositive("reservedHorizontalRadius", reservedHorizontalRadius);
        requireNonNegative("minimumGap", minimumGap);
        requireNonNegative("elevationJitter", elevationJitter);
        memberMorphologies = List.copyOf(memberMorphologies);
        if (memberMorphologies.isEmpty()) {
            throw new IllegalArgumentException("group must contain at least one member");
        }
        if (memberMorphologies.size() > MAXIMUM_MEMBER_COUNT) {
            throw new IllegalArgumentException(
                    "group exceeds first-proof maximum member count " + MAXIMUM_MEMBER_COUNT);
        }
        double required = 2.0 * reservedHorizontalRadius + minimumGap;
        if (layout.minimumCenterSpacing() < required) {
            throw new IllegalArgumentException(
                    "layout minimum center spacing " + layout.minimumCenterSpacing()
                            + " is smaller than required reservation spacing " + required);
        }
    }

    /** Required pairwise center spacing implied by the reservation radius and requested gap. */
    public double requiredCenterSpacing() {
        return 2.0 * reservedHorizontalRadius + minimumGap;
    }

    /** Number of planned members, equal to the explicit morphology list size. */
    public int memberCount() {
        return memberMorphologies.size();
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
