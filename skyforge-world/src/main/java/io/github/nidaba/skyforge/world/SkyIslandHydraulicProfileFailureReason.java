package io.github.nidaba.skyforge.world;

/** Fail-closed reason returned when an ordinary hydraulic profile has no feasible solution. */
public enum SkyIslandHydraulicProfileFailureReason {
    BED_GRADE_INFEASIBLE,
    WATER_SURFACE_INFEASIBLE
}
