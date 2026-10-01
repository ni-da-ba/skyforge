package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0081 backend-neutral audit/storage-coordination admission seam.
 *
 * <p>The issuer accepts only an AUTH-0080 prepared-consumption validation that is CURRENT. There is
 * no raw-preparation shortcut, hidden revalidation, refresh, retry, retargeting, or actual I/O.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
            issue(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                            validation,
                    long ticketSequence) {
        Objects.requireNonNull(validation, "validation");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                        .of(ticketSequence, validation.preparedConsumption());
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
                id,
                validation);
    }
}
