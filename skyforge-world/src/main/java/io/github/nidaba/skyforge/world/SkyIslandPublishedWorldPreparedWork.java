package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0062 immutable prepared-work envelope retaining exact snapshot/region/query provenance.
 *
 * <p>The evidence list must equal the exact query result from the captured AUTH-0061 binding.
 */
public record SkyIslandPublishedWorldPreparedWork(
        SkyIslandPublishedWorldPreparedWorkId id,
        SkyIslandPublishedWorldSnapshotBinding binding,
        List<SkyIslandPublishedWorldEntry> queryEvidence) {

    public SkyIslandPublishedWorldPreparedWork {
        id = Objects.requireNonNull(id, "id");
        binding = Objects.requireNonNull(binding, "binding");
        queryEvidence = List.copyOf(Objects.requireNonNull(queryEvidence, "queryEvidence"));

        if (!id.snapshotId().equals(binding.snapshotId())) {
            throw new IllegalArgumentException(
                    "prepared-work identity does not bind the exact snapshot binding");
        }

        List<SkyIslandPublishedWorldEntry> expected =
                binding.query(id.region());
        if (!expected.equals(queryEvidence)) {
            throw new IllegalArgumentException(
                    "prepared-work query evidence differs from exact bound-snapshot region query");
        }
    }

    public static SkyIslandPublishedWorldPreparedWork of(
            SkyIslandPublishedWorldPreparedWorkId id,
            SkyIslandPublishedWorldSnapshotBinding binding) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(binding, "binding");
        return new SkyIslandPublishedWorldPreparedWork(
                id,
                binding,
                binding.query(id.region()));
    }

    public WorldBounds region() {
        return id.region();
    }

    public SkyIslandPublishedWorldSnapshotId snapshotId() {
        return id.snapshotId();
    }

    public int hitCount() {
        return queryEvidence.size();
    }
}
