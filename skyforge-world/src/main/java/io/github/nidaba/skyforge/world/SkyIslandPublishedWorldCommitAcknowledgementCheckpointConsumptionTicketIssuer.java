package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0069 backend-neutral downstream I/O-coordination admission seam.
 *
 * <p>The issuer accepts only an AUTH-0068 prepared-consumption validation that is CURRENT. There is
 * no raw-preparation shortcut, hidden revalidation, refresh, retry, or actual I/O.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket issue(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                    validation,
            long ticketSequence) {
        Objects.requireNonNull(validation, "validation");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId.of(
                        ticketSequence,
                        validation.preparedConsumption());
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket(
                id,
                validation);
    }
}
