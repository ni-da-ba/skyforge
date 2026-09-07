package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * AUTH-0055 builds a reviewable immutable candidate re-plan request without executing that
 * candidate.
 *
 * <p>The original request is deterministically replayed only to verify that the supplied original
 * plan actually belongs to it. Candidate planning is deliberately outside this builder.
 */
public final class SkyIslandSupportReplanProposalBuilder {

    public SkyIslandSupportReplanProposal propose(
            SkyIslandArchipelagoRequest originalRequest,
            SkyIslandArchipelagoPlan originalPlan,
            SkyIslandSupportReservationRequirementSynthesis synthesis,
            SkyIslandWorldVerticalReservation originalVerticalReservation,
            SkyIslandSupportReplanMargin authorMargin) {
        Objects.requireNonNull(originalRequest, "originalRequest");
        Objects.requireNonNull(originalPlan, "originalPlan");
        Objects.requireNonNull(synthesis, "synthesis");
        Objects.requireNonNull(originalVerticalReservation, "originalVerticalReservation");
        Objects.requireNonNull(authorMargin, "authorMargin");

        validateOriginalRequestPlan(originalRequest, originalPlan);
        validateSynthesis(originalRequest, originalPlan, synthesis);

        ArrayList<SkyIslandSupportReplanGroupProposal> groupProposals =
                new ArrayList<>(originalRequest.groupCount());
        ArrayList<SkyIslandGroupTemplate> candidateTemplates =
                new ArrayList<>(originalRequest.groupCount());

        for (int ordinal = 0; ordinal < originalRequest.groupCount(); ordinal++) {
            SkyIslandGroupTemplate template = originalRequest.groupTemplates().get(ordinal);
            SkyIslandArchipelagoGroupPlan groupPlan = originalPlan.groups().get(ordinal);
            SkyIslandSupportReservationGroupRequirement requirement =
                    synthesis.groupRequirements().get(ordinal);

            OptionalDouble proofHorizontal = requirement.requiredMemberHorizontalRadius();
            SkyIslandSupportReplanValue memberHorizontal =
                    SkyIslandSupportReplanValue.propose(
                            template.reservedHorizontalRadius(),
                            proofHorizontal,
                            authorMargin.memberHorizontal());

            boolean pairwiseSpacingRelevant = template.memberMorphologies().size() > 1;
            OptionalDouble proofLayoutSpacing =
                    pairwiseSpacingRelevant && proofHorizontal.isPresent()
                            ? OptionalDouble.of(
                                    outwardPairSpacing(
                                            proofHorizontal.orElseThrow(),
                                            template.minimumMemberGap()))
                            : OptionalDouble.empty();
            double derivedLayoutMargin =
                    pairwiseSpacingRelevant && authorMargin.memberHorizontal() > 0.0
                            ? Math.nextUp(2.0 * authorMargin.memberHorizontal())
                            : 0.0;
            SkyIslandSupportReplanValue layoutSpacingBase =
                    SkyIslandSupportReplanValue.propose(
                            template.layout().minimumCenterSpacing(),
                            proofLayoutSpacing,
                            derivedLayoutMargin);
            double exactCandidateSpacing =
                    pairwiseSpacingRelevant
                            ? outwardPairSpacing(
                                    memberHorizontal.proposedValue(),
                                    template.minimumMemberGap())
                            : template.layout().minimumCenterSpacing();
            SkyIslandSupportReplanValue layoutSpacing =
                    new SkyIslandSupportReplanValue(
                            layoutSpacingBase.originalValue(),
                            layoutSpacingBase.proofMinimum(),
                            layoutSpacingBase.authorMargin(),
                            Math.max(
                                    layoutSpacingBase.proposedValue(),
                                    exactCandidateSpacing));

            SkyIslandGroupLayout proposedLayout =
                    raiseLayoutMinimumCenterSpacing(
                            template.layout(), layoutSpacing.proposedValue());

            OptionalDouble proofGroupRadius =
                    requirement.exactPlanRequiredGroupRadius();
            SkyIslandSupportReplanValue groupRadiusBase =
                    SkyIslandSupportReplanValue.propose(
                            template.reservedGroupRadius(),
                            proofGroupRadius,
                            authorMargin.groupRadius());

            double dependentCurrentLayoutFloor =
                    currentLayoutGroupFloor(
                            groupPlan,
                            memberHorizontal.proposedValue());
            double proposedGroupRadius =
                    Math.max(
                            Math.max(
                                    groupRadiusBase.proposedValue(),
                                    dependentCurrentLayoutFloor),
                            memberHorizontal.proposedValue());
            SkyIslandSupportReplanValue groupRadius =
                    new SkyIslandSupportReplanValue(
                            groupRadiusBase.originalValue(),
                            groupRadiusBase.proofMinimum(),
                            groupRadiusBase.authorMargin(),
                            proposedGroupRadius);

            SkyIslandGroupTemplate proposedTemplate =
                    new SkyIslandGroupTemplate(
                            template.identifier(),
                            template.role(),
                            template.memberTemplate(),
                            memberHorizontal.proposedValue(),
                            template.minimumMemberGap(),
                            template.memberElevationJitter(),
                            template.memberMorphologies(),
                            proposedLayout,
                            groupRadius.proposedValue());

            boolean freshPlacementValidationRequired =
                    memberHorizontal.raisedByProofOrMargin()
                            || layoutSpacing.raisedByProofOrMargin()
                            || groupRadius.raisedByProofOrMargin();

            groupProposals.add(
                    new SkyIslandSupportReplanGroupProposal(
                            ordinal,
                            template.identifier(),
                            template,
                            proposedTemplate,
                            memberHorizontal,
                            layoutSpacing,
                            dependentCurrentLayoutFloor,
                            groupRadius,
                            freshPlacementValidationRequired));
            candidateTemplates.add(proposedTemplate);
        }

        OptionalDouble proofBelow = synthesis.requiredBelowSuspension();
        OptionalDouble proofAbove = synthesis.requiredAboveSuspension();
        SkyIslandSupportReplanValue below =
                SkyIslandSupportReplanValue.propose(
                        originalVerticalReservation.belowSuspension(),
                        proofBelow,
                        authorMargin.belowSuspension());
        SkyIslandSupportReplanValue above =
                SkyIslandSupportReplanValue.propose(
                        originalVerticalReservation.aboveSuspension(),
                        proofAbove,
                        authorMargin.aboveSuspension());

        boolean complete = synthesis.fullySynthesized();
        Optional<SkyIslandArchipelagoRequest> candidateRequest =
                complete
                        ? Optional.of(
                                new SkyIslandArchipelagoRequest(
                                        originalRequest.rootSeed(),
                                        originalRequest.centerX(),
                                        originalRequest.centerZ(),
                                        originalRequest.baseSuspensionElevation(),
                                        originalRequest.minimumGroupGap(),
                                        candidateTemplates,
                                        originalRequest.layout()))
                        : Optional.empty();
        Optional<SkyIslandWorldVerticalReservation> candidateVertical =
                complete
                        ? Optional.of(
                                new SkyIslandWorldVerticalReservation(
                                        below.proposedValue(),
                                        above.proposedValue()))
                        : Optional.empty();

        return new SkyIslandSupportReplanProposal(
                originalRequest,
                originalPlan,
                synthesis,
                originalVerticalReservation,
                authorMargin,
                groupProposals,
                below,
                above,
                candidateRequest,
                candidateVertical,
                synthesis.uncertifiedMemberCount());
    }

