package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandSupportConvergenceExecutorTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void adequateCandidateIsAcceptedInExactlyOnePass() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        56001L,
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(
                                        MorphologyFamily.MASSIF)),
                        360.0,
                        440.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);

        SkyIslandSupportConvergenceReport report =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);

        assertEquals(SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS, report.outcome());
        assertTrue(report.accepted());
        assertEquals(1, report.plannerAttemptCount());
        assertTrue(report.plannerFailure().isEmpty());
        assertTrue(report.freshPlan().isPresent());
        assertTrue(report.freshSynthesis().orElseThrow().fullySynthesized());
        assertTrue(report.freshPreflight().orElseThrow().admitted());
    }

    @Test
    void candidatePlannerRejectionIsTerminalWithoutFreshProofStages() {
        SkyIslandArchipelagoRequest request =
                twoRequest(
                        56002L,
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(
                                        MorphologyFamily.MASSIF)),
                        120.0,
                        280.0,
                        260.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);

        SkyIslandSupportConvergenceReport report =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);

        assertEquals(SkyIslandSupportConvergenceOutcome.PLANNER_REJECTED, report.outcome());
        assertFalse(report.accepted());
        assertEquals(1, report.plannerAttemptCount());
        assertTrue(report.plannerFailure().isPresent());
        assertTrue(report.plannerFailure().orElseThrow().exceptionType().endsWith("IllegalStateException"));
        assertTrue(report.freshPlan().isEmpty());
        assertTrue(report.freshSynthesis().isEmpty());
        assertTrue(report.freshPreflight().isEmpty());
    }

    @Test
    void freshLossOfProviderCertificateIsTerminalIncompleteSynthesis() {
        MorphologyProviderId id =
                new MorphologyProviderId("test", "auth56-fresh-uncertified");
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(positionSensitiveProvider(id, true))
                        .build();
        SkyIslandArchipelagoRequest request =
                twoRequest(
                        56003L,
                        new ProviderMorphologySpec(id, 0.0, 0.0),
                        120.0,
                        350.0,
                        260.0);
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        new SkyIslandSupportReplanMargin(0.0, 100.0, 0.0, 0.0));

        SkyIslandSupportConvergenceReport report =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);

        assertEquals(
                SkyIslandSupportConvergenceOutcome.FRESH_SYNTHESIS_INCOMPLETE,
                report.outcome());
        assertEquals(1, report.plannerAttemptCount());
        assertTrue(report.plannerFailure().isEmpty());
        assertTrue(report.freshPlan().isPresent());
        assertFalse(report.freshSynthesis().orElseThrow().fullySynthesized());
        assertTrue(report.freshSynthesis().orElseThrow().uncertifiedMemberCount() > 0);
        assertTrue(report.freshPreflight().isEmpty());
    }

    @Test
    void freshLargerCertifiedSupportIsTerminalReservationRejection() {
        MorphologyProviderId id =
                new MorphologyProviderId("test", "auth56-fresh-larger");
        SkyIslandMorphologyProviderRegistry registry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(positionSensitiveProvider(id, false))
                        .build();
        SkyIslandArchipelagoRequest request =
                twoRequest(
                        56004L,
                        new ProviderMorphologySpec(id, 0.0, 0.0),
                        120.0,
                        350.0,
                        260.0);
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        new SkyIslandSupportReplanMargin(0.0, 100.0, 0.0, 0.0));

        SkyIslandSupportConvergenceReport report =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);

        assertEquals(
                SkyIslandSupportConvergenceOutcome.FRESH_RESERVATION_REJECTED,
                report.outcome());
        assertEquals(1, report.plannerAttemptCount());
        assertTrue(report.freshSynthesis().orElseThrow().fullySynthesized());
        assertFalse(report.freshPreflight().orElseThrow().admitted());
        assertTrue(
                report.freshPreflight().orElseThrow().undersizedMemberHorizontalCount() > 0);
        assertTrue(report.plannerFailure().isEmpty());
    }

    @Test
    void incompleteAuth0055ProposalIsRejectedBeforeCandidatePlanning() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        56005L,
                        new io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec(
                                new io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend(
                                        massif, basin, 0.35),
                                1.0,
                                1.0),
                        360.0,
                        440.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);
        assertFalse(proposal.complete());

        assertThrows(
                IllegalStateException.class,
                () -> new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry));
    }

    @Test
    void repeatedExecutionIsDeterministicButNeverLoopsInsideOneExecution() {
        SkyIslandArchipelagoRequest request =
                singleRequest(
                        56006L,
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(
                                        MorphologyFamily.BASIN)),
                        360.0,
                        440.0);
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandSupportReplanProposal proposal =
                proposal(
                        request,
                        registry,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);
        var executor = new SkyIslandSupportConvergenceExecutor();

        SkyIslandSupportConvergenceReport first = executor.executeOnce(proposal, registry);
        SkyIslandSupportConvergenceReport second = executor.executeOnce(proposal, registry);

        assertEquals(first, second);
        assertEquals(1, first.plannerAttemptCount());
        assertEquals(1, second.plannerAttemptCount());
    }

    private static SkyIslandMorphologyProvider positionSensitiveProvider(
            MorphologyProviderId id, boolean becomeUncertified) {
        return new SkyIslandMorphologyProvider() {
            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(
                    SkyIslandVolumeDescriptor descriptor) {
                throw new AssertionError(
                        "AUTH-0056 convergence proof must not compile primary morphology");
            }

            @Override
            public Optional<PrimaryMorphologySupportEnvelope>
                    certifiedPrimarySupportEnvelope(
                            SkyIslandVolumeDescriptor descriptor) {
                double radial = Math.hypot(descriptor.centerX(), descriptor.centerZ());
                if (radial < 180.0) {
                    return Optional.of(
                            new PrimaryMorphologySupportEnvelope(200.0, 50.0, 50.0));
                }
                if (becomeUncertified) {
                    return Optional.empty();
                }
                return Optional.of(
                        new PrimaryMorphologySupportEnvelope(500.0, 50.0, 50.0));
            }
        };
    }

    private static SkyIslandSupportReplanProposal proposal(
            SkyIslandArchipelagoRequest request,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation vertical,
            SkyIslandSupportReplanMargin margin) {
        SkyIslandArchipelagoPlan plan = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(plan, registry);
        return new SkyIslandSupportReplanProposalBuilder()
                .propose(request, plan, synthesis, vertical, margin);
    }

    private static SkyIslandArchipelagoRequest singleRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius) {
        return request(
                rootSeed,
                List.of(morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                0.0);
    }

    private static SkyIslandArchipelagoRequest twoRequest(
            long rootSeed,
            SkyIslandMorphologySpec morphology,
            double horizontal,
            double groupRadius,
            double spacing) {
        return request(
                rootSeed,
                List.of(morphology, morphology),
                horizontal,
                groupRadius,
                new SkyIslandGroupLayout.Cluster(spacing, 0.0, 0.0, 0.0),
                20.0);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            List<SkyIslandMorphologySpec> morphologies,
            double horizontal,
            double groupRadius,
            SkyIslandGroupLayout layout,
            double minimumGap) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth56",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        horizontal,
                        minimumGap,
                        0.0,
                        morphologies,
                        layout,
                        groupRadius);
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
