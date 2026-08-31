package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

/** Semantic importance of one child group inside an archipelago plan. */
public enum SkyIslandGroupRole {
    /** Dominant formation around which an archipelago may organize. */
    ANCHOR,
    /** Major supporting formation with substantial spatial weight. */
    SECONDARY,
    /** Smaller formation associated with an anchor or secondary group. */
    SATELLITE,
    /** Deliberately isolated peripheral formation. */
    OUTLIER
}
