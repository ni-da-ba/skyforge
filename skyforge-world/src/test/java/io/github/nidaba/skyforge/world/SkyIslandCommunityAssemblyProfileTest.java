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
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SkyIslandCommunityAssemblyProfileTest {
    private static final long AUTHORED_WORLD = 0x415353454D505246L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void archetypesSelectExactAcceptedCommunitySuitabilityFields() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 12L, 120L, 120601L));
        SkyIslandCommunitySuitabilityFieldSet fields =
                SkyIslandCommunitySuitabilityFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 0.13,
                descriptor.nominalRadius() * -0.19);
        SkyIslandCommunitySuitabilitySample sample = fields.sample(position);

        for (SkyIslandCommunityArchetype archetype : SkyIslandCommunityArchetype.values()) {
            assertEquals(
                    Double.doubleToLongBits(archetype.suitability(sample)),
                    Double.doubleToLongBits(archetype.field(fields).sample(position)));
        }
    }

    @Test
    void longerRangeDispersalCanIncreaseCompositeSupportForSameCommunityAndEvidence() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120602L, 2).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.8, 50.0));

        double gap = assembly.nearestNominalRadialGap().orElseThrow();
        SkyIslandCommunityAssemblyProfile shortRange = profile(
                community,
                Math.max(1.0, gap * 0.25),
                100.0,
                0.0,
                1.0);
        SkyIslandCommunityAssemblyProfile longRange = profile(
                community,
                Math.max(2.0, gap * 2.0),
                100.0,
                0.0,
                1.0);

        SkyIslandCommunityAssemblyEvaluation shortEvaluation =
                shortRange.evaluate(disturbance, position);
        SkyIslandCommunityAssemblyEvaluation longEvaluation =
                longRange.evaluate(disturbance, position);

        assertTrue(shortEvaluation.assemblySupport().isPresent());
        assertTrue(longEvaluation.assemblySupport().isPresent());
        assertTrue(
                longEvaluation.dispersalAccessibility().accessibility().orElseThrow()
                        > shortEvaluation.dispersalAccessibility().accessibility().orElseThrow());
        assertTrue(
                longEvaluation.assemblySupport().orElseThrow()
                        > shortEvaluation.assemblySupport().orElseThrow());
    }

    @Test
    void sameDisturbanceCanFavorRecentDisturbanceOrRecoveryUnderDifferentProfiles() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120603L, 2).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.9, 10.0));

        SkyIslandCommunityAssemblyProfile disturbanceFavoring = profile(
                community,
                1_000.0,
                100.0,
                1.0,
                0.0);
        SkyIslandCommunityAssemblyProfile recoveryFavoring = profile(
                community,
                1_000.0,
                100.0,
                0.0,
                1.0);

        SkyIslandCommunityAssemblyEvaluation recent =
                disturbanceFavoring.evaluate(disturbance, position);
        SkyIslandCommunityAssemblyEvaluation recovered =
                recoveryFavoring.evaluate(disturbance, position);

        assertTrue(
                recent.successionAffinity().orElseThrow()
                        > recovered.successionAffinity().orElseThrow());
        assertTrue(
                recent.assemblySupport().orElseThrow()
                        > recovered.assemblySupport().orElseThrow());
    }

    @Test
    void missingNeighborEvidencePropagatesToUnresolvedCompositeSupport() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120604L, 1).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.7, 50.0));

        SkyIslandCommunityAssemblyEvaluation evaluation =
                profile(community, 500.0, 100.0, 0.5, 0.5)
                        .evaluate(disturbance, position);

        assertFalse(evaluation.dispersalAccessibility().resolved());
        assertTrue(evaluation.successionAffinity().isPresent());
        assertFalse(evaluation.supportResolved());
        assertTrue(evaluation.assemblySupport().isEmpty());
    }

    @Test
    void missingDisturbanceEvidencePropagatesToUnresolvedCompositeSupport() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120605L, 2).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.unresolved(assembly);

        SkyIslandCommunityAssemblyEvaluation evaluation =
                profile(community, 500.0, 100.0, 0.5, 0.5)
                        .evaluate(disturbance, position);

        assertTrue(evaluation.dispersalAccessibility().resolved());
        assertFalse(evaluation.successionAssessment().resolved());
        assertTrue(evaluation.successionAffinity().isEmpty());
        assertTrue(evaluation.assemblySupport().isEmpty());
    }

    @Test
    void multiplicativeCombinerProducesExactNormalizedProduct() {
        OptionalDouble support = SkyIslandMultiplicativeAssemblyCombiner.INSTANCE.support(
                0.8,
                OptionalDouble.of(0.5),
                OptionalDouble.of(0.25));

        assertEquals(
                Double.doubleToLongBits(0.1),
                Double.doubleToLongBits(support.orElseThrow()));
        assertTrue(SkyIslandMultiplicativeAssemblyCombiner.INSTANCE
                .support(0.8, OptionalDouble.empty(), OptionalDouble.of(0.25))
                .isEmpty());
        assertTrue(SkyIslandMultiplicativeAssemblyCombiner.INSTANCE
                .support(0.8, OptionalDouble.of(0.5), OptionalDouble.empty())
                .isEmpty());
    }

    @Test
    void invalidCustomAffinityOutputFailsClosedAtProfileBoundary() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120606L, 2).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.8, 50.0));

        SkyIslandCommunityAssemblyProfile invalid =
                new SkyIslandCommunityAssemblyProfile(
                        community,
                        new SkyIslandExponentialDispersalProfile(500.0),
                        new SkyIslandExponentialSuccessionProfile(100.0),
                        ignored -> OptionalDouble.of(1.5),
                        SkyIslandMultiplicativeAssemblyCombiner.INSTANCE);

        assertThrows(
                IllegalArgumentException.class,
                () -> invalid.evaluate(disturbance, position));
    }

    @Test
    void successionAffinityWeightsMustBeFiniteNonNegativeAndNonzeroInTotal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWeightedSuccessionAffinityProfile(-1.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWeightedSuccessionAffinityProfile(0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWeightedSuccessionAffinityProfile(Double.NaN, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandWeightedSuccessionAffinityProfile(1.0, Double.POSITIVE_INFINITY));
    }

    private static SkyIslandCommunityAssemblyProfile profile(
            SkyIslandCommunityArchetype community,
            double characteristicGap,
            double recoveryTime,
            double disturbanceWeight,
            double recoveryWeight) {
        return new SkyIslandCommunityAssemblyProfile(
                community,
                new SkyIslandExponentialDispersalProfile(characteristicGap),
                new SkyIslandExponentialSuccessionProfile(recoveryTime),
                new SkyIslandWeightedSuccessionAffinityProfile(
                        disturbanceWeight,
                        recoveryWeight),
                SkyIslandMultiplicativeAssemblyCombiner.INSTANCE);
    }

    private static SkyIslandCommunityArchetype strongestCommunity(
            SkyIslandCommunityAssemblyEvidence assembly,
            SkyIslandLocalPosition position) {
        SkyIslandCommunitySuitabilitySample sample =
                assembly.communitySuitability().sample(position);
        SkyIslandCommunityArchetype best = SkyIslandCommunityArchetype.CLOSED_WOODLAND;
        double bestValue = -1.0;
        for (SkyIslandCommunityArchetype candidate : SkyIslandCommunityArchetype.values()) {
            double value = candidate.suitability(sample);
            if (value > bestValue) {
                best = candidate;
                bestValue = value;
            }
        }
        assertTrue(bestValue > 0.0, "fixture must expose positive local community suitability");
        return best;
    }

    private static List<SkyIslandCommunityAssemblyEvidence> evidence(
            long rootSeed,
            int memberCount) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed, memberCount), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(70_000L + ordinal, volume),
                    volume));
            ordinal++;
        }
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        return new SkyIslandCommunityAssemblyEvidenceBinder()
                .bindCurrentSemantics(isolation);
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            int memberCount) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, memberCount, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
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
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException(
                    "community assembly profile fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "assembly-profile",
                        SkyIslandGroupRole.ANCHOR,
                        volumeDescriptor(),
                        360.0,
                        48.0,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor volumeDescriptor() {
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
