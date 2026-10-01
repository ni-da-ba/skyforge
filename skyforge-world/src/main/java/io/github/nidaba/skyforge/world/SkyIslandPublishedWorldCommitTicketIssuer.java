package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0063 backend-neutral commit-ticket issuance seam.
 *
 * <p>The only issuance input is an AUTH-0062 prepared-work validation that is CURRENT. There is no
 * raw-work overload and no hidden revalidation, refresh, retry, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitTicketIssuer {

    public SkyIslandPublishedWorldCommitTicket issue(
            SkyIslandPublishedWorldPreparedWorkValidation validation,
            long ticketSequence) {
        Objects.requireNonNull(validation, "validation");
        validation.requireCurrent();

        SkyIslandPublishedWorldCommitTicketId id =
                SkyIslandPublishedWorldCommitTicketId.of(
                        ticketSequence,
                        validation.preparedWork());
        return new SkyIslandPublishedWorldCommitTicket(id, validation);
    }
}
