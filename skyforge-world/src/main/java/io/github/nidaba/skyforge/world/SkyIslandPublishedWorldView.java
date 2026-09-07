package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AUTH-0059 immutable admitted view over one or more AUTH-0058 compiled-world publications.
 *
 * <p>Publications are canonically ordered by unsigned regional root. Exactly one publication is
 * permitted per regional root, and certified physical support from different publications must be
 * provably disjoint. Query results retain publication identity and support proof.
 */
public final class SkyIslandPublishedWorldView {
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final List<SkyIslandCompiledWorldPublication> publications;
    private final List<SkyIslandPublishedWorldEntry> entries;

    public SkyIslandPublishedWorldView(
            int schemaVersion,
            List<SkyIslandCompiledWorldPublication> publications) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world view schema: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        Objects.requireNonNull(publications, "publications");
        if (publications.isEmpty()) {
            throw new IllegalArgumentException(
                    "published-world view requires at least one publication");
        }

        ArrayList<SkyIslandCompiledWorldPublication> ordered =
                new ArrayList<>(publications.size());
        for (SkyIslandCompiledWorldPublication publication : publications) {
            ordered.add(Objects.requireNonNull(publication, "publication"));
        }
        ordered.sort(
                (first, second) ->
                        Long.compareUnsigned(
                                first.id().archipelagoRootSeed(),
                                second.id().archipelagoRootSeed()));

        Set<Long> roots = new HashSet<>();
        for (SkyIslandCompiledWorldPublication publication : ordered) {
            if (!roots.add(publication.id().archipelagoRootSeed())) {
                throw new IllegalArgumentException(
                        "AUTH-0059 requires explicit selection of exactly one publication per regional root");
            }
            if (!publication.compilation().supportBundle().fullyCertified()) {
                throw new IllegalArgumentException(
                        "AUTH-0059 view requires fully certified publications");
            }
        }
        this.publications = List.copyOf(ordered);

        ArrayList<SkyIslandPublishedWorldEntry> builtEntries = new ArrayList<>();
        for (SkyIslandCompiledWorldPublication publication : this.publications) {
            for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
                builtEntries.add(SkyIslandPublishedWorldEntry.of(publication, volume));
            }
        }
        requireCrossPublicationSupportIsolation(builtEntries);
        this.entries = List.copyOf(builtEntries);
    }

    public static SkyIslandPublishedWorldView of(
            List<SkyIslandCompiledWorldPublication> publications) {
        return new SkyIslandPublishedWorldView(SCHEMA_VERSION, publications);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Canonical unsigned-root publication order, independent of caller list order. */
    public List<SkyIslandCompiledWorldPublication> publications() {
        return publications;
    }

    /** Publication IDs in the same canonical order used by every flattened query result. */
    public List<SkyIslandCompiledWorldPublicationId> viewIdentity() {
        return publications.stream().map(SkyIslandCompiledWorldPublication::id).toList();
    }

    /** All proof-carrying volumes in publication order then original plan order. */
    public List<SkyIslandPublishedWorldEntry> entries() {
        return entries;
    }

    public int publicationCount() {
        return publications.size();
    }

    public int volumeCount() {
        return entries.size();
    }

    public Optional<SkyIslandCompiledWorldPublication> publicationForRoot(long regionalRootSeed) {
        return publications.stream()
                .filter(
                        publication ->
                                publication.id().archipelagoRootSeed() == regionalRootSeed)
                .findFirst();
    }

    /**
     * Conservative backend-neutral region query preserving canonical publication/plan order.
     *
     * <p>Each hit retains the publication identity and exact support certificate needed by later
     * backend stages. Query bounds remain the broad catalog bounds; proof bounds are carried
     * separately.
     */
    public List<SkyIslandPublishedWorldEntry> query(WorldBounds region) {
        Objects.requireNonNull(region, "region");
        ArrayList<SkyIslandPublishedWorldEntry> result = new ArrayList<>();
        for (SkyIslandPublishedWorldEntry entry : entries) {
            if (entry.volume().bounds().intersects(region)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Explicit compare-and-replace publication update for one regional root.
     *
     * <p>No implicit newest-wins rule exists. The caller must name the exact current publication,
     * and the replacement must keep the same root while strictly increasing revision. The returned
     * view is re-admitted from scratch, including cross-publication support isolation.
     */
    public SkyIslandPublishedWorldView replace(
            SkyIslandCompiledWorldPublicationId expectedCurrent,
            SkyIslandCompiledWorldPublication replacement) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");
        if (replacement.id().archipelagoRootSeed()
                != expectedCurrent.archipelagoRootSeed()) {
            throw new IllegalArgumentException(
                    "replacement publication must preserve regional root");
        }
        if (replacement.id().publicationRevision()
                <= expectedCurrent.publicationRevision()) {
            throw new IllegalArgumentException(
                    "replacement publication revision must strictly increase");
        }

        ArrayList<SkyIslandCompiledWorldPublication> replaced =
                new ArrayList<>(publications);
        int found = -1;
        for (int index = 0; index < replaced.size(); index++) {
            if (replaced.get(index).id().equals(expectedCurrent)) {
                found = index;
                break;
            }
        }
        if (found < 0) {
            throw new IllegalStateException(
                    "expected current publication is absent; refusing stale replacement");
        }
        replaced.set(found, replacement);
        return new SkyIslandPublishedWorldView(schemaVersion, replaced);
    }

    private static void requireCrossPublicationSupportIsolation(
            List<SkyIslandPublishedWorldEntry> entries) {
        for (int firstIndex = 0; firstIndex < entries.size(); firstIndex++) {
            SkyIslandPublishedWorldEntry first = entries.get(firstIndex);
            for (int secondIndex = firstIndex + 1;
                    secondIndex < entries.size();
                    secondIndex++) {
                SkyIslandPublishedWorldEntry second = entries.get(secondIndex);
                if (first.publicationId().archipelagoRootSeed()
                        == second.publicationId().archipelagoRootSeed()) {
                    continue;
                }
                if (first.certifiedSupportBounds()
                        .intersects(second.certifiedSupportBounds())) {
                    throw new IllegalArgumentException(
                            "AUTH-0059 cross-publication certified support overlaps or touches: "
                                    + first.publicationId().canonicalToken()
                                    + " / "
                                    + second.publicationId().canonicalToken());
                }
            }
        }
    }
}
