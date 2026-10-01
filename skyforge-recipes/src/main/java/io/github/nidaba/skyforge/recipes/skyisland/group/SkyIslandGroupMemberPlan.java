package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Objects;

/** One deterministic island placement and morphology intent within a group plan. */
public record SkyIslandGroupMemberPlan(
        int ordinal,
        SkyIslandVolumeDescriptor descriptor,
        SkyIslandMorphologySpec morphology,
        double reservedHorizontalRadius) {

    /** Validates immutable member-plan state. */
    public SkyIslandGroupMemberPlan {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(morphology, "morphology");
        if (!Double.isFinite(reservedHorizontalRadius) || reservedHorizontalRadius <= 0.0) {
            throw new IllegalArgumentException("reservedHorizontalRadius must be finite and positive");
        }
    }

    /** Stable ordinal-derived member identifier. */
    public String memberIdentifier() {
        return "member-" + ordinal;
    }
}
