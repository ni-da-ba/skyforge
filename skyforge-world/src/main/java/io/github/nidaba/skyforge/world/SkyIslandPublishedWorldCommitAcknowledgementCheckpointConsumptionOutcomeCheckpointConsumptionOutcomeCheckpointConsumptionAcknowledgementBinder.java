package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0082 structural outcome binder.
 *
 * <p>The binder never creates an attestation. It can only bind externally supplied evidence to the
 * exact AUTH-0081 ticket it names.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
            bind(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                            ticket,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                            attestation,
                    long acknowledgementSequence) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(attestation, "attestation");

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                        .of(acknowledgementSequence, ticket);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
                id,
                ticket,
                attestation);
    }
}
