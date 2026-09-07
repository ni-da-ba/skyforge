package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0064 structural acknowledgement binder.
 *
 * <p>The binder never creates an outcome attestation. It can only bind an externally supplied
 * attestation to the exact AUTH-0063 ticket it names.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementBinder {

    public SkyIslandPublishedWorldCommitAcknowledgement bind(
            SkyIslandPublishedWorldCommitTicket ticket,
            SkyIslandPublishedWorldCommitOutcomeAttestation attestation,
            long acknowledgementSequence) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(attestation, "attestation");

        SkyIslandPublishedWorldCommitAcknowledgementId id =
                SkyIslandPublishedWorldCommitAcknowledgementId.of(
                        acknowledgementSequence,
                        ticket);
        return new SkyIslandPublishedWorldCommitAcknowledgement(id, ticket, attestation);
    }
}
