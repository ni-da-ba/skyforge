package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0068 identity for one prepared downstream consumption of one exact checkpoint by one exact
 * target.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId(
        int schemaVersion,
        long preparationSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointId checkpointId,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported prepared checkpoint-consumption identity schema: " + schemaVersion);
        }
        if (preparationSequence <= 0) {
            throw new IllegalArgumentException("preparationSequence must be positive");
        }
        checkpointId = Objects.requireNonNull(checkpointId, "checkpointId");
        targetId = Objects.requireNonNull(targetId, "targetId");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId of(
            long preparationSequence,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding binding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId) {
        Objects.requireNonNull(binding, "binding");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId(
                SCHEMA_VERSION,
                preparationSequence,
                binding.checkpointId(),
                targetId);
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpprep:v%d:%016x:%s:%s",
                schemaVersion,
                preparationSequence,
                checkpointId.canonicalToken(),
                targetId.canonicalToken());
    }
}
