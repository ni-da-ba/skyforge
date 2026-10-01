package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0046 explicit association between one placement-free native-authored island and one
 * independently compiled world volume.
 *
 * <p>The association is declarative. It does not discover, rank, or infer a realized volume from
 * geometry, seed similarity, list position, spatial proximity, or backend state.
 */
public record SkyIslandAuthoredRealizationAssociation(
        int schemaVersion,
        SkyIslandDescriptor authoredDescriptor,
        SkyIslandWorldVolume realizedVolume) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandAuthoredRealizationAssociation {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported authored-realization association schema: " + schemaVersion);
        }
        authoredDescriptor = Objects.requireNonNull(authoredDescriptor, "authoredDescriptor");
        realizedVolume = Objects.requireNonNull(realizedVolume, "realizedVolume");

        double authoredRadius = authoredDescriptor.nominalRadius();
        double realizedRadius = realizedVolume.compiledVolume().descriptor().nominalRadius();
        if (Double.doubleToLongBits(authoredRadius) != Double.doubleToLongBits(realizedRadius)) {
            throw new IllegalArgumentException(
                    "authored and realized nominal radii must match exactly");
        }

        var realizedDescriptor = realizedVolume.compiledVolume().descriptor();
        if (realizedDescriptor.hasSemanticMorphologyFamily()
                && realizedDescriptor.morphologyFamily()
                        != authoredDescriptor.morphologyFamily()) {
            throw new IllegalArgumentException(
                    "realized semantic morphology must match authored morphology");
        }
    }

    public static SkyIslandAuthoredRealizationAssociation of(
            SkyIslandDescriptor authoredDescriptor,
            SkyIslandWorldVolume realizedVolume) {
        return new SkyIslandAuthoredRealizationAssociation(
                SCHEMA_VERSION,
                authoredDescriptor,
                realizedVolume);
    }

    public SkyIslandIdentity authoredIdentity() {
        return authoredDescriptor.identity();
    }

    public SkyIslandWorldVolumeId realizedVolumeId() {
        return realizedVolume.id();
    }

    /**
     * Stable diagnostic/cache token containing both identities without asserting that their root
     * seeds or leaf seeds are numerically related.
     */
    public String canonicalToken() {
        SkyIslandIdentity authored = authoredIdentity();
        SkyIslandWorldVolumeId realized = realizedVolumeId();
        String group = realized.groupIdentifier();
        return String.format(
                Locale.ROOT,
                "sfassoc:v%d:%016x:%016x:%016x:%016x:%016x:%d:%s:%08x:%08x:%016x",
                schemaVersion,
                authored.worldSeed(),
                authored.provinceKey(),
                authored.clusterKey(),
                authored.islandKey(),
                realized.archipelagoRootSeed(),
                group.length(),
                group,
                realized.groupOrdinal(),
                realized.memberOrdinal(),
                realized.geometrySeed());
    }
}
