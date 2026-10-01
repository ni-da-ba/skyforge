package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0060 immutable activated snapshot of one exact admitted AUTH-0059 publication view.
 *
 * <p>The snapshot is a backend-neutral capability. It does not itself mutate or bind a backend.
 */
public record SkyIslandPublishedWorldSnapshot(
        SkyIslandPublishedWorldSnapshotId id,
        SkyIslandPublishedWorldView view) {

    public SkyIslandPublishedWorldSnapshot {
        id = Objects.requireNonNull(id, "id");
        view = Objects.requireNonNull(view, "view");
        if (!id.viewIdentity().equals(view.viewIdentity())) {
            throw new IllegalArgumentException(
                    "snapshot identity does not bind the exact admitted publication view");
        }
    }

    public static SkyIslandPublishedWorldSnapshot of(
            long snapshotRevision,
            SkyIslandPublishedWorldView view) {
        Objects.requireNonNull(view, "view");
        return new SkyIslandPublishedWorldSnapshot(
                SkyIslandPublishedWorldSnapshotId.of(snapshotRevision, view),
                view);
    }

    public int publicationCount() {
        return view.publicationCount();
    }

    public int volumeCount() {
        return view.volumeCount();
    }

    /** Delegates the immutable region query while preserving publication/support provenance. */
    public List<SkyIslandPublishedWorldEntry> query(WorldBounds region) {
        return view.query(region);
    }
}