    private static void validateOriginalRequestPlan(
            SkyIslandArchipelagoRequest request,
            SkyIslandArchipelagoPlan plan) {
        SkyIslandArchipelagoPlan reproduced =
                new SkyIslandArchipelagoPlanner().plan(request);
        if (!reproduced.equals(plan)) {
            throw new IllegalArgumentException(
                    "AUTH-0055 original plan does not exactly reproduce from original request");
        }
    }

    private static void validateSynthesis(
            SkyIslandArchipelagoRequest request,
            SkyIslandArchipelagoPlan plan,
            SkyIslandSupportReservationRequirementSynthesis synthesis) {
        if (synthesis.archipelagoRootSeed() != plan.rootSeed()
                || request.rootSeed() != plan.rootSeed()) {
            throw new IllegalArgumentException(
                    "AUTH-0055 request/plan/synthesis root seeds differ");
        }
        if (synthesis.groupRequirements().size() != plan.groupCount()
                || request.groupCount() != plan.groupCount()) {
            throw new IllegalArgumentException(
                    "AUTH-0055 request/plan/synthesis group counts differ");
        }

        int memberIndex = 0;
        for (int groupOrdinal = 0; groupOrdinal < plan.groupCount(); groupOrdinal++) {
            SkyIslandGroupTemplate template = request.groupTemplates().get(groupOrdinal);
            SkyIslandArchipelagoGroupPlan groupPlan = plan.groups().get(groupOrdinal);
            SkyIslandSupportReservationGroupRequirement groupRequirement =
                    synthesis.groupRequirements().get(groupOrdinal);

            if (groupPlan.ordinal() != groupOrdinal
                    || groupRequirement.groupOrdinal() != groupOrdinal
                    || !template.identifier().equals(groupPlan.identifier())
                    || !template.identifier().equals(groupRequirement.groupIdentifier())
                    || Double.doubleToLongBits(template.reservedHorizontalRadius())
                            != Double.doubleToLongBits(
                                    groupRequirement.currentMemberHorizontalRadius())
                    || Double.doubleToLongBits(template.reservedGroupRadius())
                            != Double.doubleToLongBits(
                                    groupRequirement.currentReservedGroupRadius())) {
                throw new IllegalArgumentException(
                        "AUTH-0055 synthesis group provenance differs from original request/plan");
            }

            for (var member : groupPlan.groupPlan().members()) {
                if (memberIndex >= synthesis.memberRequirements().size()) {
                    throw new IllegalArgumentException(
                            "AUTH-0055 synthesis member count is smaller than plan");
                }
                SkyIslandSupportReservationMemberRequirement memberRequirement =
                        synthesis.memberRequirements().get(memberIndex++);
                if (memberRequirement.groupOrdinal() != groupOrdinal
                        || memberRequirement.memberOrdinal() != member.ordinal()
                        || !memberRequirement.groupIdentifier().equals(groupPlan.identifier())
                        || memberRequirement.descriptorSeed() != member.descriptor().seed()
                        || !memberRequirement.morphologyIdentifier()
                                .equals(member.morphology().stableIdentifier())
                        || Double.doubleToLongBits(
                                        memberRequirement.currentReservedHorizontalRadius())
                                != Double.doubleToLongBits(
                                        member.reservedHorizontalRadius())) {
                    throw new IllegalArgumentException(
                            "AUTH-0055 synthesis member provenance differs from original plan");
                }
            }
        }
        if (memberIndex != synthesis.memberRequirements().size()) {
            throw new IllegalArgumentException(
                    "AUTH-0055 synthesis member count is larger than plan");
        }
    }

