package io.github.nidaba.skyforge.world;

/**
 * AUTH-0064 backend-neutral seam for outcome evidence supplied by a downstream commit coordinator.
 *
 * <p>Skyforge world/authorship code does not provide a success-attestation factory. A concrete
 * backend owns the trust model behind an implementation and its evidence token.
 */
public interface SkyIslandPublishedWorldCommitOutcomeAttestation {
    int SCHEMA_VERSION = 1;

    int schemaVersion();

    SkyIslandPublishedWorldCommitTicketId ticketId();

    SkyIslandPublishedWorldCommitOutcome outcome();

    /**
     * Stable backend-owned evidence reference/token.
     *
     * <p>AUTH-0064 requires this to be nonblank but deliberately does not interpret or authenticate
     * its backend-specific meaning.
     */
    String evidenceToken();
}
