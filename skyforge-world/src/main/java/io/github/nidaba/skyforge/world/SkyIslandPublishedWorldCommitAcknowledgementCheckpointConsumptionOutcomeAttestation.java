package io.github.nidaba.skyforge.world;

/**
 * AUTH-0070 backend-neutral seam for persistence/replication outcome evidence supplied by a
 * downstream I/O coordinator.
 *
 * <p>Skyforge does not provide a success-attestation factory. A concrete adapter owns the trust
 * model behind this evidence.
 */
public interface SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation {
    int SCHEMA_VERSION = 1;

    int schemaVersion();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId ticketId();

    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome outcome();

    /**
     * Stable backend-owned evidence reference/token.
     *
     * <p>AUTH-0070 requires this to be nonblank but deliberately does not interpret or authenticate
     * its backend-specific meaning.
     */
    String evidenceToken();
}
