package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0087 exact production binding between one accepted compiled-world publication and one
 * explicit authored-realization association catalog.
 *
 * <p>AUTH-0049 material composition is the first concrete consumer, but the coverage invariant is
 * intentionally reusable by other authored world-space semantics.
 *
 * <p>The binding introduces no association inference. Every published volume must be covered by
 * exactly one AUTH-0046 association that retains the exact published volume value.
 */
public record SkyIslandPublishedAuthoredRealizationBinding(
        SkyIslandCompiledWorldPublication publication,
        SkyIslandAuthoredRealizationCatalog associationCatalog) {

    public SkyIslandPublishedAuthoredRealizationBinding {
        publication = Objects.requireNonNull(publication, "publication");
        associationCatalog = Objects.requireNonNull(associationCatalog, "associationCatalog");

        if (associationCatalog.realizationRootSeed() != publication.catalog().rootSeed()) {
            throw new IllegalArgumentException(
                    "authored-realization catalog root must equal the published realization root");
        }
        if (associationCatalog.size() != publication.volumeCount()) {
            throw new IllegalArgumentException(
                    "published authored-realization binding requires exact one-to-one volume coverage");
        }

        for (SkyIslandWorldVolume publishedVolume : publication.catalog().volumes()) {
            SkyIslandAuthoredRealizationAssociation association =
                    associationCatalog.associationFor(publishedVolume.id())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "published volume lacks an explicit authored-realization association: "
                                            + publishedVolume.id().path()));
            if (!association.realizedVolume().equals(publishedVolume)) {
                throw new IllegalArgumentException(
                        "authored-realization association does not retain the exact published volume: "
                                + publishedVolume.id().path());
            }
        }
    }

    /** Number of exactly covered published volumes. */
    public int volumeCount() {
        return publication.volumeCount();
    }

    /** Authored world identity remains independent of the realized publication root. */
    public long authoredWorldSeed() {
        return associationCatalog.authoredWorldSeed();
    }

    /**
     * Creates the already-accepted AUTH-0049 composer from the exact association set proven here.
     *
     * <p>Concrete backend material identity remains outside {@code skyforge-world}.
     */
    public SkyIslandWorldAuthoredMaterialComposer materialComposer() {
        return new SkyIslandWorldAuthoredMaterialComposer(associationCatalog);
    }
}
