package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import java.util.List;
import java.util.Objects;

/** Immutable hierarchical placement result containing independently planned child groups. */
public record SkyIslandArchipelagoPlan(
        long rootSeed,
        double centerX,
        double centerZ,
        double baseSuspensionElevation,
        double minimumGroupGap,
        SkyIslandArchipelagoLayout layout,
        List<SkyIslandArchipelagoGroupPlan> groups) {

    /** Validates child ordering and freezes the hierarchy. */
    public SkyIslandArchipelagoPlan {
        requireFinite("centerX", centerX);
        requireFinite("centerZ", centerZ);
        requireFinite("baseSuspensionElevation", baseSuspensionElevation);
        if (!Double.isFinite(minimumGroupGap) || minimumGroupGap < 0.0) {
            throw new IllegalArgumentException("minimumGroupGap must be finite and non-negative");
        }
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(groups, "groups");
        groups = List.copyOf(groups);
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("archipelago plan must contain at least one group");
        }
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).ordinal() != index) {
                throw new IllegalArgumentException(
                        "archipelago group ordinals must be contiguous and match list order");
            }
        }
    }

    /** Number of child groups. */
    public int groupCount() {
        return groups.size();
    }

    /** Total number of island members across all child groups. */
    public int totalMemberCount() {
        int total = 0;
        for (SkyIslandArchipelagoGroupPlan group : groups) {
            total = Math.addExact(total, group.groupPlan().memberCount());
        }
        return total;
    }

    /** Smallest realized group-envelope gap, or positive infinity for one group. */
    public double minimumObservedGroupGap() {
        if (groups.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int first = 0; first < groups.size(); first++) {
            SkyIslandArchipelagoGroupPlan a = groups.get(first);
            for (int second = first + 1; second < groups.size(); second++) {
                SkyIslandArchipelagoGroupPlan b = groups.get(second);
                double distance = Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ());
                minimum = Math.min(
                        minimum,
                        distance - a.reservedGroupRadius() - b.reservedGroupRadius());
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
