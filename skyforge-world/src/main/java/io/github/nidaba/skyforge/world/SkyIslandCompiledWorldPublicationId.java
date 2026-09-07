package io.github.nidaba.skyforge.world;

import java.util.Locale;

/**
 * Stable backend-neutral identity for one published proof-backed regional world.
 *
 * <p>The archipelago root identifies the accepted regional realization domain. The publication
 * revision is an explicit author-controlled version axis within that domain; it is not a content
 * hash and must not be interpreted independently of the publication binding.
 */
public record SkyIslandCompiledWorldPublicationId(
        int schemaVersion,
        long archipelagoRootSeed,
        long publicationRevision) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandCompiledWorldPublicationId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported compiled-world publication identity schema: " + schemaVersion);
        }
        if (publicationRevision <= 0) {
            throw new IllegalArgumentException("publicationRevision must be positive");
        }
    }

    public static SkyIslandCompiledWorldPublicationId of(
            long archipelagoRootSeed, long publicationRevision) {
        return new SkyIslandCompiledWorldPublicationId(
                SCHEMA_VERSION, archipelagoRootSeed, publicationRevision);
    }

    /** Stable diagnostic/cache token. Revision remains an explicit version, not a content hash. */
    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfpub:v%d:%016x:%016x",
                schemaVersion,
                archipelagoRootSeed,
                publicationRevision);
    }
}
