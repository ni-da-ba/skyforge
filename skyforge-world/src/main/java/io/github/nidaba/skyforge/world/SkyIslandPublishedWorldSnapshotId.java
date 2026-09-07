package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0060 stable backend-neutral identity for one activated publication-view snapshot.
 *
 * <p>The identity carries the exact canonical AUTH-0059 view identity plus an explicit monotonic
 * activation revision. The revision is a version axis, not a content hash.
 */
public record SkyIslandPublishedWorldSnapshotId(
        int schemaVersion,
        long snapshotRevision,
        List<SkyIslandCompiledWorldPublicationId> viewIdentity) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldSnapshotId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world snapshot identity schema: " + schemaVersion);
        }
        if (snapshotRevision <= 0) {
            throw new IllegalArgumentException("snapshotRevision must be positive");
        }
        viewIdentity = List.copyOf(Objects.requireNonNull(viewIdentity, "viewIdentity"));
        if (viewIdentity.isEmpty()) {
            throw new IllegalArgumentException("snapshot view identity must not be empty");
        }

        long previousRoot = 0L;
        boolean first = true;
        for (SkyIslandCompiledWorldPublicationId publicationId : viewIdentity) {
            publicationId = Objects.requireNonNull(publicationId, "publication identity");
            if (!first
                    && Long.compareUnsigned(
                                    previousRoot,
                                    publicationId.archipelagoRootSeed())
                            >= 0) {
                throw new IllegalArgumentException(
                        "snapshot view identity must use strict canonical unsigned-root order");
            }
            previousRoot = publicationId.archipelagoRootSeed();
            first = false;
        }
    }

    public static SkyIslandPublishedWorldSnapshotId of(
            long snapshotRevision,
            SkyIslandPublishedWorldView view) {
        Objects.requireNonNull(view, "view");
        return new SkyIslandPublishedWorldSnapshotId(
                SCHEMA_VERSION,
                snapshotRevision,
                view.viewIdentity());
    }

    /**
     * Stable diagnostic/cache token containing the full publication-set identity.
     *
     * <p>This is intentionally not a digest. Exact publication identities remain visible.
     */
    public String canonicalToken() {
        StringBuilder result =
                new StringBuilder(
                        String.format(
                                Locale.ROOT,
                                "sfviewsnap:v%d:%016x:%d",
                                schemaVersion,
                                snapshotRevision,
                                viewIdentity.size()));
        for (SkyIslandCompiledWorldPublicationId publicationId : viewIdentity) {
            result.append(':').append(publicationId.canonicalToken());
        }
        return result.toString();
    }
}