    private static double outwardPairSpacing(double horizontalRadius, double minimumGap) {
        return Math.nextUp(Math.nextUp(2.0 * horizontalRadius) + minimumGap);
    }

    private static SkyIslandGroupLayout raiseLayoutMinimumCenterSpacing(
            SkyIslandGroupLayout layout, double minimumCenterSpacing) {
        if (layout instanceof SkyIslandGroupLayout.Cluster cluster) {
            double target = Math.max(cluster.minimumCenterSpacing(), minimumCenterSpacing);
            return new SkyIslandGroupLayout.Cluster(
                    target,
                    cluster.phaseRadians(),
                    cluster.radialJitterFraction(),
                    cluster.orientationJitterRadians());
        }

        SkyIslandGroupLayout.Chain chain = (SkyIslandGroupLayout.Chain) layout;
        if (chain.minimumCenterSpacing() >= minimumCenterSpacing) {
            return chain;
        }

        double retainedFraction = 1.0 - chain.spacingJitterFraction();
        double centerSpacing = minimumCenterSpacing / retainedFraction;
        while (centerSpacing * retainedFraction < minimumCenterSpacing) {
            centerSpacing = Math.nextUp(centerSpacing);
        }
        return new SkyIslandGroupLayout.Chain(
                chain.headingRadians(),
                centerSpacing,
                chain.spacingJitterFraction(),
                chain.lateralJitter(),
                chain.curveAmplitude(),
                chain.orientationJitterRadians());
    }

    private static double currentLayoutGroupFloor(
            SkyIslandArchipelagoGroupPlan group,
            double proposedMemberHorizontalRadius) {
        double maximum = proposedMemberHorizontalRadius;
        for (var member : group.groupPlan().members()) {
            double distance =
                    Math.nextUp(
                            Math.hypot(
                                    member.descriptor().centerX()
                                            - group.groupPlan().groupCenterX(),
                                    member.descriptor().centerZ()
                                            - group.groupPlan().groupCenterZ()));
            maximum =
                    Math.max(
                            maximum,
                            Math.nextUp(distance + proposedMemberHorizontalRadius));
        }
        return Math.nextUp(maximum);
    }
}
