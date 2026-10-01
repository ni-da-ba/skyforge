package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** D2-accepted reach intentionally withheld from terrain authority until transition ownership exists. */
public record SkyIslandQualifiedFluvialDeferral(
        SkyIslandGeomorphicReachQualification qualification,
        List<SkyIslandQualifiedFluvialDeferralReason> reasons) {

    public SkyIslandQualifiedFluvialDeferral {
        qualification = Objects.requireNonNull(qualification, "qualification");
        if (!qualification.accepted()) {
            throw new IllegalArgumentException("only D2-accepted reaches may be realization-deferred");
        }
        reasons = List.copyOf(reasons);
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("realization deferral requires at least one reason");
        }
        reasons.forEach(reason -> Objects.requireNonNull(reason, "reason"));
    }
}
