package io.github.nidaba.skyforge.world;

/** Result of the local F3B finite-confluence head compatibility solve. */
public enum SkyIslandConfluenceHeadCompatibilityStatus {
    SOLVED,
    INFEASIBLE,
    NUMERICAL_FAILURE,
    CASCADE_COUPLED
}
