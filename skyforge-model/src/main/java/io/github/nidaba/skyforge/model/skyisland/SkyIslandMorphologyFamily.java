package io.github.nidaba.skyforge.model.skyisland;

import java.util.Objects;

/**
 * Built-in semantic primary morphology families for suspended sky islands.
 *
 * <p>This vocabulary is intentionally extensible by future engine versions. It is not the extension
 * mechanism for arbitrary user-authored morphology providers; custom/provider-based morphology is a
 * separate future API boundary.
 */
public enum SkyIslandMorphologyFamily {
    /** High compact mass with a pronounced crown and concentrated underside. */
    MASSIF("massif"),

    /** Broad, comparatively level upper interior with a compact footprint. */
    TABLELAND("tableland"),

    /** Strongly elongated suspended mass organized around one dominant axis. */
    SPINE("spine"),

    /** Raised outer interior surrounding a lower central upper-surface basin. */
    BASIN("basin"),

    /** Star-shaped primary footprint with several broad connected shoulders. */
    LOBED("lobed");

    private final String identifier;

    SkyIslandMorphologyFamily(String identifier) {
        this.identifier = identifier;
    }

    /** Returns the stable lowercase identifier used by semantic serialization and provenance. */
    public String identifier() {
        return identifier;
    }

    /** Resolves one built-in identifier exactly. */
    public static SkyIslandMorphologyFamily fromIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        for (SkyIslandMorphologyFamily family : values()) {
            if (family.identifier.equals(identifier)) {
                return family;
            }
        }
        throw new IllegalArgumentException("unknown built-in sky-island morphology family: " + identifier);
    }
}
