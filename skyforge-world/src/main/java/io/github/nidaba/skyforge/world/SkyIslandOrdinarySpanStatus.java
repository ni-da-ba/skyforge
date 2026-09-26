package io.github.nidaba.skyforge.world;

/** F3D finite ordinary-span result after transition-boundary partitioning. */
public enum SkyIslandOrdinarySpanStatus {
    SOLVED_QUALIFIED,
    SOLVED_REJECTED,
    INFEASIBLE,
    NUMERICAL_FAILURE,
    BOUNDARY_DEFERRED
}
