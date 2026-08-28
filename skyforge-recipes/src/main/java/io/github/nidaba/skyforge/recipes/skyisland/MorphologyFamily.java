package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import java.util.Objects;

/**
 * Recipe-layer compatibility names for the accepted primary suspended-landform families.
 *
 * <p>Schema-2 callers should select {@link SkyIslandMorphologyFamily} in the semantic descriptor.
 * This enum remains as a compatibility adapter for the accepted SF-IMP-0018 through SF-IMP-0020
 * recipe APIs and evidence corpus.
 */
public enum MorphologyFamily {
    /** High compact mass with a pronounced crown and concentrated underside. */
    MASSIF("massif", SkyIslandMorphologyFamily.MASSIF),

    /** Broad, comparatively level upper interior with a compact footprint. */
    TABLELAND("tableland", SkyIslandMorphologyFamily.TABLELAND),

    /** Strongly elongated suspended mass organized around one dominant axis. */
    SPINE("spine", SkyIslandMorphologyFamily.SPINE),

    /** Raised outer interior surrounding a lower central upper-surface basin. */
    BASIN("basin", SkyIslandMorphologyFamily.BASIN),

    /** Star-shaped primary footprint with several broad connected shoulders. */
    LOBED("lobed", SkyIslandMorphologyFamily.LOBED);

    private final String identifier;
    private final SkyIslandMorphologyFamily semanticFamily;

    MorphologyFamily(String identifier, SkyIslandMorphologyFamily semanticFamily) {
        this.identifier = identifier;
        this.semanticFamily = semanticFamily;
    }

    /** Returns the stable recipe-layer identifier used by evidence and provenance. */
    public String identifier() {
        return identifier;
    }

    /** Returns the corresponding schema-2 semantic family. */
    public SkyIslandMorphologyFamily semanticFamily() {
        return semanticFamily;
    }

    /** Resolves the compatibility recipe family for one schema-2 semantic family. */
    public static MorphologyFamily fromSemantic(SkyIslandMorphologyFamily family) {
        Objects.requireNonNull(family, "family");
        for (MorphologyFamily candidate : values()) {
            if (candidate.semanticFamily == family) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unsupported semantic morphology family: " + family);
    }
}
