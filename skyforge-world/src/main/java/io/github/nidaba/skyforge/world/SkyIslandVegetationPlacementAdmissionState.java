package io.github.nidaba.skyforge.world;

/**
 * Resolved admission state for one deterministic vegetation functional-group candidate.
 *
 * <p>Admission is not species selection, realized occupancy, population state, or backend\n * placement.
 */
public record SkyIslandVegetationPlacementAdmissionState(boolean admitted) {}
