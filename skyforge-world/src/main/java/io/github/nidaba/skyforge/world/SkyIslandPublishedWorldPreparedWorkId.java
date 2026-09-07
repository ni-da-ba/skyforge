package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0062 deterministic identity for one unit of work prepared from an exact snapshot binding.
 *
 * <p>The explicit work sequence is an author/adapter-controlled identity axis. It is not a content
 * hash. Exact snapshot and region provenance remain structural parts of the identity.
 */
public record SkyIslandPublishedWorldPreparedWorkId(
        int schemaVersion,
        long workSequence,
        SkyIslandPublishedWorldSnapshotId snapshotId,
        WorldBounds region) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldPreparedWorkId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world prepared-work identity schema: " + schemaVersion);
        }
        if (workSequence <= 0) {
            throw new IllegalArgumentException("workSequence must be positive");
        }
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        region = Objects.requireNonNull(region, "region");
    }

    public static SkyIslandPublishedWorldPreparedWorkId of(
            long workSequence,
            SkyIslandPublishedWorldSnapshotBinding binding,
            WorldBounds region) {
        Objects.requireNonNull(binding, "binding");
        return new SkyIslandPublishedWorldPreparedWorkId(
                SCHEMA_VERSION,
                workSequence,
                binding.snapshotId(),
                region);
    }

    /**
     * Stable visible identity containing exact snapshot and region coordinates.
     *
     * <p>Coordinates are encoded as IEEE-754 bit patterns to avoid locale/decimal ambiguity.
     */
    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfwork:v%d:%016x:%s:%016x:%016x:%016x:%016x:%016x:%016x",
                schemaVersion,
                workSequence,
                snapshotId.canonicalToken(),
                Double.doubleToLongBits(region.minimumX()),
                Double.doubleToLongBits(region.maximumX()),
                Double.doubleToLongBits(region.minimumY()),
                Double.doubleToLongBits(region.maximumY()),
                Double.doubleToLongBits(region.minimumZ()),
                Double.doubleToLongBits(region.maximumZ()));
    }
}
