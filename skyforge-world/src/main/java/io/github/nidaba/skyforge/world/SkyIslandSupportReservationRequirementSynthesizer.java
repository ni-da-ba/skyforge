package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
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
 * AUTH-0054 synthesizes admission-safe reservation minima for one exact deterministic plan.
 *
 * <p>It never mutates the plan. Group outer radii are exact for the current member centers only and
 * must be recomputed after any fresh re-plan that changes placement.
 */
public final class SkyIslandSupportReservationRequirementSynthesizer {
    private final SkyIslandMorphologySpecSupportEnvelopeCompiler supportCompiler =
            new SkyIslandMorphologySpecSupportEnvelopeCompiler();

    public SkyIslandSupportReservationRequirementSynthesis synthesize(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");

        ArrayList<SkyIslandSupportReservationMemberRequirement> members =
                new ArrayList<>();
        ArrayList<SkyIslandSupportReservationGroupRequirement> groups =
                new ArrayList<>();

        boolean allCertified = true;
        double globalBelow = 0.0;
        double globalAbove = 0.0;

        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            ArrayList<SkyIslandSupportReservationMemberRequirement> groupMembers =
                    new ArrayList<>(group.groupPlan().memberCount());

            for (SkyIslandGroupMemberPlan member : group.groupPlan().members()) {
                Optional<CertifiedSkyIslandSupportEnvelope> support =
                        supportCompiler.certify(
                                member.descriptor(),
                                member.morphology(),
                                registry);
                SkyIslandSupportReservationMemberRequirement requirement =
                        memberRequirement(group, member, support);
                groupMembers.add(requirement);
                members.add(requirement);

                if (requirement.certified()) {
                    globalBelow =
                            Math.max(
                                    globalBelow,
                                    requirement.requiredBelowSuspension().orElseThrow());
                    globalAbove =
                            Math.max(
                                    globalAbove,
                                    requirement.requiredAboveSuspension().orElseThrow());
                } else {
                    allCertified = false;
                }
            }

            groups.add(groupRequirement(group, groupMembers));
        }

