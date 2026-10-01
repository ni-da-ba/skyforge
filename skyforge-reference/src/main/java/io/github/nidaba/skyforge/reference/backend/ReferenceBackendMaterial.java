package io.github.nidaba.skyforge.reference.backend;

/**
 * Reference-backend material tokens used only for adapter-seam verification.
 *
 * <p>These values are not part of the Skyforge world or material ontology.
 */
public enum ReferenceBackendMaterial {
    AIR(false),
    GREEN_SURFACE(true),
    FROZEN_SURFACE(true),
    EXPOSED_SHELL(true),
    STRUCTURAL_ROCK(true);

    private final boolean solid;

    ReferenceBackendMaterial(boolean solid) {
        this.solid = solid;
    }

    public boolean isSolid() {
        return solid;
    }
}
