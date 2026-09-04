package io.github.nidaba.skyforge.world;

/** Geological rationale for one semantic connection inside an authored cave system. */
public enum SkyIslandCaveConnectionKind {
    /** Chambers occupy the same connected AUTH-0023 void-prone domain. */
    VOID_CONTINUITY,
    /** Separate void domains are plausibly bridged by an expressed fracture system. */
    FRACTURE_BRIDGE,
    /** Separate void domains are plausibly bridged by an expressed aquifer body. */
    AQUIFER_BRIDGE,
    /** Separate void domains are jointly supported by fracture and aquifer structure. */
    MIXED_GEOLOGIC_BRIDGE
}
