package io.github.nidaba.skyforge.world;

/** Fail-closed completeness status for a reach or terminal hydrology component. */
public enum SkyIslandHydraulicAssemblyStatus {
    QUALIFIED,
    TERMINAL_DEFERRED,
    TRANSITION_DEFERRED,
    PHYSICAL_REJECTION,
    NUMERICAL_FAILURE
}
