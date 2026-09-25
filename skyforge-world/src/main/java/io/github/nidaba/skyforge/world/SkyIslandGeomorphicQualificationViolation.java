package io.github.nidaba.skyforge.world;

/** Hard geomorphic qualification failures that can reject a candidate hydrologic reach pre-carving. */
public enum SkyIslandGeomorphicQualificationViolation {
    CENTERLINE_LOWERING,
    LATERAL_RECOVERY_GRADE,
    EXCAVATION_BURDEN,
    CURVATURE_TO_WIDTH,
    RIDGE_OCCUPANCY,
    LONGITUDINAL_GRADE
}
