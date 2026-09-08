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
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPetroleumInfrastructureCandidateMatcherTest {
    private static final long AUTHORED_WORLD = 0x434f4e54454e5435L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void roleAssociationPolicyKeepsExtractionOnPetroleumEligibleIslandsOnly() {
        assertFalse(SkyIslandPetroleumInfrastructureCandidateMatcher.roleAllowsAssociation(
                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                        .PETROLEUM_EXTRACTION_INTERFACE,
                false));
        assertTrue(SkyIslandPetroleumInfrastructureCandidateMatcher.roleAllowsAssociation(
                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                        .PETROLEUM_EXTRACTION_INTERFACE,
                true));
        assertTrue(SkyIslandPetroleumInfrastructureCandidateMatcher.roleAllowsAssociation(
                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                        .REFINERY_PROCESSING,
                false));
        assertTrue(SkyIslandPetroleumInfrastructureCandidateMatcher.roleAllowsAssociation(
                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                        .FREIGHT_TRANSFER_EDGE,
                false));
    }

    @Test
    void matcherPassesThroughAcceptedSurfaceAndAccessEvidenceWithoutSelectingSite() {
        SkyIslandRegionalPetroleumSystemOpportunityProfile regional =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler().profile(fixture(240001L));
        SkyIslandPetroleumProvincePlanner.Plan province =
                new SkyIslandPetroleumProvincePlanner().plan(
                        regional,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE);
        assertEquals(
                SkyIslandPetroleumProvincePlanner.Outcome.CANDIDATES_AVAILABLE,
                province.outcome());

        SkyIslandAuthoredRealizationAssociation petroleumAssociation =
                province.canonicalEligibleCandidates().getFirst().association();
        SkyIslandSurfaceAccessCapabilityProfile access = access(petroleumAssociation);
        SkyIslandPetroleumInfrastructureCandidateMatcher matcher =
                new SkyIslandPetroleumInfrastructureCandidateMatcher();

        List<SkyIslandSurfaceAccessCapabilityCell> supported = access.cells().stream()
                .filter(cell -> cell.sourceCell().physicalSurfacePresent())
                .toList();
        List<SkyIslandSurfaceAccessCapabilityCell> edge = supported.stream()
                .filter(cell -> cell.rays().stream().anyMatch(ray -> ray.observedOpenSample()))
                .toList();

        assertEquals(
                supported,
                matcher.candidates(
                                province,
                                access,
                                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                        .PETROLEUM_EXTRACTION_INTERFACE)
                        .stream()
                        .map(SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate::evidence)
                        .toList());
        assertEquals(
                supported,
                matcher.candidates(
                                province,
                                access,
                                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                        .REFINERY_PROCESSING)
                        .stream()
                        .map(SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate::evidence)
                        .toList());
        assertEquals(
                edge,
                matcher.candidates(
                                province,
                                access,
                                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                        .FREIGHT_TRANSFER_EDGE)
                        .stream()
                        .map(SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate::evidence)
                        .toList());

        assertTrue(Arrays.stream(
                        SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate.class
                                .getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("selected")));
    }

    @Test
    void refineryAndFreightCandidatesMayUseAnotherIslandInTheSamePublishedRegion() {
        SkyIslandRegionalPetroleumSystemOpportunityProfile regional =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler().profile(fixture(240002L));
        SkyIslandPetroleumProvincePlanner.Plan province =
                new SkyIslandPetroleumProvincePlanner().plan(
                        regional,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE);
        assertTrue(regional.islandCount() >= 2);

        SkyIslandAuthoredRealizationAssociation association =
                regional.islands().getLast().association();
        SkyIslandSurfaceAccessCapabilityProfile access = access(association);
        SkyIslandPetroleumInfrastructureCandidateMatcher matcher =
                new SkyIslandPetroleumInfrastructureCandidateMatcher();

        List<SkyIslandSurfaceAccessCapabilityCell> expectedRefinery = access.cells().stream()
                .filter(cell -> cell.sourceCell().physicalSurfacePresent())
                .toList();
        List<SkyIslandSurfaceAccessCapabilityCell> expectedFreight = expectedRefinery.stream()
                .filter(cell -> cell.rays().stream().anyMatch(ray -> ray.observedOpenSample()))
                .toList();

        assertEquals(
                expectedRefinery,
                matcher.candidates(
                                province,
                                access,
                                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                        .REFINERY_PROCESSING)
                        .stream()
                        .map(SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate::evidence)
                        .toList());
        assertEquals(
                expectedFreight,
                matcher.candidates(
                                province,
                                access,
                                SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                        .FREIGHT_TRANSFER_EDGE)
                        .stream()
                        .map(SkyIslandPetroleumInfrastructureCandidateMatcher.Candidate::evidence)
                        .toList());
    }

    @Test
    void matcherRejectsSiteEvidenceFromOutsideTheExactC23Region() {
        SkyIslandRegionalPetroleumSystemOpportunityProfile first =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler().profile(fixture(240003L));
        SkyIslandRegionalPetroleumSystemOpportunityProfile second =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler().profile(fixture(240004L));
        SkyIslandPetroleumProvincePlanner.Plan province =
                new SkyIslandPetroleumProvincePlanner().plan(
                        first,
                        SkyIslandPetroleumProvincePlanner.ProvinceIntent.PETROLEUM_STRATEGIC_NODE);
        SkyIslandSurfaceAccessCapabilityProfile foreign =
                access(second.islands().getFirst().association());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumInfrastructureCandidateMatcher().candidates(
                        province,
                        foreign,
                        SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole
                                .REFINERY_PROCESSING));
    }

    @Test
    void publicMatcherAcceptsOnlyC23PlanAuth0097ProfileAndContentRole() {
        Method[] publicCandidates = Arrays.stream(
                        SkyIslandPetroleumInfrastructureCandidateMatcher.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("candidates"))
                .toArray(Method[]::new);

        assertEquals(1, publicCandidates.length);
        assertEquals(
                List.of(
                        SkyIslandPetroleumProvincePlanner.Plan.class,
                        SkyIslandSurfaceAccessCapabilityProfile.class,
                        SkyIslandPetroleumInfrastructureCandidateMatcher.InfrastructureRole.class),
                List.of(publicCandidates[0].getParameterTypes()));
        assertEquals(List.class, publicCandidates[0].getReturnType());
    }

    private static SkyIslandSurfaceAccessCapabilityProfile access(
            SkyIslandAuthoredRealizationAssociation association) {
        return new SkyIslandSurfaceAccessCapabilityProfiler()
                .profile(new SkyIslandSurfaceSiteCapabilityProfiler().profile(association));
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(70_000L + ordinal, volume), volume));
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
                SkyIslandIdentity.of(AUTHORED_WORLD, 24L, 240L, islandKey));
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
            throw new IllegalStateException("C24 petroleum infrastructure fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "c24",
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
