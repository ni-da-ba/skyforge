package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandSupportReplanProposalBuilderTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void adequateZeroMarginProposalPreservesOriginalRequestExactly() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55001L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);

        assertTrue(proposal.complete());
        assertEquals(request, proposal.candidateRequest().orElseThrow());
        assertEquals(
                ADEQUATE_VERTICAL,
                proposal.candidateVerticalReservation().orElseThrow());
        assertFalse(proposal.changesHorizontalPlanning());
        assertFalse(proposal.changesVerticalReservation());
        assertFalse(proposal.freshReplanRequired());
        assertEquals(
                request.groupTemplates().get(0).layout().minimumCenterSpacing(),
                proposal.groupProposals().get(0).layoutMinimumCenterSpacing().proposedValue(),
                0.0);
    }

    @Test
    void proofDrivenHorizontalIncreaseRaisesMemberAndPairwiseLayoutSpacing() {
        SkyIslandArchipelagoRequest request =
                twoMemberRequest(
                        55002L,
                        direct(MorphologyFamily.MASSIF),
                        120.0,
                        280.0,
                        260.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);

        var group = proposal.groupProposals().get(0);
        assertTrue(proposal.complete());
        assertTrue(group.memberHorizontal().raisedByProof());
        assertTrue(group.memberHorizontal().proposedValue() > 120.0);
        assertTrue(group.layoutMinimumCenterSpacing().raisedByProof());
        assertTrue(
                group.layoutMinimumCenterSpacing().proposedValue()
                        >= 2.0 * group.memberHorizontal().proposedValue()
                                + request.groupTemplates().get(0).minimumMemberGap());
        assertTrue(
                group.provisionalGroupRadius().proposedValue()
                        >= group.dependentCurrentLayoutGroupFloor());
        assertTrue(group.freshPlacementValidationRequired());
        assertTrue(proposal.freshReplanRequired());

        SkyIslandArchipelagoRequest candidate =
                proposal.candidateRequest().orElseThrow();
        assertEquals(
                group.memberHorizontal().proposedValue(),
                candidate.groupTemplates().get(0).reservedHorizontalRadius(),
                0.0);
        assertEquals(
                group.layoutMinimumCenterSpacing().proposedValue(),
                candidate.groupTemplates().get(0).layout().minimumCenterSpacing(),
                0.0);
    }

    @Test
    void authorMarginsRemainExplicitlySeparateFromProofMinimums() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55003L,
                        direct(MorphologyFamily.TABLELAND),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());
        var margin = new SkyIslandSupportReplanMargin(10.0, 20.0, 5.0, 7.0);

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                margin);

        var group = proposal.groupProposals().get(0);
        assertEquals(10.0, group.memberHorizontal().authorMargin(), 0.0);
        assertTrue(group.memberHorizontal().proofMinimum().isPresent());
        assertEquals(20.0, group.provisionalGroupRadius().authorMargin(), 0.0);
        assertEquals(5.0, proposal.belowSuspension().authorMargin(), 0.0);
        assertEquals(7.0, proposal.aboveSuspension().authorMargin(), 0.0);
        assertTrue(group.memberHorizontal().raisedByAuthorMargin());
        assertTrue(group.provisionalGroupRadius().raisedByAuthorMargin());
        assertTrue(proposal.belowSuspension().raisedByAuthorMargin());
        assertTrue(proposal.aboveSuspension().raisedByAuthorMargin());
        assertTrue(
                group.memberHorizontal().proposedValue()
                        > Math.max(
                                group.memberHorizontal().originalValue(),
                                group.memberHorizontal().proofMinimum().orElseThrow()));
    }

    @Test
    void verticalOnlyProposalLeavesArchipelagoRequestUnchanged() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55004L,
                        direct(MorphologyFamily.SPINE),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());
        var originalVertical = new SkyIslandWorldVerticalReservation(180.0, 140.0);

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                originalVertical,
                                SkyIslandSupportReplanMargin.ZERO);

        assertEquals(request, proposal.candidateRequest().orElseThrow());
        assertFalse(proposal.changesHorizontalPlanning());
        assertTrue(proposal.changesVerticalReservation());
        assertFalse(proposal.freshReplanRequired());
        assertTrue(
                proposal.candidateVerticalReservation().orElseThrow().belowSuspension()
                        > originalVertical.belowSuspension());
    }

    @Test
    void uncertifiedSynthesisProducesReviewableIncompleteProposalWithoutCandidate() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55005L,
                        ProviderBlendMorphologySpec.full(
                                new MorphologyProviderBlend(massif, basin, 0.35)),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                new SkyIslandSupportReplanMargin(10.0, 10.0, 5.0, 5.0));

        assertFalse(proposal.complete());
        assertEquals(1, proposal.uncertifiedMemberCount());
        assertTrue(proposal.candidateRequest().isEmpty());
        assertTrue(proposal.candidateVerticalReservation().isEmpty());
        assertTrue(
                proposal.groupProposals().get(0).memberHorizontal().proofMinimum().isEmpty());
        assertTrue(proposal.belowSuspension().proofMinimum().isEmpty());
        assertThrows(IllegalStateException.class, proposal::requireComplete);
    }

    @Test
    void mismatchedOriginalRequestAndPlanAreRejected() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55006L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandArchipelagoRequest otherRequest =
                singleRequest(
                        55007L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(otherRequest);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandSupportReplanProposalBuilder()
                                .propose(
                                        request,
                                        plan,
                                        synthesis,
                                        ADEQUATE_VERTICAL,
                                        SkyIslandSupportReplanMargin.ZERO));
    }

    @Test
    void mismatchedSynthesisIsRejectedEvenWhenRequestAndPlanMatch() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55008L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandArchipelagoRequest otherRequest =
                singleRequest(
                        55009L,
                        direct(MorphologyFamily.MASSIF),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan otherPlan =
                new SkyIslandArchipelagoPlanner().plan(otherRequest);
        var wrongSynthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                otherPlan,
                                SkyIslandMorphologyProviders.builtInRegistry());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandSupportReplanProposalBuilder()
                                .propose(
                                        request,
                                        plan,
                                        wrongSynthesis,
                                        ADEQUATE_VERTICAL,
                                        SkyIslandSupportReplanMargin.ZERO));
    }

    @Test
    void proposalConstructionDoesNotExecuteCandidateReplan() {
        SkyIslandArchipelagoRequest request =
                twoMemberRequest(
                        55010L,
                        direct(MorphologyFamily.MASSIF),
                        120.0,
                        280.0,
                        260.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);

        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);

        assertTrue(proposal.complete());
        SkyIslandArchipelagoRequest candidate =
                proposal.candidateRequest().orElseThrow();

        assertThrows(
                IllegalStateException.class,
                () -> new SkyIslandArchipelagoPlanner().plan(candidate));
    }

    @Test
    void successfulFreshCandidateMustBeResynthesizedRatherThanReuseOldGroupProof() {
        SkyIslandArchipelagoRequest request =
                twoMemberRequest(
                        55011L,
                        direct(MorphologyFamily.MASSIF),
                        120.0,
                        280.0,
                        260.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var oldSynthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                plan,
                                oldSynthesis,
                                ADEQUATE_VERTICAL,
                                new SkyIslandSupportReplanMargin(0.0, 400.0, 0.0, 0.0));
        SkyIslandArchipelagoRequest candidate =
                proposal.candidateRequest().orElseThrow();

        SkyIslandArchipelagoPlan fresh =
                new SkyIslandArchipelagoPlanner().plan(candidate);
        var freshSynthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(fresh, registry);

        assertNotEquals(plan, fresh);
        assertNotEquals(
                oldSynthesis
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow(),
                freshSynthesis
                        .groupRequirements()
                        .get(0)
                        .exactPlanRequiredGroupRadius()
                        .orElseThrow());
        assertTrue(
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                fresh,
                                registry,
                                proposal.candidateVerticalReservation().orElseThrow())
                        .admitted());
    }

    @Test
    void repeatedProposalConstructionIsDeterministic() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        55012L,
                        direct(MorphologyFamily.BASIN),
                        360.0,
                        440.0);
        SkyIslandArchipelagoPlan plan =
                new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry());
        var builder = new SkyIslandSupportReplanProposalBuilder();
        var margin = new SkyIslandSupportReplanMargin(3.0, 4.0, 5.0, 6.0);

        assertEquals(
                builder.propose(request, plan, synthesis, ADEQUATE_VERTICAL, margin),
                builder.propose(request, plan, synthesis, ADEQUATE_VERTICAL, margin));
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
    }

    private static SkyIslandArchipelagoRequest singleRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup) {
        return request(
                rootSeed,
                List.of(morphology),
                reservedHorizontal,
                reservedGroup,
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                0.0);
    }

    private static SkyIslandArchipelagoRequest twoMemberRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double reservedHorizontal,
            double reservedGroup,
            double minimumCenterSpacing) {
        return request(
                rootSeed,
                List.of(morphology, morphology),
                reservedHorizontal,
                reservedGroup,
                new SkyIslandGroupLayout.Cluster(
                        minimumCenterSpacing, 0.0, 0.0, 0.0),
                20.0);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            List<SkyIslandMorphologySpec> morphologies,
            double reservedHorizontal,
            double reservedGroup,
            SkyIslandGroupLayout layout,
            double minimumGap) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth55",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        reservedHorizontal,
                        minimumGap,
                        0.0,
                        morphologies,
                        layout,
                        reservedGroup);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }
}
