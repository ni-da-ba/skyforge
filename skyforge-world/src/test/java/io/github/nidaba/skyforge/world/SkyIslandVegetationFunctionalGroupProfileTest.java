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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SkyIslandVegetationFunctionalGroupProfileTest {
    private static final long AUTHORED_WORLD = 0x46554E4347524F55L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private static SkyIslandCommunityDisturbanceEvidence disturbanceEvidence;
    private static SkyIslandLocalPosition position;

    @BeforeAll
    static void buildFixture() {
        SkyIslandCommunityAssemblyEvidence assemblyEvidence = evidence(121601L).getFirst();
        disturbanceEvidence = SkyIslandCommunityDisturbanceEvidence.of(
                assemblyEvidence,
                new SkyIslandCommunityDisturbanceEvent(0.7, 60.0));
        position = new SkyIslandLocalPosition(0.0, 0.0);
    }

    @Test
    void weightedMeanTransformIsExactAndNormalized() {
        SkyIslandCommunityStructureRealization structure =
                new SkyIslandCommunityStructureRealization(
                        0.8, 0.6, 0.4, 0.2, 0.9, 0.7, 0.5, 0.3, 0.1);
        SkyIslandFunctionalGroupStructuralAffinity affinity =
                new SkyIslandFunctionalGroupStructuralAffinity(
                        1.0, 2.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        double support = SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE
                .support(Optional.of(structure), affinity)
                .orElseThrow();

        assertEquals((0.8 + 2.0 * 0.6 + 0.4) / 4.0, support, 0.0);
        assertTrue(support >= 0.0 && support <= 1.0);
    }

    @Test
    void explicitAffinityPolicyChangesSupportWithoutChangingEcology() {
        SkyIslandMultiCommunityRealizationComposition composition = resolvedComposition();

        SkyIslandVegetationFunctionalGroupProfile canopyBiased =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
        SkyIslandVegetationFunctionalGroupProfile groundBiased =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        var canopy = canopyBiased.evaluate(composition);
        var ground = groundBiased.evaluate(composition);

        assertEquals(composition, canopy.composition());
        assertEquals(composition.position(), canopy.position());
        assertTrue(canopy.resolved());
        assertTrue(ground.resolved());
        assertTrue(
                Double.doubleToLongBits(canopy.structuralNicheSupport().orElseThrow())
                        != Double.doubleToLongBits(ground.structuralNicheSupport().orElseThrow()));
    }

    @Test
    void unresolvedAggregateRealizationPropagatesToUnresolvedNicheSupport() {
        SkyIslandMultiCommunityRealizationComposition composition = unresolvedComposition();
        SkyIslandVegetationFunctionalGroupProfile profile =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.SHRUB,
                        uniformAffinity(),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        SkyIslandVegetationFunctionalGroupEvaluation evaluation =
                profile.evaluate(composition);

        assertFalse(composition.resolved());
        assertFalse(evaluation.resolved());
        assertTrue(evaluation.structuralNicheSupport().isEmpty());
        assertEquals(composition, evaluation.composition());
    }

    @Test
    void functionalGroupIdentityDoesNotCarryAutomaticAffinity() {
        SkyIslandVegetationFunctionalGroupProfile first =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.GRASS_FORB,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
        SkyIslandVegetationFunctionalGroupProfile second =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.GRASS_FORB,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        assertEquals(first.functionalGroup(), second.functionalGroup());
        assertTrue(!first.structuralAffinity().equals(second.structuralAffinity()));
    }

    @Test
    void invalidStructuralAffinityWeightsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void invalidCustomTransformOutputFailsAtProfileBoundary() {
        SkyIslandVegetationFunctionalGroupProfile invalid =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.FERN_GROUNDCOVER,
                        uniformAffinity(),
                        (realization, affinity) -> OptionalDouble.of(1.5));

        assertThrows(
                IllegalArgumentException.class,
                () -> invalid.evaluate(resolvedComposition()));
    }

    @Test
    void spatialPatchEvaluationIsDeterministicAndRetainsExactEcology() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandVegetationSpatialPatchProfile spatial =
                new SkyIslandVegetationSpatialPatchProfile(
                        SkyIslandEcologicalPatchSignalProfile.current(
                                0x1221A11L,
                                "ecology.patch.shrub",
                                96.0),
                        SkyIslandPatchinessRetentionSpatialTransform.INSTANCE);

        SkyIslandVegetationSpatialPatchEvaluation first = spatial.evaluate(group);
        SkyIslandVegetationSpatialPatchEvaluation second = spatial.evaluate(group);

        assertEquals(
                Double.doubleToLongBits(first.rawSignal()),
                Double.doubleToLongBits(second.rawSignal()));
        assertEquals(first.state(), second.state());
        assertEquals(group, first.functionalGroupEvaluation());
        assertEquals(group.position(), first.position());
        assertEquals(spatial, first.spatialProfile());
    }

    @Test
    void zeroPatchinessExactlyPreservesUpstreamNicheSupport() {
        SkyIslandMultiCommunityRealizationComposition composition =
                withPatchiness(resolvedComposition(), 0.0);
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(composition);
        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221B22L, "ecology.patch.zero", 80.0).evaluate(group);

        SkyIslandFunctionalGroupSpatialPatchState state =
                evaluation.state().orElseThrow();
        assertEquals(1.0, state.retention(), 0.0);
        assertEquals(
                Double.doubleToLongBits(group.structuralNicheSupport().orElseThrow()),
                Double.doubleToLongBits(state.spatialNicheSupport()));
    }

    @Test
    void zeroUpstreamSupportCannotBeCreatedBySpatialSignal() {
        SkyIslandVegetationFunctionalGroupEvaluation base =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandVegetationFunctionalGroupEvaluation zero =
                new SkyIslandVegetationFunctionalGroupEvaluation(
                        base.profile(),
                        base.composition(),
                        OptionalDouble.of(0.0));

        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221C33L, "ecology.patch.zero-support", 72.0)
                        .evaluate(zero);

        assertEquals(0.0, evaluation.state().orElseThrow().spatialNicheSupport(), 0.0);
    }

    @Test
    void unresolvedUpstreamEcologyPropagatesToUnresolvedSpatialState() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(unresolvedComposition());

        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221D44L, "ecology.patch.unresolved", 64.0)
                        .evaluate(group);

        assertFalse(group.resolved());
        assertFalse(evaluation.resolved());
        assertTrue(evaluation.state().isEmpty());
        assertTrue(Double.isFinite(evaluation.rawSignal()));
    }

    @Test
    void retentionTransformIsExactAndNeverAmplifiesSupport() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        double support = group.structuralNicheSupport().orElseThrow();
        double patchiness =
                group.composition().aggregateRealization().orElseThrow().patchinessPotential();

        SkyIslandFunctionalGroupSpatialPatchState low =
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE
                        .spatialize(group, -1.0)
                        .orElseThrow();
        SkyIslandFunctionalGroupSpatialPatchState high =
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE
                        .spatialize(group, 1.0)
                        .orElseThrow();

        assertEquals(0.0, low.normalizedSignal(), 0.0);
        assertEquals(1.0 - patchiness, low.retention(), 1.0e-15);
        assertEquals(support * (1.0 - patchiness), low.spatialNicheSupport(), 1.0e-15);
        assertEquals(1.0, high.normalizedSignal(), 0.0);
        assertEquals(1.0, high.retention(), 0.0);
        assertEquals(support, high.spatialNicheSupport(), 1.0e-15);
        assertTrue(low.spatialNicheSupport() <= support);
        assertTrue(high.spatialNicheSupport() <= support);
    }

    @Test
    void explicitSignalIdentityChangesPatternWithoutChangingEcology() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandEcologicalPatchSignalProfile first =
                SkyIslandEcologicalPatchSignalProfile.current(
                        0x1221E55L,
                        "ecology.patch.first",
                        80.0);
        SkyIslandEcologicalPatchSignalProfile second =
                SkyIslandEcologicalPatchSignalProfile.current(
                        0x1221E55L,
                        "ecology.patch.second",
                        80.0);

        SkyIslandVegetationSpatialPatchEvaluation firstEvaluation =
                new SkyIslandVegetationSpatialPatchProfile(
                                first,
                                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE)
                        .evaluate(group);
        SkyIslandVegetationSpatialPatchEvaluation secondEvaluation =
                new SkyIslandVegetationSpatialPatchProfile(
                                second,
                                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE)
                        .evaluate(group);

        assertTrue(
                Double.doubleToLongBits(firstEvaluation.rawSignal())
                        != Double.doubleToLongBits(secondEvaluation.rawSignal()));
        assertEquals(group, firstEvaluation.functionalGroupEvaluation());
        assertEquals(group, secondEvaluation.functionalGroupEvaluation());
    }

    private static SkyIslandVegetationFunctionalGroupProfile functionalGroupProfile() {
        return new SkyIslandVegetationFunctionalGroupProfile(
                SkyIslandVegetationFunctionalGroup.SHRUB,
                uniformAffinity(),
                SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
    }

    private static SkyIslandVegetationSpatialPatchProfile spatialProfile(
            long rootSeed,
            String namespace,
            double scale) {
        return new SkyIslandVegetationSpatialPatchProfile(
                SkyIslandEcologicalPatchSignalProfile.current(rootSeed, namespace, scale),
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE);
    }

    private static SkyIslandMultiCommunityRealizationComposition withPatchiness(
            SkyIslandMultiCommunityRealizationComposition composition,
            double patchiness) {
        SkyIslandCommunityStructureRealization source =
                composition.aggregateRealization().orElseThrow();
        SkyIslandCommunityStructureRealization patched =
                new SkyIslandCommunityStructureRealization(
                        source.vegetationDensity(),
                        source.canopyCover(),
                        source.canopyHeightPotential(),
                        source.understoryDensity(),
                        source.groundCover(),
                        source.biomassPotential(),
                        patchiness,
                        source.organicSurfaceAccumulationPotential(),
                        source.deadwoodPotential());
        return new SkyIslandMultiCommunityRealizationComposition(
                composition.realizationSet(),
                composition.coexistenceWeights(),
                composition.compositor(),
                Optional.of(patched));
    }

    private static SkyIslandFunctionalGroupStructuralAffinity uniformAffinity() {
        return new SkyIslandFunctionalGroupStructuralAffinity(
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private static SkyIslandMultiCommunityRealizationComposition resolvedComposition() {
        SkyIslandCommunityRealizationEvaluation woodland =
                realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        SkyIslandCommunityRealizationEvaluation grass =
                realization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.5);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));
        return SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);
    }

    private static SkyIslandMultiCommunityRealizationComposition unresolvedComposition() {
        SkyIslandCommunityRealizationEvaluation woodland =
                realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8);
        SkyIslandCommunityRealizationEvaluation grass =
                unresolvedRealization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));
        return SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);
    }

    private static SkyIslandCommunityRealizationEvaluation realization(
            SkyIslandCommunityArchetype community,
            double forcedSupport) {
        SkyIslandCommunityAssemblyEvaluation base = assembly(community);
        return realizationProfile().evaluate(
                withSupport(base, OptionalDouble.of(forcedSupport)));
    }

    private static SkyIslandCommunityRealizationEvaluation unresolvedRealization(
            SkyIslandCommunityArchetype community) {
        return realizationProfile().evaluate(
                withSupport(assembly(community), OptionalDouble.empty()));
    }

    private static SkyIslandCommunityAssemblyEvaluation assembly(
            SkyIslandCommunityArchetype community) {
        return new SkyIslandCommunityAssemblyProfile(
                        community,
                        new SkyIslandExponentialDispersalProfile(1_000.0),
                        new SkyIslandExponentialSuccessionProfile(100.0),
                        new SkyIslandWeightedSuccessionAffinityProfile(0.25, 0.75),
                        SkyIslandMultiplicativeAssemblyCombiner.INSTANCE)
                .evaluate(disturbanceEvidence, position);
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

    private static List<SkyIslandCommunityAssemblyEvidence> evidence(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(121_600L + ordinal, volume),
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
            long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
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
                    "functional-group niche fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "functional-group-niche",
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
