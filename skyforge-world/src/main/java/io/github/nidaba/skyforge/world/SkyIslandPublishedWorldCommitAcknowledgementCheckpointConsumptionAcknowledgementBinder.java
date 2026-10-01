package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0070 structural outcome-acknowledgement binder.
 *
 * <p>The binder never creates an outcome attestation. It can only bind an externally supplied
 * attestation to the exact AUTH-0069 ticket it names.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement bind(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation
                    attestation,
            long acknowledgementSequence) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(attestation, "attestation");

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId
                        .of(acknowledgementSequence, ticket);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement(
                id,
                ticket,
                attestation);
    }
}
