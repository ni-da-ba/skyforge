package io.github.nidaba.skyforge.world;

/** AUTH-0050 explicit pairwise authored-realization overlap policy. */
public enum SkyIslandAuthoredOverlapMode {
    /** Pair must be certified as unable to share a native-authored world point. */
    SEPARATE,

    /**
     * Pair is an intentional same-X/Z vertical stack and must have a certified conservative
     * vertical gap.
     */
    STACKED,

    /** True native overlap is explicitly permitted for a future composition policy. */
    COMPOSE
}
