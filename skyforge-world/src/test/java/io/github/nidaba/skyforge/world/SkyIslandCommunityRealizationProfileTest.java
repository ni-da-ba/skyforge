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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SkyIslandCommunityRealizationProfileTest {
    private static final long AUTHORED_WORLD = 0x5245414C495A4154L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private static SkyIslandCommunityAssemblyEvaluation baseAssembly;

    @BeforeAll
    static void buildBaseAssemblyFixture() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120801L).getFirst();
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandCommunityArchetype community = strongestCommunity(assembly, position);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.75, 80.0));
        SkyIslandCommunityAssemblyProfile profile =
                new SkyIslandCommunityAssemblyProfile(
                        community,
                        new SkyIslandExponentialDispersalProfile(1_000.0),
                        new SkyIslandExponentialSuccessionProfile(100.0),
                        new SkyIslandWeightedSuccessionAffinityProfile(0.25, 0.75),
                        SkyIslandMultiplicativeAssemblyCombiner.INSTANCE);
        baseAssembly = profile.evaluate(disturbance, position);
        assertTrue(baseAssembly.supportResolved());
    }

    @Test
    void structureCapacityRequiresFiniteNormalizedDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityStructureCapacity(
                        -0.01, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityStructureCapacity(
                        0.5, 1.01, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityStructureCapacity(
                        0.5, 0.5, Double.NaN, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void fullSupportReproducesExactSuppliedCapacity() {
        SkyIslandCommunityStructureCapacity capacity = capacity();
        SkyIslandCommunityRealizationEvaluation evaluation =
                realizationProfile(capacity).evaluate(withSupport(1.0));

        SkyIslandCommunityStructureRealization realized =
                evaluation.realization().orElseThrow();
        assertCapacityRealized(capacity, realized, 1.0);
        assertEquals(baseAssembly.profile().community(), evaluation.community());
        assertEquals(baseAssembly.position(), evaluation.position());
    }

    @Test
    void zeroSupportProducesZeroRealizedStructure() {
        SkyIslandCommunityRealizationEvaluation evaluation =
                realizationProfile(capacity()).evaluate(withSupport(0.0));

        assertEquals(
                SkyIslandCommunityStructureRealization.zero(),
                evaluation.realization().orElseThrow());
    }

    @Test
    void intermediateSupportScalesEveryStructuralDimensionMonotonically() {
        SkyIslandCommunityStructureCapacity capacity = capacity();
        SkyIslandCommunityStructureRealization quarter =
                realizationProfile(capacity)
                        .evaluate(withSupport(0.25))
                        .realization()
                        .orElseThrow();
        SkyIslandCommunityStructureRealization threeQuarter =
                realizationProfile(capacity)
                        .evaluate(withSupport(0.75))
                        .realization()
                        .orElseThrow();

        assertCapacityRealized(capacity, quarter, 0.25);
        assertCapacityRealized(capacity, threeQuarter, 0.75);
        assertTrue(threeQuarter.vegetationDensity() > quarter.vegetationDensity());
        assertTrue(threeQuarter.canopyCover() > quarter.canopyCover());
        assertTrue(threeQuarter.biomassPotential() > quarter.biomassPotential());
    }

    @Test
    void unresolvedAssemblySupportRemainsUnresolvedRealization() {
        SkyIslandCommunityRealizationEvaluation evaluation =
                realizationProfile(capacity()).evaluate(withUnresolvedSupport());

        assertFalse(evaluation.resolved());
        assertTrue(evaluation.realization().isEmpty());
    }

    @Test
    void sameAssemblyMayUseDifferentExplicitStructuralProfiles() {
        SkyIslandCommunityStructureCapacity sparse =
                new SkyIslandCommunityStructureCapacity(
                        0.25, 0.10, 0.15, 0.20, 0.45, 0.20, 0.70, 0.15, 0.10);
        SkyIslandCommunityStructureCapacity dense =
                new SkyIslandCommunityStructureCapacity(
                        0.90, 0.85, 0.80, 0.70, 0.75, 0.88, 0.35, 0.72, 0.60);
        SkyIslandCommunityAssemblyEvaluation assembly = withSupport(0.8);

        SkyIslandCommunityStructureRealization sparseResult =
                realizationProfile(sparse).evaluate(assembly).realization().orElseThrow();
        SkyIslandCommunityStructureRealization denseResult =
                realizationProfile(dense).evaluate(assembly).realization().orElseThrow();

        assertTrue(denseResult.vegetationDensity() > sparseResult.vegetationDensity());
        assertTrue(denseResult.canopyCover() > sparseResult.canopyCover());
        assertTrue(denseResult.canopyHeightPotential() > sparseResult.canopyHeightPotential());
        assertTrue(denseResult.biomassPotential() > sparseResult.biomassPotential());
    }

    @Test
    void evaluationRetainsExactAssemblyAndRealizationPolicyProvenance() {
        SkyIslandCommunityStructureCapacity capacity = capacity();
        SkyIslandCommunityRealizationProfile profile = realizationProfile(capacity);
        SkyIslandCommunityAssemblyEvaluation assembly = withSupport(0.6);

        SkyIslandCommunityRealizationEvaluation evaluation = profile.evaluate(assembly);

        assertEquals(assembly, evaluation.assemblyEvaluation());
        assertEquals(profile, evaluation.realizationProfile());
        assertEquals(capacity, evaluation.realizationProfile().capacity());
        assertEquals(
                SkyIslandLinearSupportRealizationTransform.INSTANCE,
                evaluation.realizationProfile().transform());
        assertTrue(evaluation.resolved());
    }

    private static SkyIslandCommunityStructureCapacity capacity() {
        return new SkyIslandCommunityStructureCapacity(
                0.82,
                0.74,
                0.68,
                0.57,
                0.76,
                0.71,
                0.43,
                0.61,
                0.49);
    }

    private static SkyIslandCommunityRealizationProfile realizationProfile(
            SkyIslandCommunityStructureCapacity capacity) {
        return new SkyIslandCommunityRealizationProfile(
                capacity,
                SkyIslandLinearSupportRealizationTransform.INSTANCE);
    }

    private static SkyIslandCommunityAssemblyEvaluation withSupport(double support) {
        return new SkyIslandCommunityAssemblyEvaluation(
                baseAssembly.profile(),
                baseAssembly.disturbanceEvidence(),
                baseAssembly.position(),
                baseAssembly.localSuitability(),
                baseAssembly.dispersalAccessibility(),
                baseAssembly.successionAssessment(),
                baseAssembly.successionAffinity(),
                OptionalDouble.of(support));
    }

    private static SkyIslandCommunityAssemblyEvaluation withUnresolvedSupport() {
        return new SkyIslandCommunityAssemblyEvaluation(
                baseAssembly.profile(),
                baseAssembly.disturbanceEvidence(),
                baseAssembly.position(),
                baseAssembly.localSuitability(),
                baseAssembly.dispersalAccessibility(),
                baseAssembly.successionAssessment(),
                baseAssembly.successionAffinity(),
                OptionalDouble.empty());
    }

    private static void assertCapacityRealized(
            SkyIslandCommunityStructureCapacity capacity,
            SkyIslandCommunityStructureRealization realized,
            double support) {
        assertEquals(
                Double.doubleToLongBits(capacity.vegetationDensityCapacity() * support),
                Double.doubleToLongBits(realized.vegetationDensity()));
        assertEquals(
                Double.doubleToLongBits(capacity.canopyCoverCapacity() * support),
                Double.doubleToLongBits(realized.canopyCover()));
        assertEquals(
                Double.doubleToLongBits(capacity.canopyHeightPotential() * support),
                Double.doubleToLongBits(realized.canopyHeightPotential()));
        assertEquals(
                Double.doubleToLongBits(capacity.understoryDensityCapacity() * support),
                Double.doubleToLongBits(realized.understoryDensity()));
        assertEquals(
                Double.doubleToLongBits(capacity.groundCoverCapacity() * support),
                Double.doubleToLongBits(realized.groundCover()));
        assertEquals(
                Double.doubleToLongBits(capacity.biomassPotential() * support),
                Double.doubleToLongBits(realized.biomassPotential()));
        assertEquals(
                Double.doubleToLongBits(capacity.patchinessPotential() * support),
                Double.doubleToLongBits(realized.patchinessPotential()));
        assertEquals(
                Double.doubleToLongBits(capacity.organicSurfaceAccumulationPotential() * support),
                Double.doubleToLongBits(realized.organicSurfaceAccumulationPotential()));
        assertEquals(
                Double.doubleToLongBits(capacity.deadwoodPotential() * support),
                Double.doubleToLongBits(realized.deadwoodPotential()));
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
        if (bestValue <= 0.0) {
            throw new IllegalStateException(
                    "fixture must expose positive local community suitability");
        }
        return best;
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
                    authored(80_000L + ordinal, volume),
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
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
                    "community structural realization fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                List.of(morphology, morphology);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "structural-realization",
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
