package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0074 identity for one prepared downstream consumption of one exact AUTH-0072 outcome
 * checkpoint by one exact target.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId(
        int schemaVersion,
        long preparationSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
                checkpointId,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                targetId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported prepared outcome-checkpoint consumption identity schema: "
                            + schemaVersion);
        }
        if (preparationSequence <= 0) {
            throw new IllegalArgumentException("preparationSequence must be positive");
        }
        checkpointId = Objects.requireNonNull(checkpointId, "checkpointId");
        targetId = Objects.requireNonNull(targetId, "targetId");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                    of(
                            long preparationSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                                    binding,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                    targetId) {
        Objects.requireNonNull(binding, "binding");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId(
                SCHEMA_VERSION,
                preparationSequence,
                binding.checkpointId(),
                targetId);
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutprep:v%d:%016x:%s:%s",
                schemaVersion,
                preparationSequence,
                checkpointId.canonicalToken(),
                targetId.canonicalToken());
    }
}
