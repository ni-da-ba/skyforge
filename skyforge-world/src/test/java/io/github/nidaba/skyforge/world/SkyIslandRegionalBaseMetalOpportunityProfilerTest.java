package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandRegionalBaseMetalOpportunityProfilerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303934L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void profilesExactPublishedRegionInCanonicalAssociationOrderDeterministically() {
        Fixture fixture = fixture(94001L);
        SkyIslandRegionalBaseMetalOpportunityProfiler profiler =
                new SkyIslandRegionalBaseMetalOpportunityProfiler();

        SkyIslandRegionalBaseMetalOpportunityProfile first = profiler.profile(fixture.binding());
        SkyIslandRegionalBaseMetalOpportunityProfile second = profiler.profile(fixture.binding());

        assertEquals(fixture.binding().publication().id(), first.publicationId());
        assertEquals(AUTHORED_WORLD, first.authoredWorldSeed());
        assertEquals(fixture.binding().volumeCount(), first.islandCount());
        assertEquals(
                fixture.binding().associationCatalog().associations(),
                first.islands().stream()
                        .map(SkyIslandRegionalBaseMetalOpportunityEntry::association)
                        .toList());
        assertEquals(first.islandCount(), second.islandCount());
        for (int index = 0; index < first.islandCount(); index++) {
            SkyIslandRegionalBaseMetalOpportunityEntry a = first.islands().get(index);
            SkyIslandRegionalBaseMetalOpportunityEntry b = second.islands().get(index);
            assertEquals(a.association(), b.association());
            assertEquals(a.islandProfile().sourcePlan(), b.islandProfile().sourcePlan());
            for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
                assertEquals(a.islandProfile().meanOpportunity(kind), b.islandProfile().meanOpportunity(kind), 0.0);
                assertEquals(a.islandProfile().peakOpportunity(kind), b.islandProfile().peakOpportunity(kind), 0.0);
                assertEquals(a.islandProfile().relativeOpportunityShare(kind), b.islandProfile().relativeOpportunityShare(kind), 0.0);
            }
        }
    }

    @Test
    void inventoryCountsEligibilityAndRanksOnlyByUnchangedAuth0093MeanOpportunity() {
        SkyIslandRegionalBaseMetalOpportunityProfile regional =
                new SkyIslandRegionalBaseMetalOpportunityProfiler().profile(fixture(94002L).binding());

        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            List<SkyIslandRegionalBaseMetalOpportunityEntry> eligible = regional.eligibleIslands(kind);
            assertEquals(eligible.size(), regional.eligibleIslandCount(kind));
            assertEquals(
                    regional.islands().stream()
                            .filter(entry -> entry.islandProfile().peakOpportunity(kind) > 0.0)
                            .toList(),
                    eligible);

            List<SkyIslandRegionalBaseMetalOpportunityEntry> ranked =
                    regional.rankedEligibleIslands(kind);
            assertEquals(eligible.size(), ranked.size());
            assertTrue(ranked.stream().allMatch(entry -> entry.geologicallyEligible(kind)));
            for (int index = 1; index < ranked.size(); index++) {
                assertTrue(
                        ranked.get(index - 1).meanOpportunity(kind)
                                >= ranked.get(index).meanOpportunity(kind));
            }
            if (!ranked.isEmpty()) {
                assertEquals(
                        ranked.getFirst().meanOpportunity(kind),
                        regional.strongestMeanOpportunity(kind).orElseThrow(),
                        0.0);
            }
        }
    }

    @Test
    void regionalEnvelopeRejectsMissingOrReorderedAssociationCoverage() {
        Fixture fixture = fixture(94003L);
        SkyIslandRegionalBaseMetalOpportunityProfile valid =
                new SkyIslandRegionalBaseMetalOpportunityProfiler().profile(fixture.binding());

        ArrayList<SkyIslandRegionalBaseMetalOpportunityEntry> missing =
                new ArrayList<>(valid.islands());
        missing.removeLast();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalBaseMetalOpportunityProfile(fixture.binding(), missing));

        ArrayList<SkyIslandRegionalBaseMetalOpportunityEntry> reversed =
                new ArrayList<>(valid.islands());
        Collections.reverse(reversed);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalBaseMetalOpportunityProfile(fixture.binding(), reversed));
    }

    @Test
    void entryRejectsProfileFromDifferentAuthoredAssociation() {
        SkyIslandRegionalBaseMetalOpportunityProfile regional =
                new SkyIslandRegionalBaseMetalOpportunityProfiler().profile(fixture(94004L).binding());
        assertTrue(regional.islandCount() >= 2);

        SkyIslandRegionalBaseMetalOpportunityEntry first = regional.islands().get(0);
        SkyIslandRegionalBaseMetalOpportunityEntry second = regional.islands().get(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalBaseMetalOpportunityEntry(
                        first.association(), second.islandProfile()));
    }

    @Test
    void publicProfilerAcceptsOnlyExactPublishedBinding() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandRegionalBaseMetalOpportunityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandPublishedAuthoredRealizationBinding.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandRegionalBaseMetalOpportunityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(40_000L + ordinal, volume), volume));
            ordinal++;
        }
        Collections.reverse(associations);
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), associations);
        return new Fixture(new SkyIslandPublishedAuthoredRealizationBinding(publication, catalog));
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 94L, islandKey));
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
                0.68,
                0.76,
                base.temperatureTendency(),
                0.72,
                base.exposureTendency(),
                0.66,
                0.74,
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
            throw new IllegalStateException("AUTH-0094 regional base-metal fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "auth94",
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

    private record Fixture(SkyIslandPublishedAuthoredRealizationBinding binding) {}
}
