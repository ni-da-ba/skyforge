package io.github.nidaba.skyforge.world;

/** AUTH-0048 outcome for one multi-island authored-realization ownership query. */
public enum SkyIslandAuthoredRealizationOwnershipStatus {
    /** No explicit associated native-authored island owns the world point. */
    NONE,

    /** Exactly one explicit associated native-authored island owns the world point. */
    UNIQUE,

    /** More than one explicit associated native-authored island owns the world point. */
    AMBIGUOUS
}
