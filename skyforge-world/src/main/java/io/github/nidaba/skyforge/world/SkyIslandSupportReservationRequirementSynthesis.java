package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** AUTH-0054 immutable exact-plan reservation requirement synthesis. */
public record SkyIslandSupportReservationRequirementSynthesis(
        long archipelagoRootSeed,
        List<SkyIslandSupportReservationMemberRequirement> memberRequirements,
        List<SkyIslandSupportReservationGroupRequirement> groupRequirements,
        OptionalDouble requiredBelowSuspension,
        OptionalDouble requiredAboveSuspension) {

    public SkyIslandSupportReservationRequirementSynthesis {
        memberRequirements =
                List.copyOf(
                        Objects.requireNonNull(memberRequirements, "memberRequirements"));
        groupRequirements =
                List.copyOf(
                        Objects.requireNonNull(groupRequirements, "groupRequirements"));
        requiredBelowSuspension =
                Objects.requireNonNull(requiredBelowSuspension, "requiredBelowSuspension");
        requiredAboveSuspension =
                Objects.requireNonNull(requiredAboveSuspension, "requiredAboveSuspension");
        boolean complete =
                !memberRequirements.isEmpty()
                        && memberRequirements.stream()
                                .allMatch(SkyIslandSupportReservationMemberRequirement::certified);
        if (requiredBelowSuspension.isPresent() != complete
                || requiredAboveSuspension.isPresent() != complete) {
            throw new IllegalArgumentException(
                    "global vertical requirements are present exactly when synthesis is complete");
        }
    }

    public boolean fullySynthesized() {
        return !memberRequirements.isEmpty()
                && memberRequirements.stream()
                        .allMatch(SkyIslandSupportReservationMemberRequirement::certified)
                && groupRequirements.stream()
                        .allMatch(SkyIslandSupportReservationGroupRequirement::allMembersCertified);
    }

    public long uncertifiedMemberCount() {
        return memberRequirements.stream().filter(requirement -> !requirement.certified()).count();
    }

    /**
     * Whether the current plan must be regenerated to apply synthesized horizontal/group minima.
     */
    public boolean requiresFreshReplan() {
        return groupRequirements.stream()
                .anyMatch(SkyIslandSupportReservationGroupRequirement::requiresFreshReplan);
    }

    /**
     * Returns the synthesized global vertical reservation when every member is certified.
     *
     * <p>Unlike horizontal/group spacing, vertical query reservation does not itself determine
     * member placement; callers still choose whether to construct a new world compilation request.
     */
    public Optional<SkyIslandWorldVerticalReservation> synthesizedVerticalReservation() {
        if (!fullySynthesized()) {
            return Optional.empty();
        }
        return Optional.of(
                new SkyIslandWorldVerticalReservation(
                        requiredBelowSuspension.orElseThrow(),
                        requiredAboveSuspension.orElseThrow()));
    }
}
