package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandRegionalEcologicalOpportunityProfilerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303930L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void profilesExactPublishedRegionInCanonicalAssociationOrder() {
        Fixture fixture = fixture(90001L);
        SkyIslandRegionalEcologicalOpportunityProfiler profiler =
                new SkyIslandRegionalEcologicalOpportunityProfiler();

        SkyIslandRegionalEcologicalOpportunityProfile first =
                profiler.profile(fixture.binding());
        SkyIslandRegionalEcologicalOpportunityProfile second =
                profiler.profile(fixture.binding());

        assertEquals(fixture.binding().publication().id(), first.publicationId());
        assertEquals(AUTHORED_WORLD, first.authoredWorldSeed());
        assertEquals(fixture.binding().volumeCount(), first.islandCount());
        assertEquals(
                fixture.binding().associationCatalog().associations(),
                first.islands().stream()
                        .map(SkyIslandRegionalEcologicalOpportunityEntry::association)
                        .toList());

        assertEquals(
                first.totalHorizontalOwnedAreaEstimate(),
                second.totalHorizontalOwnedAreaEstimate(),
                0.0);
        assertEquals(first.meanVegetationPotential(), second.meanVegetationPotential(), 0.0);
        assertEquals(first.meanSaturationPotential(), second.meanSaturationPotential(), 0.0);
        assertEquals(first.meanThermalSuitability(), second.meanThermalSuitability(), 0.0);
        assertEquals(first.regimeFractions(), second.regimeFractions());
    }

    @Test
    void aggregateIsExactlyAreaWeightedFromAcceptedIslandProfiles() {
        SkyIslandRegionalEcologicalOpportunityProfile regional =
                new SkyIslandRegionalEcologicalOpportunityProfiler()
                        .profile(fixture(90002L).binding());

        double totalArea = 0.0;
        double vegetationArea = 0.0;
        double saturationArea = 0.0;
        double thermalArea = 0.0;
        double[] regimeAreas = new double[SkyIslandEcologyRegime.values().length];

        for (SkyIslandRegionalEcologicalOpportunityEntry entry : regional.islands()) {
            SkyIslandEcologicalOpportunityProfile island = entry.islandProfile();
            double area = island.horizontalOwnedAreaEstimate();
            totalArea += area;
            vegetationArea += area * island.meanVegetationPotential();
            saturationArea += area * island.meanSaturationPotential();
            thermalArea += area * island.meanThermalSuitability();
            for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
                regimeAreas[regime.ordinal()] += area * island.regimeFraction(regime);
            }
        }

        assertEquals(totalArea, regional.totalHorizontalOwnedAreaEstimate(), 0.0);
        assertEquals(vegetationArea / totalArea, regional.meanVegetationPotential(), 0.0);
        assertEquals(saturationArea / totalArea, regional.meanSaturationPotential(), 0.0);
        assertEquals(thermalArea / totalArea, regional.meanThermalSuitability(), 0.0);

        double sum = 0.0;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            assertEquals(
                    regimeAreas[regime.ordinal()] / totalArea,
                    regional.regimeFraction(regime),
                    0.0);
            sum += regional.regimeFraction(regime);
        }
        assertEquals(1.0, sum, 1.0e-12);
    }

    @Test
    void regionalEnvelopeRejectsMissingOrReorderedAssociationCoverage() {
        Fixture fixture = fixture(90003L);
        SkyIslandRegionalEcologicalOpportunityProfile valid =
                new SkyIslandRegionalEcologicalOpportunityProfiler()
                        .profile(fixture.binding());

        ArrayList<SkyIslandRegionalEcologicalOpportunityEntry> missing =
                new ArrayList<>(valid.islands());
        missing.removeLast();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalEcologicalOpportunityProfile(
                        fixture.binding(), missing));

        ArrayList<SkyIslandRegionalEcologicalOpportunityEntry> reversed =
                new ArrayList<>(valid.islands());
        Collections.reverse(reversed);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalEcologicalOpportunityProfile(
                        fixture.binding(), reversed));
    }

    @Test
    void entryRejectsProfileFromDifferentAuthoredAssociation() {
        SkyIslandRegionalEcologicalOpportunityProfile regional =
                new SkyIslandRegionalEcologicalOpportunityProfiler()
                        .profile(fixture(90004L).binding());
        assertTrue(regional.islandCount() >= 2);

        SkyIslandRegionalEcologicalOpportunityEntry first = regional.islands().get(0);
        SkyIslandRegionalEcologicalOpportunityEntry second = regional.islands().get(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalEcologicalOpportunityEntry(
                        first.association(),
                        second.islandProfile()));
    }

    @Test
    void publicProfilerAcceptsOnlyExactPublishedBinding() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandRegionalEcologicalOpportunityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandPublishedAuthoredRealizationBinding.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandRegionalEcologicalOpportunityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed), 1L);

        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(10_000L + ordinal, volume),
                    volume));
            ordinal++;
        }

        // Caller order is deliberately non-canonical. AUTH-0046 catalog canonicalizes it.
        Collections.reverse(associations);
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations);
        return new Fixture(
                new SkyIslandPublishedAuthoredRealizationBinding(publication, catalog));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 90L, islandKey));
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
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor()
                        .executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException(
                    "AUTH-0090 regional ecology fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth90",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        48.0,
                        0.0,
                        List.of(morphology, morphology, morphology),
                        new SkyIslandGroupLayout.Chain(
                                0.15, 800.0, 0.0, 0.0, 0.0, 0.0),
                        1_400.0);
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

    private record Fixture(
            SkyIslandPublishedAuthoredRealizationBinding binding) {}
}
