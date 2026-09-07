package io.github.nidaba.skyforge.world;

/**
 * AUTH-0076 backend-neutral seam for downstream audit/storage outcome evidence.
 *
 * <p>Concrete adapters implement this interface. Skyforge validates structural provenance only and
 * does not authenticate the backend-specific evidence token.
 */
public interface SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {
    int SCHEMA_VERSION = 1;

    int schemaVersion();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
            ticketId();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
            outcome();

    String evidenceToken();
}