        return new SkyIslandSupportReservationRequirementSynthesis(
                plan.rootSeed(),
                members,
                groups,
                allCertified ? OptionalDouble.of(globalBelow) : OptionalDouble.empty(),
                allCertified ? OptionalDouble.of(globalAbove) : OptionalDouble.empty());
    }

    private static SkyIslandSupportReservationMemberRequirement memberRequirement(
            SkyIslandArchipelagoGroupPlan group,
            SkyIslandGroupMemberPlan member,
            Optional<CertifiedSkyIslandSupportEnvelope> support) {
        if (support.isEmpty()) {
            return new SkyIslandSupportReservationMemberRequirement(
                    group.ordinal(),
                    group.identifier(),
                    member.ordinal(),
                    member.descriptor().seed(),
                    member.morphology().stableIdentifier(),
                    member.reservedHorizontalRadius(),
                    Optional.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty());
        }

        CertifiedSkyIslandSupportEnvelope envelope = support.orElseThrow();
        SkyIslandVolumeDescriptor descriptor = member.descriptor();
        WorldBounds supportBounds = supportBounds(descriptor, envelope);

        double requiredHorizontal =
                maximum(
                        descriptor.centerX() - supportBounds.minimumX(),
                        supportBounds.maximumX() - descriptor.centerX(),
                        descriptor.centerZ() - supportBounds.minimumZ(),
                        supportBounds.maximumZ() - descriptor.centerZ());
        double requiredBelow =
                descriptor.suspensionElevation() - supportBounds.minimumY();
        double requiredAbove =
                supportBounds.maximumY() - descriptor.suspensionElevation();

        requiredHorizontal =
                ensureHorizontalAdmission(
                        descriptor, envelope, requiredHorizontal);
        requiredBelow =
                ensureBelowAdmission(descriptor, envelope, requiredBelow);
        requiredAbove =
                ensureAboveAdmission(descriptor, envelope, requiredAbove);

        SkyIslandSupportReservationMemberRequirement result =
                new SkyIslandSupportReservationMemberRequirement(
                        group.ordinal(),
                        group.identifier(),
                        member.ordinal(),
                        descriptor.seed(),
                        member.morphology().stableIdentifier(),
                        member.reservedHorizontalRadius(),
                        Optional.of(envelope),
                        OptionalDouble.of(requiredHorizontal),
                        OptionalDouble.of(requiredBelow),
                        OptionalDouble.of(requiredAbove));

        SkyIslandSupportReservationMemberCheck proof =
                new SkyIslandSupportReservationMemberCheck(
                        group.ordinal(),
                        group.identifier(),
                        member.ordinal(),
                        descriptor.seed(),
                        member.morphology().stableIdentifier(),
                        descriptor.centerX(),
                        descriptor.centerZ(),
                        descriptor.suspensionElevation(),
                        requiredHorizontal,
                        requiredBelow,
                        requiredAbove,
                        Optional.of(envelope));
        if (!proof.admitted()) {
            throw new IllegalStateException(
                    "AUTH-0054 synthesized member requirement does not satisfy AUTH-0053");
        }
        return result;
    }

    private static SkyIslandSupportReservationGroupRequirement groupRequirement(
            SkyIslandArchipelagoGroupPlan group,
            List<SkyIslandSupportReservationMemberRequirement> members) {
        boolean allCertified =
                members.stream()
                        .allMatch(SkyIslandSupportReservationMemberRequirement::certified);
        double currentMemberHorizontal = commonCurrentMemberHorizontal(members);

        if (!allCertified) {
            return new SkyIslandSupportReservationGroupRequirement(
                    group.ordinal(),
                    group.identifier(),
                    currentMemberHorizontal,
                    group.reservedGroupRadius(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    false);
        }

        double requiredMemberHorizontal = 0.0;
        double requiredGroupRadius = 0.0;
        for (int index = 0; index < members.size(); index++) {
            SkyIslandSupportReservationMemberRequirement requirement = members.get(index);
            SkyIslandGroupMemberPlan member =
                    group.groupPlan().members().get(index);
            if (member.ordinal() != requirement.memberOrdinal()) {
                throw new IllegalStateException(
                        "AUTH-0054 member requirement ordering diverged from plan");
            }
            double memberHorizontal =
                    requirement.requiredHorizontalRadius().orElseThrow();
            requiredMemberHorizontal = Math.max(requiredMemberHorizontal, memberHorizontal);

            double centerDistance =
                    Math.nextUp(
                            Math.hypot(
                                    member.descriptor().centerX()
                                            - group.groupPlan().groupCenterX(),
                                    member.descriptor().centerZ()
                                            - group.groupPlan().groupCenterZ()));
            double outer = Math.nextUp(centerDistance + memberHorizontal);
            requiredGroupRadius = Math.max(requiredGroupRadius, outer);
        }

        return new SkyIslandSupportReservationGroupRequirement(
                group.ordinal(),
                group.identifier(),
                currentMemberHorizontal,
                group.reservedGroupRadius(),
                OptionalDouble.of(requiredMemberHorizontal),
                OptionalDouble.of(Math.nextUp(requiredGroupRadius)),
                true);
    }

    private static double commonCurrentMemberHorizontal(
            List<SkyIslandSupportReservationMemberRequirement> members) {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("group must contain at least one member requirement");
        }
        double expected = members.get(0).currentReservedHorizontalRadius();
        for (SkyIslandSupportReservationMemberRequirement requirement : members) {
            if (Double.doubleToLongBits(requirement.currentReservedHorizontalRadius())
                    != Double.doubleToLongBits(expected)) {
                throw new IllegalStateException(
                        "AUTH-0054 requires one shared member horizontal reservation per group");
            }
        }
        return expected;
    }

    private static WorldBounds supportBounds(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope) {
        return new WorldBounds(
                Math.nextDown(
                        descriptor.centerX() - envelope.maximumHorizontalRadius()),
                Math.nextUp(
                        descriptor.centerX() + envelope.maximumHorizontalRadius()),
                Math.nextDown(
                        descriptor.suspensionElevation()
                                - envelope.maximumUndersideDepth()),
                Math.nextUp(
                        descriptor.suspensionElevation()
                                + envelope.maximumUpperOffset()),
                Math.nextDown(
                        descriptor.centerZ() - envelope.maximumHorizontalRadius()),
                Math.nextUp(
                        descriptor.centerZ() + envelope.maximumHorizontalRadius()));
    }

    private static double ensureHorizontalAdmission(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double candidate) {
        double worldStep =
                Math.max(Math.ulp(descriptor.centerX()), Math.ulp(descriptor.centerZ()));
        double result = candidate;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (horizontalAdmitted(descriptor, envelope, result)) {
                return result;
            }
            result = Math.nextUp(result + worldStep);
        }
        throw new IllegalStateException(
                "could not synthesize admission-safe horizontal reservation");
    }

    private static double ensureBelowAdmission(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double candidate) {
        double result = candidate;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (belowAdmitted(descriptor, envelope, result)) {
                return result;
            }
            result = Math.nextUp(result + Math.ulp(descriptor.suspensionElevation()));
        }
        throw new IllegalStateException(
                "could not synthesize admission-safe below-suspension reservation");
    }

    private static double ensureAboveAdmission(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double candidate) {
        double result = candidate;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (aboveAdmitted(descriptor, envelope, result)) {
                return result;
            }
            result = Math.nextUp(result + Math.ulp(descriptor.suspensionElevation()));
        }
        throw new IllegalStateException(
                "could not synthesize admission-safe above-suspension reservation");
    }

    private static boolean horizontalAdmitted(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double reservation) {
        SkyIslandSupportReservationMemberCheck check =
                syntheticCheck(descriptor, envelope, reservation, 1.0, 1.0);
        return check.horizontalReservationAdequate();
    }

    private static boolean belowAdmitted(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double reservation) {
        SkyIslandSupportReservationMemberCheck check =
                syntheticCheck(descriptor, envelope, 1.0, reservation, 1.0);
        return check.belowReservationAdequate();
    }

    private static boolean aboveAdmitted(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double reservation) {
        SkyIslandSupportReservationMemberCheck check =
                syntheticCheck(descriptor, envelope, 1.0, 1.0, reservation);
        return check.aboveReservationAdequate();
    }

    private static SkyIslandSupportReservationMemberCheck syntheticCheck(
            SkyIslandVolumeDescriptor descriptor,
            CertifiedSkyIslandSupportEnvelope envelope,
            double horizontal,
            double below,
            double above) {
        return new SkyIslandSupportReservationMemberCheck(
                0,
                "auth54-synthetic",
                0,
                descriptor.seed(),
                "auth54-synthetic",
                descriptor.centerX(),
                descriptor.centerZ(),
                descriptor.suspensionElevation(),
                horizontal,
                below,
                above,
                Optional.of(envelope));
    }

    private static double maximum(double first, double second, double third, double fourth) {
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }
}
