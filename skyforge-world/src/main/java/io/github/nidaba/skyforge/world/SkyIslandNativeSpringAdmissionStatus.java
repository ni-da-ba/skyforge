package io.github.nidaba.skyforge.world;

/** Explainable AUTH-0085 semantic disposition for one candidate native subsurface spring. */
public enum SkyIslandNativeSpringAdmissionStatus {
    /** Water is inside authored cave volume and supported by an accepted AUTH-0023 aquifer cell. */
    ADMITTED_AQUIFER_CAVE_WATER,

    /** Candidate position is outside current authored island geological ownership. */
    OUTSIDE_AUTHORED_ISLAND,

    /** Candidate is not inside AUTH-0030 exterior-connected authored cave volume. */
    NOT_AUTHORED_CAVE_INTERIOR,

    /** Water candidate has no accepted AUTH-0023 aquifer-body cell at the candidate position. */
    NO_AQUIFER_SUPPORT,

    /** Molten fluid has no current geothermal/volcanic authorship semantics. */
    MISSING_GEOTHERMAL_SEMANTICS;

    /** Returns whether the candidate is semantically admitted. */
    public boolean admitted() {
        return this == ADMITTED_AQUIFER_CAVE_WATER;
    }
}
