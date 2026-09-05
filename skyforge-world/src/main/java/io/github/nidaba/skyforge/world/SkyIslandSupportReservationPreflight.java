package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecSupportEnvelopeCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * AUTH-0053 seed-aware support-reservation preflight for one exact deterministic archipelago plan.
 *
 * <p>The plan already contains the derived member seeds/descriptors used by arbitrary provider
 * support certificates. This auditor never enlarges a reservation or moves a member. It only
 * determines whether the reservations already consumed by planning remain valid.
 */
public final class SkyIslandSupportReservationPreflight {
    private final SkyIslandMorphologySpecSupportEnvelopeCompiler supportCompiler =
            new SkyIslandMorphologySpecSupportEnvelopeCompiler();

    public SkyIslandSupportReservationPreflightReport evaluate(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(verticalReservation, "verticalReservation");

        ArrayList<SkyIslandSupportReservationMemberCheck> members = new ArrayList<>();
        ArrayList<SkyIslandSupportReservationGroupCheck> groups = new ArrayList<>();

        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            List<SkyIslandSupportReservationMemberCheck> groupMembers =
                    evaluateGroupMembers(group, registry, verticalReservation);
            members.addAll(groupMembers);

            boolean allCertified =
                    groupMembers.stream()
                            .allMatch(SkyIslandSupportReservationMemberCheck::certified);
            boolean allMemberReservationsAdequate =
                    groupMembers.stream()
                            .allMatch(SkyIslandSupportReservationMemberCheck::admitted);

            OptionalDouble requiredGroupRadius =
                    allCertified
                            ? OptionalDouble.of(requiredGroupRadius(group, groupMembers))
                            : OptionalDouble.empty();

            groups.add(
                    new SkyIslandSupportReservationGroupCheck(
                            group.ordinal(),
                            group.identifier(),
                            group.reservedGroupRadius(),
                            requiredGroupRadius,
                            allCertified,
                            allMemberReservationsAdequate));
        }

        return new SkyIslandSupportReservationPreflightReport(
                plan.rootSeed(),
                verticalReservation,
                members,
                groups);
    }

    private List<SkyIslandSupportReservationMemberCheck> evaluateGroupMembers(
            SkyIslandArchipelagoGroupPlan group,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        ArrayList<SkyIslandSupportReservationMemberCheck> result =
                new ArrayList<>(group.groupPlan().memberCount());

        for (SkyIslandGroupMemberPlan member : group.groupPlan().members()) {
            Optional<CertifiedSkyIslandSupportEnvelope> support =
                    supportCompiler.certify(
                            member.descriptor(),
                            member.morphology(),
                            registry);
            result.add(
                    new SkyIslandSupportReservationMemberCheck(
                            group.ordinal(),
                            group.identifier(),
                            member.ordinal(),
                            member.descriptor().seed(),
                            member.morphology().stableIdentifier(),
                            member.reservedHorizontalRadius(),
                            verticalReservation.belowSuspension(),
                            verticalReservation.aboveSuspension(),
                            support));
        }
        return List.copyOf(result);
    }

    private static double requiredGroupRadius(
            SkyIslandArchipelagoGroupPlan group,
            List<SkyIslandSupportReservationMemberCheck> checks) {
        double maximum = 0.0;
        for (int index = 0; index < checks.size(); index++) {
            SkyIslandGroupMemberPlan member =
                    group.groupPlan().members().get(index);
            SkyIslandSupportReservationMemberCheck check = checks.get(index);
            if (member.ordinal() != check.memberOrdinal()) {
                throw new IllegalStateException(
                        "AUTH-0053 group/member check ordering diverged");
            }
            double distance =
                    Math.nextUp(
                            Math.hypot(
                                    member.descriptor().centerX()
                                            - group.groupPlan().groupCenterX(),
                                    member.descriptor().centerZ()
                                            - group.groupPlan().groupCenterZ()));
            double required =
                    Math.nextUp(distance + check.requiredHorizontalRadius());
            maximum = Math.max(maximum, required);
        }
        return Math.nextUp(maximum);
    }
}
