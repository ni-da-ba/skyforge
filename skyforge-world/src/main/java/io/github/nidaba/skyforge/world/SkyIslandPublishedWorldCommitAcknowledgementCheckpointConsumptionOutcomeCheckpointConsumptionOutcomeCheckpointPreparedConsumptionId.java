package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0080 identity for one prepared downstream consumption of one exact AUTH-0078 outcome
 * checkpoint by one exact target.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId(
        int schemaVersion,
        long preparationSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                checkpointId,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                targetId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported prepared outcome-checkpoint-consumption outcome-checkpoint consumption identity schema: "
                            + schemaVersion);
        }
        if (preparationSequence <= 0) {
            throw new IllegalArgumentException("preparationSequence must be positive");
        }
        checkpointId = Objects.requireNonNull(checkpointId, "checkpointId");
        targetId = Objects.requireNonNull(targetId, "targetId");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                    of(
                            long preparationSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                                    binding,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                    targetId) {
        Objects.requireNonNull(binding, "binding");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId(
                SCHEMA_VERSION,
                preparationSequence,
                binding.checkpointId(),
                targetId);
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutcpoutprep:v%d:%016x:%s:%s",
                schemaVersion,
                preparationSequence,
                checkpointId.canonicalToken(),
                targetId.canonicalToken());
    }
}
