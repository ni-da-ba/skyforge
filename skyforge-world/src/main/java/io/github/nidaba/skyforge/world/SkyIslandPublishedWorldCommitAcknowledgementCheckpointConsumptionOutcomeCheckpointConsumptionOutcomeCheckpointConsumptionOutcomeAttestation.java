package io.github.nidaba.skyforge.world;

/**
 * AUTH-0082 backend-neutral seam for downstream audit/storage outcome evidence.
 *
 * <p>Concrete adapters implement this interface. Skyforge validates structural provenance only and
 * does not authenticate the backend-specific evidence token.
 */
public interface SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {
    int SCHEMA_VERSION = 1;

    int schemaVersion();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
            ticketId();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
            outcome();

    String evidenceToken();
}
