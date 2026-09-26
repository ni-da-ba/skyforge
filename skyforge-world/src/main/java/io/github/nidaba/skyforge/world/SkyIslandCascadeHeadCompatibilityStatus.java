package io.github.nidaba.skyforge.world;

/** Result of the local F3C authored-CASCADE head discontinuity solve. */
public enum SkyIslandCascadeHeadCompatibilityStatus {
    SOLVED,
    INFEASIBLE,
    NUMERICAL_FAILURE,
    BOUNDARY_COUPLED
}
