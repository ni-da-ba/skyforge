package io.github.nidaba.skyforge.recipes.skyisland.group;

import java.util.List;
import java.util.Objects;

/** Immutable deterministic placement result for one multi-island group. */
public record SkyIslandGroupPlan(
        long rootSeed,
        double groupCenterX,
        double groupCenterZ,
        double baseSuspensionElevation,
        double requiredCenterSpacing,
        SkyIslandGroupLayout layout,
        List<SkyIslandGroupMemberPlan> members) {

    /** Validates member ordering and freezes the plan list. */
    public SkyIslandGroupPlan {
        requireFinite("groupCenterX", groupCenterX);
        requireFinite("groupCenterZ", groupCenterZ);
        requireFinite("baseSuspensionElevation", baseSuspensionElevation);
        if (!Double.isFinite(requiredCenterSpacing) || requiredCenterSpacing <= 0.0) {
            throw new IllegalArgumentException("requiredCenterSpacing must be finite and positive");
        }
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(members, "members");
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("group plan must contain at least one member");
        }
        for (int index = 0; index < members.size(); index++) {
            if (members.get(index).ordinal() != index) {
                throw new IllegalArgumentException(
                        "group member ordinals must be contiguous and match list order");
            }
        }
    }

    /** Number of planned islands. */
    public int memberCount() {
        return members.size();
    }

    /** Smallest observed horizontal center-to-center distance, or positive infinity for one member. */
    public double minimumObservedCenterSpacing() {
        if (members.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int first = 0; first < members.size(); first++) {
            var firstDescriptor = members.get(first).descriptor();
            for (int second = first + 1; second < members.size(); second++) {
                var secondDescriptor = members.get(second).descriptor();
                double dx = firstDescriptor.centerX() - secondDescriptor.centerX();
                double dz = firstDescriptor.centerZ() - secondDescriptor.centerZ();
                minimum = Math.min(minimum, Math.hypot(dx, dz));
            }
        }
        return minimum;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
