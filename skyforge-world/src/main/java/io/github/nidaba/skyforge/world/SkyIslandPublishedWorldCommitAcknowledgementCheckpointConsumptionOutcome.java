package io.github.nidaba.skyforge.world;

/**
 * AUTH-0070 outcome classes attested by a downstream checkpoint-consumption I/O coordinator.
 *
 * <p>These values report downstream persistence/replication outcome only; the authorship layer
 * does not infer them from admission.
 */
public enum SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome {
    SUCCEEDED,
    FAILED
}
