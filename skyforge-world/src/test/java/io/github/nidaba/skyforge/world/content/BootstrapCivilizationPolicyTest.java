package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
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
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BootstrapCivilizationPolicyTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void destinationPolicyPreservesAuthorshipOrderAndGuaranteesFirstReachableHall() {
        SkyIslandSurfaceAccessCapabilityProfile access = accessWithCandidate(fixture(270001L));
        BootstrapGuildDestinationPolicy policy = new BootstrapGuildDestinationPolicy();
        List<BootstrapGuildDestinationPolicy.Candidate> candidates = policy.candidates("bootstrap-hall", access, true);
        assertFalse(candidates.isEmpty());
        BootstrapGuildDestinationPolicy.Plan plan = policy.plan(candidates);
        assertEquals(BootstrapGuildDestinationPolicy.Outcome.DESTINATION_AVAILABLE, plan.outcome());
        assertEquals(candidates, plan.canonicalCandidates());
        assertEquals(candidates.getFirst(), plan.guaranteedDestination());
        assertTrue(plan.guaranteedDestination().resolvesBellancaClaim());
        assertEquals(BootstrapGuildDestinationPolicy.requiredHallServices(), plan.guaranteedDestination().services());
    }

    @Test
    void noReachableCandidateFailsClosedForReplanRatherThanInventingRouteGeometry() {
        SkyIslandSurfaceAccessCapabilityProfile access = access(fixture(270002L).catalog().associations().getFirst());
        BootstrapGuildDestinationPolicy policy = new BootstrapGuildDestinationPolicy();
        BootstrapGuildDestinationPolicy.Plan plan = policy.plan(policy.candidates("bootstrap-hall", access, false));
        assertEquals(BootstrapGuildDestinationPolicy.Outcome.REPLAN_REQUIRED, plan.outcome());
        assertTrue(plan.requiresReplan());
    }

    @Test
    void bellancaFlowIsRecoverableAndRestitutionIsMutuallyExclusive() {
        SkyIslandSurfaceAccessCapabilityProfile access = accessWithCandidate(fixture(270003L));
        BootstrapGuildDestinationPolicy.Candidate hall = new BootstrapGuildDestinationPolicy()
                .candidates("bootstrap-hall", access, true).getFirst();
        BellancaOnboardingStateMachine machine = new BellancaOnboardingStateMachine();
        var crash = machine.crash();
        assertEquals(BellancaOnboardingStateMachine.WreckIdentity.BELLANCA_B0_A, crash.wreckIdentity());
        var unfavorable = machine.initiateClaim(crash, hall);
        assertThrows(IllegalStateException.class, () -> machine.submitRecorderEvidence(unfavorable));
        var liability = machine.submitRecorderEvidence(machine.recoverRecorder(unfavorable));
        var complete = machine.chooseRestitution(
                liability, BellancaOnboardingStateMachine.Restitution.RETAIN_WRECK_WITH_PARTIAL_PAYOUT);
        assertTrue(complete.tutorialComplete());
        assertEquals(BellancaOnboardingStateMachine.Restitution.RETAIN_WRECK_WITH_PARTIAL_PAYOUT, complete.restitution());
        assertThrows(IllegalStateException.class, () -> machine.chooseRestitution(
                complete, BellancaOnboardingStateMachine.Restitution.SCRIP_PAYOUT));
    }

    @Test
    void firstFreightIsRealPostTutorialCopperConsumerContractNotQuestOnly() {
        BootstrapFreightOpportunity opportunity = BootstrapFreightOpportunity.firstOpportunity();
        assertEquals("bootstrap-copper-producer", opportunity.producerSettlementId());
        assertEquals("bootstrap-guild-consumer", opportunity.consumerSettlementId());
        assertEquals(BootstrapFreightOpportunity.ProducerSpecialization.COPPER_EXTRACTION, opportunity.producerSpecialization());
        assertEquals(BootstrapFreightOpportunity.ConsumerNeed.ENGINEERING_COPPER_INPUT, opportunity.consumerNeed());
        assertTrue(opportunity.routine());
        assertTrue(opportunity.recognizedRoute());
        assertTrue(opportunity.physicalCargoRequired());
        assertFalse(opportunity.mandatoryTutorialChore());
    }

    private static SkyIslandSurfaceAccessCapabilityProfile accessWithCandidate(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        return binding.catalog().associations().stream()
                .map(BootstrapCivilizationPolicyTest::access)
                .filter(profile -> profile.cells().stream().anyMatch(cell ->
                        cell.sourceCell().physicalSurfacePresent()
                                && cell.rays().stream().anyMatch(ray -> ray.observedOpenSample())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("C27 fixture has no AUTH-0096/0097 Guild candidate"));
    }

    private static SkyIslandSurfaceAccessCapabilityProfile access(SkyIslandAuthoredRealizationAssociation association) {
        return new SkyIslandSurfaceAccessCapabilityProfiler()
                .profile(new SkyIslandSurfaceSiteCapabilityProfiler().profile(association));
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication = new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(authored(80_000L + ordinal++, volume), volume));
        }
        Collections.reverse(associations);
        return new SkyIslandPublishedAuthoredRealizationBinding(publication,
                new SkyIslandAuthoredRealizationCatalog(0x434f4e54454e5437L, publication.catalog().rootSeed(), associations));
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(0x434f4e54454e5437L, 27L, 270L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        return new SkyIslandDescriptor(base.schemaVersion(), base.identity(), base.authorshipSeed(),
                realized.hasSemanticMorphologyFamily() ? realized.morphologyFamily() : base.morphologyFamily(),
                realized.nominalRadius(), base.reliefBudget(), 0.72, 0.68, 0.70, 0.70,
                base.exposureTendency(), 0.60, 0.76, base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        ProviderMorphologySpec morphology = new ProviderMorphologySpec(SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis = new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal = new SkyIslandSupportReplanProposalBuilder().propose(
                request, original, synthesis, ADEQUATE_VERTICAL, SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) throw new IllegalStateException("C27 fixture did not converge");
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate("c27", SkyIslandGroupRole.ANCHOR, descriptor(), 360.0, 48.0, 0.0,
                morphologies, new SkyIslandGroupLayout.Chain(0.15, 800.0, 0.0, 0.0, 0.0, 0.0), 1_400.0);
        return new SkyIslandArchipelagoRequest(rootSeed, 0.0, 0.0, 320.0, 500.0, List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(SkyIslandVolumeDescriptor.SCHEMA_VERSION_1, 0L, 0.0, 0.0, 320.0, 96.0, 48.0, 64.0, 24.0,
                Math.PI / 6.0, 0.65, 0.60, 0.25, 0.0, 24.0);
    }
}
