package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral AUTH-0037 semantic material-palette roles.
 *
 * <p>Roles describe how a backend may use a candidate material binding. They are not registry
 * entries, named rocks, block ids, or final placement probabilities.
 */
public enum SkyIslandSemanticMaterialPaletteRole {
    PRIMARY_MATRIX,
    SECONDARY_MATRIX,
    ALTERATION_OVERPRINT,
    HYDROLOGIC_CONDITIONING,
    MINERAL_BEARING_STRUCTURE
}
