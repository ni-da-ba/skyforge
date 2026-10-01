package io.github.nidaba.skyforge.world;

/** AUTH-0061 validation state for one captured snapshot binding against activation state. */
public enum SkyIslandPublishedWorldBindingStatus {
    /** The exact bound snapshot identity is still active in the supplied activation state. */
    CURRENT,

    /** Another snapshot identity is active in the supplied activation state. */
    STALE,

    /** The supplied activation state has no active snapshot. */
    INACTIVE
}
