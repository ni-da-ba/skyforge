package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable one-to-one AUTH-0046 association catalog for one authored world and one realization
 * root.
 *
 * <p>The two root seeds are independent identity domains. They are intentionally stored
 * separately and are not required to be numerically equal.
 */
public final class SkyIslandAuthoredRealizationCatalog {
    private final long authoredWorldSeed;
    private final long realizationRootSeed;
    private final List<SkyIslandAuthoredRealizationAssociation> associations;
    private final Map<SkyIslandIdentity, SkyIslandAuthoredRealizationAssociation> byAuthoredIdentity;
    private final Map<SkyIslandWorldVolumeId, SkyIslandAuthoredRealizationAssociation>
            byRealizedVolumeId;

    public SkyIslandAuthoredRealizationCatalog(
            long authoredWorldSeed,
            long realizationRootSeed,
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        this.authoredWorldSeed = authoredWorldSeed;
        this.realizationRootSeed = realizationRootSeed;
        Objects.requireNonNull(associations, "associations");

        ArrayList<SkyIslandAuthoredRealizationAssociation> ordered =
                new ArrayList<>(associations.size());
        for (SkyIslandAuthoredRealizationAssociation association : associations) {
            ordered.add(Objects.requireNonNull(association, "association"));
        }
        ordered.sort(
                java.util.Comparator.comparing(
                        SkyIslandAuthoredRealizationAssociation::canonicalToken));

        Map<SkyIslandIdentity, SkyIslandAuthoredRealizationAssociation> authored =
                new HashMap<>();
        Map<SkyIslandWorldVolumeId, SkyIslandAuthoredRealizationAssociation> realized =
                new HashMap<>();

        for (SkyIslandAuthoredRealizationAssociation association : ordered) {
            if (association.authoredIdentity().worldSeed() != authoredWorldSeed) {
                throw new IllegalArgumentException(
                        "association belongs to a different authored world");
            }
            if (association.realizedVolumeId().archipelagoRootSeed()
                    != realizationRootSeed) {
                throw new IllegalArgumentException(
                        "association belongs to a different realization root");
            }
            if (authored.put(association.authoredIdentity(), association) != null) {
                throw new IllegalArgumentException(
                        "duplicate authored island association: "
                                + association.authoredIdentity());
            }
            if (realized.put(association.realizedVolumeId(), association) != null) {
                throw new IllegalArgumentException(
                        "duplicate realized volume association: "
                                + association.realizedVolumeId().path());
            }
        }

        this.associations = List.copyOf(ordered);
        this.byAuthoredIdentity = Map.copyOf(authored);
        this.byRealizedVolumeId = Map.copyOf(realized);
    }

    public long authoredWorldSeed() {
        return authoredWorldSeed;
    }

    public long realizationRootSeed() {
        return realizationRootSeed;
    }

    public int size() {
        return associations.size();
    }

    /** Canonical identity order, independent of caller list order. */
    public List<SkyIslandAuthoredRealizationAssociation> associations() {
        return associations;
    }

    public Optional<SkyIslandAuthoredRealizationAssociation> associationFor(
            SkyIslandIdentity authoredIdentity) {
        Objects.requireNonNull(authoredIdentity, "authoredIdentity");
        return Optional.ofNullable(byAuthoredIdentity.get(authoredIdentity));
    }

    public Optional<SkyIslandAuthoredRealizationAssociation> associationFor(
            SkyIslandWorldVolumeId realizedVolumeId) {
        Objects.requireNonNull(realizedVolumeId, "realizedVolumeId");
        return Optional.ofNullable(byRealizedVolumeId.get(realizedVolumeId));
    }
}
