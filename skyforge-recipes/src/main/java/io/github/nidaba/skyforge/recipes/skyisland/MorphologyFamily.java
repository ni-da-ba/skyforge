package io.github.nidaba.skyforge.recipes.skyisland;

/** Experimental primary suspended-landform families for SF-IMP-0018. */
public enum MorphologyFamily {
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

    MorphologyFamily(String identifier) {
        this.identifier = identifier;
    }

    /** Returns the stable recipe-layer identifier used by evidence and provenance. */
    public String identifier() {
        return identifier;
    }
}
