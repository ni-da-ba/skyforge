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
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPetroleumProvincePlannerTest {
    private static final long AUTHORED_WORLD = 0x434f4e54454e5434L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void ordinaryProvinceNeverReplansSolelyForPetroleumAbsence() {
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.NO_PETROLEUM_REQUIREMENT,
                SkyIslandPetroleumProvincePlanner.outcomeForIntent(
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.ORDINARY_PROVINCE, 0));
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.NO_PETROLEUM_REQUIREMENT,
                SkyIslandPetroleumProvincePlanner.outcomeForIntent(
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.ORDINARY_PROVINCE, 5));
    }

    @Test
    void intentionalStrategicNodeRequiresAtLeastOneAuth0100EligibleIsland() {
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.REPLAN_REQUIRED,
                SkyIslandPetroleumProvincePlanner.outcomeForIntent(
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE, 0));
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.CANDIDATES_AVAILABLE,
                SkyIslandPetroleumProvincePlanner.outcomeForIntent(
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPetroleumProvincePlanner.outcomeForIntent(
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE, -1));
    }

    @Test
    void plannerPassesThroughExactAuth0100CandidateViewsWithoutSelectingSite() {
        SkyIslandRegionalPetroleumSystemOpportunityProfile profile =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler()
                        .profile(fixture(230001L));
        assertTrue(profile.eligibleIslandCount() > 0);

        SkyIslandPetroleumProvincePlanner.Plan plan =
                new SkyIslandPetroleumProvincePlanner().plan(
                        profile,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE);

        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.CANDIDATES_AVAILABLE,
                plan.outcome());
        assertFalse(plan.requiresReplan());
        assertEquals(profile, plan.sourceProfile());
        assertEquals(profile.eligibleIslands(), plan.canonicalEligibleCandidates());
        assertEquals(profile.rankedEligibleIslands(), plan.rankedEligibleCandidates());
        assertEquals(
                profile.eligibleIslandCount(),
                plan.canonicalEligibleCandidates().size());

        assertTrue(Arrays.stream(SkyIslandPetroleumProvincePlanner.Plan.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("selected")));
    }

    @Test
    void planEnvelopeRejectsSubstitutedAuth0100CandidateViews() {
        SkyIslandRegionalPetroleumSystemOpportunityProfile profile =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler()
                        .profile(fixture(230002L));
        assertTrue(profile.eligibleIslandCount() > 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumProvincePlanner.Plan(
                        profile,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE,
                        SkyIslandPetroleumProvincePlanner.Outcome.REPLAN_REQUIRED,
                        List.of(),
                        profile.rankedEligibleIslands()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumProvincePlanner.Plan(
                        profile,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE,
                        SkyIslandPetroleumProvincePlanner.Outcome.CANDIDATES_AVAILABLE,
                        profile.eligibleIslands(),
                        List.of()));
    }

    @Test
    void publicPlannerAcceptsOnlyExactAuth0100ProfileAndContentIntent() {
        Method[] publicPlans = Arrays.stream(
                        SkyIslandPetroleumProvincePlanner.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("plan"))
                .toArray(Method[]::new);

        assertEquals(1, publicPlans.length);
        assertEquals(
                List.of(
                        SkyIslandRegionalPetroleumSystemOpportunityProfile.class,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.class),
                List.of(publicPlans[0].getParameterTypes()));
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Plan.class,
                publicPlans[0].getReturnType());
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(60_000L + ordinal, volume), volume));
            ordinal++;
        }
        Collections.reverse(associations);
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), associations);
        return new SkyIslandPublishedAuthoredRealizationBinding(publication, catalog);
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 23L, 230L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        var morphology = realized.hasSemanticMorphologyFamily()
                ? realized.morphologyFamily()
                : base.morphologyFamily();
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                morphology,
                realized.nominalRadius(),
                base.reliefBudget(),
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        ProviderMorphologySpec morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder().propose(
                        request,
                        original,
                        synthesis,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("C23 petroleum province fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "c23",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                360.0,
                48.0,
                0.0,
                morphologies,
                new SkyIslandGroupLayout.Chain(0.15, 800.0, 0.0, 0.0, 0.0, 0.0),
                1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0,
                48.0,
                64.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0);
    }
}
