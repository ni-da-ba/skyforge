package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0076 structural outcome binder.
 *
 * <p>The binder never creates an attestation. It can only bind externally supplied evidence to the
 * exact AUTH-0075 ticket it names.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
            bind(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                            ticket,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                            attestation,
                    long acknowledgementSequence) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(attestation, "attestation");

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                        .of(acknowledgementSequence, ticket);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
                id,
                ticket,
                attestation);
    }
}
