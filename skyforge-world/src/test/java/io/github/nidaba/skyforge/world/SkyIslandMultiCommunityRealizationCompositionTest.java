package io.github.nidaba.skyforge.world;

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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SkyIslandMultiCommunityRealizationCompositionTest {
    private static final long AUTHORED_WORLD = 0x4D554C5449434F4DL;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private static SkyIslandCommunityAssemblyEvidence assemblyEvidence;
    private static SkyIslandCommunityDisturbanceEvidence disturbanceEvidence;
    private static SkyIslandLocalPosition position;

    @BeforeAll
    static void buildFixture() {
        assemblyEvidence = evidence(121201L).getFirst();
        disturbanceEvidence = SkyIslandCommunityDisturbanceEvidence.of(
                assemblyEvidence,
                new SkyIslandCommunityDisturbanceEvent(0.7, 60.0));
        position = new SkyIslandLocalPosition(0.0, 0.0);
    }

    @Test
    void realizationSetPreservesMembersAndRejectsDuplicateCommunities() {
        var woodland = realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        var grass = realization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.6);

        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));

        assertEquals(2, set.evaluations().size());
        assertEquals(position, set.position());
        assertEquals(disturbanceEvidence, set.disturbanceEvidence());
        assertEquals(woodland, set.evaluation(SkyIslandCommunityArchetype.CLOSED_WOODLAND).orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandMultiCommunityRealizationSet(List.of(woodland, woodland)));
    }

    @Test
    void weightedAdditiveCompositionIsExactAndRetainsPerCommunityEvidence() {
        var woodland = realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        var grass = realization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.5);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));

        SkyIslandCommunityCoexistenceWeights weights =
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.4,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3));

        SkyIslandMultiCommunityRealizationComposition composition =
                SkyIslandMultiCommunityRealizationComposition.evaluate(
                        set,
                        weights,
                        SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);

        assertTrue(composition.resolved());
        SkyIslandCommunityStructureRealization expected = weighted(
                woodland.realization().orElseThrow(), 0.4,
                grass.realization().orElseThrow(), 0.3);
        assertEquals(expected, composition.aggregateRealization().orElseThrow());
        assertEquals(set, composition.realizationSet());
        assertEquals(woodland, composition.realizationSet()
                .evaluation(SkyIslandCommunityArchetype.CLOSED_WOODLAND)
                .orElseThrow());
    }

    @Test
    void positivelyWeightedUnresolvedMemberPropagatesUnresolvedAggregate() {
        var woodland = realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        var grass = unresolvedRealization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));

        SkyIslandMultiCommunityRealizationComposition composition =
                SkyIslandMultiCommunityRealizationComposition.evaluate(
                        set,
                        new SkyIslandCommunityCoexistenceWeights(Map.of(
                                SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.4,
                                SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3)),
                        SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);

        assertFalse(composition.resolved());
        assertTrue(composition.aggregateRealization().isEmpty());
    }

    @Test
    void omittedOrZeroWeightUnresolvedMemberDoesNotBlockComposition() {
        var woodland = realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        var grass = unresolvedRealization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));

        var omitted = SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);
        var zeroWeighted = SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.0)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);

        assertTrue(omitted.resolved());
        assertTrue(zeroWeighted.resolved());
        assertEquals(omitted.aggregateRealization(), zeroWeighted.aggregateRealization());
    }

    @Test
    void coexistenceWeightsAreExplicitNotRenormalizedAndMustStayWithinBudget() {
        var weights = new SkyIslandCommunityCoexistenceWeights(Map.of(
                SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.2,
                SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3));
        assertEquals(0.5, weights.totalWeight(), 0.0);
        assertEquals(0.2, weights.weight(SkyIslandCommunityArchetype.CLOSED_WOODLAND), 0.0);
        assertEquals(0.0, weights.weight(SkyIslandCommunityArchetype.XERIC_SCRUB), 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.7,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.4)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, -0.1)));
    }

    private static SkyIslandCommunityRealizationEvaluation realization(
            SkyIslandCommunityArchetype community,
            double forcedSupport) {
        SkyIslandCommunityAssemblyEvaluation base = assembly(community);
        SkyIslandCommunityAssemblyEvaluation forced = withSupport(base, OptionalDouble.of(forcedSupport));
        return realizationProfile().evaluate(forced);
    }

    private static SkyIslandCommunityRealizationEvaluation unresolvedRealization(
            SkyIslandCommunityArchetype community) {
        SkyIslandCommunityAssemblyEvaluation base = assembly(community);
        return realizationProfile().evaluate(withSupport(base, OptionalDouble.empty()));
    }

    private static SkyIslandCommunityAssemblyEvaluation assembly(
            SkyIslandCommunityArchetype community) {
        SkyIslandCommunityAssemblyProfile profile = new SkyIslandCommunityAssemblyProfile(
                community,
                new SkyIslandExponentialDispersalProfile(1_000.0),
                new SkyIslandExponentialSuccessionProfile(100.0),
                new SkyIslandWeightedSuccessionAffinityProfile(0.25, 0.75),
                SkyIslandMultiplicativeAssemblyCombiner.INSTANCE);
        return profile.evaluate(disturbanceEvidence, position);
    }

    private static SkyIslandCommunityAssemblyEvaluation withSupport(
            SkyIslandCommunityAssemblyEvaluation base,
            OptionalDouble support) {
        return new SkyIslandCommunityAssemblyEvaluation(
                base.profile(),
                base.disturbanceEvidence(),
                base.position(),
                base.localSuitability(),
                base.dispersalAccessibility(),
                base.successionAssessment(),
                base.successionAffinity(),
                support);
    }

    private static SkyIslandCommunityRealizationProfile realizationProfile() {
        return new SkyIslandCommunityRealizationProfile(
                new SkyIslandCommunityStructureCapacity(
                        0.8, 0.7, 0.6, 0.5, 0.9, 0.75, 0.4, 0.55, 0.3),
                SkyIslandLinearSupportRealizationTransform.INSTANCE);
    }

    private static SkyIslandCommunityStructureRealization weighted(
            SkyIslandCommunityStructureRealization a,
            double aw,
            SkyIslandCommunityStructureRealization b,
            double bw) {
        return new SkyIslandCommunityStructureRealization(
                a.vegetationDensity() * aw + b.vegetationDensity() * bw,
                a.canopyCover() * aw + b.canopyCover() * bw,
                a.canopyHeightPotential() * aw + b.canopyHeightPotential() * bw,
                a.understoryDensity() * aw + b.understoryDensity() * bw,
                a.groundCover() * aw + b.groundCover() * bw,
                a.biomassPotential() * aw + b.biomassPotential() * bw,
                a.patchinessPotential() * aw + b.patchinessPotential() * bw,
                a.organicSurfaceAccumulationPotential() * aw
                        + b.organicSurfaceAccumulationPotential() * bw,
                a.deadwoodPotential() * aw + b.deadwoodPotential() * bw);
    }

    private static List<SkyIslandCommunityAssemblyEvidence> evidence(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(90_000L + ordinal, volume), volume));
            ordinal++;
        }
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), associations);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        return new SkyIslandCommunityAssemblyEvidenceBinder().bindCurrentSemantics(isolation);
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 12L, 120L, islandKey));
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
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder().propose(
                        request, original, synthesis, ADEQUATE_VERTICAL, SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("multi-community realization fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "multi-community-realization",
                SkyIslandGroupRole.ANCHOR,
                volumeDescriptor(),
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

    private static SkyIslandVolumeDescriptor volumeDescriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L, 0.0, 0.0, 320.0, 96.0, 48.0, 64.0, 24.0,
                Math.PI / 6.0, 0.65, 0.60, 0.25, 0.0, 24.0);
    }
}
