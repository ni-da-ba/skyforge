package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0063 immutable capability recording admission of one exact CURRENT prepared-work validation
 * to downstream commit coordination.
 *
 * <p>A ticket does not assert that backend mutation has begun or succeeded.
 */
public record SkyIslandPublishedWorldCommitTicket(
        SkyIslandPublishedWorldCommitTicketId id,
        SkyIslandPublishedWorldPreparedWorkValidation admissionValidation) {

    public SkyIslandPublishedWorldCommitTicket {
        id = Objects.requireNonNull(id, "id");
        admissionValidation =
                Objects.requireNonNull(admissionValidation, "admissionValidation");

        admissionValidation.requireCurrent();
        if (!id.preparedWorkId().equals(admissionValidation.preparedWork().id())) {
            throw new IllegalArgumentException(
                    "commit-ticket identity does not bind the exact admitted prepared work");
        }
    }

    public SkyIslandPublishedWorldPreparedWork preparedWork() {
        return admissionValidation.preparedWork();
    }

    public SkyIslandPublishedWorldSnapshotId snapshotId() {
        return preparedWork().snapshotId();
    }

    public WorldBounds region() {
        return preparedWork().region();
    }

    public int hitCount() {
        return preparedWork().hitCount();
    }
}
