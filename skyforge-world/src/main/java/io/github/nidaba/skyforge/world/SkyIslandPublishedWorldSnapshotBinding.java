package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0061 immutable preparation-time binding to one exact AUTH-0060 active snapshot.
 *
 * <p>The binding retains the snapshot capability used to prepare work. It does not track or refresh
 * activation state automatically.
 */
public record SkyIslandPublishedWorldSnapshotBinding(
        int schemaVersion,
        SkyIslandPublishedWorldSnapshot snapshot) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldSnapshotBinding {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world snapshot binding schema: " + schemaVersion);
        }
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static SkyIslandPublishedWorldSnapshotBinding of(
            SkyIslandPublishedWorldSnapshot snapshot) {
        return new SkyIslandPublishedWorldSnapshotBinding(
                SCHEMA_VERSION,
                snapshot);
    }

    public SkyIslandPublishedWorldSnapshotId snapshotId() {
        return snapshot.id();
    }

    /** Stable visible binding token; no extra hidden lease identity is manufactured. */
    public String canonicalToken() {
        return "sfbinding:v" + schemaVersion + ":" + snapshotId().canonicalToken();
    }

    /** Preparation queries use the exact captured snapshot, not whatever later becomes active. */
    public List<SkyIslandPublishedWorldEntry> query(WorldBounds region) {
        return snapshot.query(region);
    }
}
