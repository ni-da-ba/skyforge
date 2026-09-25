package io.github.nidaba.skyforge.world;

/** Hard pre-authoring rejection causes for candidate fluvial geometry. */
public enum SkyIslandGeomorphicQualificationFailureKind {
    RIDGE_OCCUPANCY,
    EXCESSIVE_CENTERLINE_LOWERING,
    EXCESSIVE_INCISION_TO_WIDTH,
    EXCESSIVE_WATER_DEPTH_TO_WIDTH,
    EXCESSIVE_LATERAL_GRADE,
    EXCESSIVE_LONGITUDINAL_GRADE,
    EXCESSIVE_CURVATURE,
    EXCESSIVE_EXCAVATION_VOLUME,
    EXCESSIVE_RAW_TERRAIN_ASCENT
}
