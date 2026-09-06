package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0075 backend-neutral audit/storage-coordination admission seam.
 *
 * <p>The issuer accepts only an AUTH-0074 prepared-consumption validation that is CURRENT. There is
 * no raw-preparation shortcut, hidden revalidation, refresh, retry, retargeting, or actual I/O.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
            issue(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                            validation,
                    long ticketSequence) {
        Objects.requireNonNull(validation, "validation");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                        .of(ticketSequence, validation.preparedConsumption());
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
                id,
                validation);
    }
}
