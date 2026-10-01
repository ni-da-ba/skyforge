package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import java.util.Objects;

/** One instantiated child-group plan inside a hierarchical archipelago. */
public record SkyIslandArchipelagoGroupPlan(
        int ordinal,
        String identifier,
        SkyIslandGroupRole role,
        long groupRootSeed,
        double reservedGroupRadius,
        double orientationRadians,
        SkyIslandGroupPlan groupPlan) {

    /** Validates stable identity and finite placement metadata. */
    public SkyIslandArchipelagoGroupPlan {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(groupPlan, "groupPlan");
        if (!Double.isFinite(reservedGroupRadius) || reservedGroupRadius <= 0.0) {
            throw new IllegalArgumentException("reservedGroupRadius must be finite and positive");
        }
        if (!Double.isFinite(orientationRadians)) {
            throw new IllegalArgumentException("orientationRadians must be finite");
        }
    }

    /** Group anchor X inherited by the child plan. */
    public double centerX() {
        return groupPlan.groupCenterX();
    }

    /** Group anchor Z inherited by the child plan. */
    public double centerZ() {
        return groupPlan.groupCenterZ();
    }

    /** Group base suspension elevation inherited by the child plan. */
    public double baseSuspensionElevation() {
        return groupPlan.baseSuspensionElevation();
    }
}
