package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** AUTH-0053 immutable seed-aware support-reservation admission report. */
public record SkyIslandSupportReservationPreflightReport(
        long archipelagoRootSeed,
        SkyIslandWorldVerticalReservation verticalReservation,
        List<SkyIslandSupportReservationMemberCheck> memberChecks,
        List<SkyIslandSupportReservationGroupCheck> groupChecks) {

    public SkyIslandSupportReservationPreflightReport {
        verticalReservation =
                Objects.requireNonNull(verticalReservation, "verticalReservation");
        memberChecks = List.copyOf(Objects.requireNonNull(memberChecks, "memberChecks"));
        groupChecks = List.copyOf(Objects.requireNonNull(groupChecks, "groupChecks"));
        for (SkyIslandSupportReservationMemberCheck check : memberChecks) {
            Objects.requireNonNull(check, "member check");
        }
        for (SkyIslandSupportReservationGroupCheck check : groupChecks) {
            Objects.requireNonNull(check, "group check");
        }
    }

    /** True only when every exact planned member and group reservation remains proof-valid. */
    public boolean admitted() {
        return !memberChecks.isEmpty()
                && memberChecks.stream().allMatch(SkyIslandSupportReservationMemberCheck::admitted)
                && groupChecks.stream().allMatch(SkyIslandSupportReservationGroupCheck::admitted);
    }

    public long uncertifiedMemberCount() {
        return memberChecks.stream().filter(check -> !check.certified()).count();
    }

    public long undersizedMemberHorizontalCount() {
        return memberChecks.stream()
                .filter(check -> check.certified() && !check.horizontalReservationAdequate())
                .count();
    }

    public long undersizedVerticalCount() {
        return memberChecks.stream()
                .filter(check -> check.certified() && !check.verticalReservationAdequate())
                .count();
    }

    public long undersizedGroupCount() {
        return groupChecks.stream()
                .filter(
                        check ->
                                check.requiredGroupRadius().isPresent()
                                        && !check.groupReservationAdequate())
                .count();
    }

    /**
     * Whether at least one planning/query reservation was already too small for certified support.
     *
     * <p>Uncertified members are tracked separately because their required envelope is unknown.
     */
    public boolean consumedReservationDefect() {
        return undersizedMemberHorizontalCount() > 0
                || undersizedVerticalCount() > 0
                || undersizedGroupCount() > 0;
    }

    public void requireAdmitted() {
        if (!admitted()) {
            throw new IllegalStateException(
                    "AUTH-0053 support reservation preflight rejected plan: uncertified="
                            + uncertifiedMemberCount()
                            + ", undersizedMemberHorizontal="
                            + undersizedMemberHorizontalCount()
                            + ", undersizedVertical="
                            + undersizedVerticalCount()
                            + ", undersizedGroup="
                            + undersizedGroupCount());
        }
    }
}
