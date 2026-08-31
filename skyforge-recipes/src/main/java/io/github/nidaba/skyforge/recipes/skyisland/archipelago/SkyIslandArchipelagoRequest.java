package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable request for deterministic hierarchical placement of child island groups. */
public record SkyIslandArchipelagoRequest(
        long rootSeed,
        double centerX,
        double centerZ,
        double baseSuspensionElevation,
        double minimumGroupGap,
        List<SkyIslandGroupTemplate> groupTemplates,
        SkyIslandArchipelagoLayout layout) {

    /** Conservative first-proof group-count bound. */
    public static final int MAXIMUM_GROUP_COUNT = 32;

    /** Validates finite anchor controls, stable template identity, and hierarchy semantics. */
    public SkyIslandArchipelagoRequest {
        requireFinite("centerX", centerX);
        requireFinite("centerZ", centerZ);
        requireFinite("baseSuspensionElevation", baseSuspensionElevation);
        requireNonNegative("minimumGroupGap", minimumGroupGap);
        Objects.requireNonNull(groupTemplates, "groupTemplates");
        Objects.requireNonNull(layout, "layout");
        groupTemplates = List.copyOf(groupTemplates);
        if (groupTemplates.isEmpty()) {
            throw new IllegalArgumentException("archipelago must contain at least one group");
        }
        if (groupTemplates.size() > MAXIMUM_GROUP_COUNT) {
            throw new IllegalArgumentException(
                    "archipelago exceeds first-proof maximum group count " + MAXIMUM_GROUP_COUNT);
        }
        Set<String> identifiers = new HashSet<>();
        for (SkyIslandGroupTemplate template : groupTemplates) {
            Objects.requireNonNull(template, "group template");
            if (!identifiers.add(template.identifier())) {
                throw new IllegalArgumentException(
                        "archipelago group template identifiers must be unique: " + template.identifier());
            }
        }
        if (layout instanceof SkyIslandArchipelagoLayout.Hub
                && groupTemplates.get(0).role() != SkyIslandGroupRole.ANCHOR) {
            throw new IllegalArgumentException(
                    "hub archipelago requires group template 0 to have ANCHOR role");
        }
    }

    /** Number of child groups requested. */
    public int groupCount() {
        return groupTemplates.size();
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requireNonNegative(String property, double value) {
        requireFinite(property, value);
        if (value < 0.0) {
            throw new IllegalArgumentException(property + " must be non-negative");
        }
    }
}
