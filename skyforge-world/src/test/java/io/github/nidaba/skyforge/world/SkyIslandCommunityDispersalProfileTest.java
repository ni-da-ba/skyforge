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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SkyIslandCommunityDispersalProfileTest {
    private static final long AUTHORED_WORLD = 0x4449535045525341L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void sameGapProducesProfileSpecificAccessibility() {
        SkyIslandCommunityAssemblyEvidence evidence = evidence(120201L, 2).getFirst();
        double gap = evidence.nearestNominalRadialGap().orElseThrow();

        var shortRange = new SkyIslandExponentialDispersalProfile(Math.max(1.0, gap * 0.25));
        var longRange = new SkyIslandExponentialDispersalProfile(Math.max(2.0, gap * 2.0));

        double shortValue = shortRange.accessibility(evidence).orElseThrow();
        double longValue = longRange.accessibility(evidence).orElseThrow();
        assertTrue(longValue > shortValue);
    }

    @Test
    void exponentialAccessibilityIsMonotoneAndZeroGapIsOne() {
        SkyIslandExponentialDispersalProfile profile =
                new SkyIslandExponentialDispersalProfile(100.0);
        assertEquals(1.0, Math.exp(-0.0 / profile.characteristicNominalGap()), 0.0);
        assertTrue(Math.exp(-50.0 / profile.characteristicNominalGap())
                > Math.exp(-200.0 / profile.characteristicNominalGap()));
    }

    @Test
    void singletonEvidenceRemainsUnresolved() {
        SkyIslandCommunityAssemblyEvidence evidence = evidence(120202L, 1).getFirst();
        var profile = new SkyIslandExponentialDispersalProfile(500.0);

        SkyIslandCommunityAssemblyAccessibility result =
                SkyIslandCommunityAssemblyAccessibility.evaluate(evidence, profile);

        assertTrue(evidence.nearestNominalRadialGap().isEmpty());
        assertTrue(result.accessibility().isEmpty());
        assertTrue(!result.resolved());
    }

    @Test
    void resultBindsExactEvidenceAndProfile() {
        SkyIslandCommunityAssemblyEvidence evidence = evidence(120203L, 2).getFirst();
        var profile = new SkyIslandExponentialDispersalProfile(750.0);

        SkyIslandCommunityAssemblyAccessibility result =
                SkyIslandCommunityAssemblyAccessibility.evaluate(evidence, profile);

        assertEquals(evidence, result.evidence());
        assertEquals(profile, result.dispersalProfile());
        assertEquals(profile.accessibility(evidence), result.accessibility());
    }

    @Test
    void characteristicGapMustBeFiniteAndPositive() {
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandExponentialDispersalProfile(0.0));
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandExponentialDispersalProfile(-1.0));
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandExponentialDispersalProfile(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SkyIslandExponentialDispersalProfile(Double.POSITIVE_INFINITY));
    }

    @Test
    void accessibilityEnvelopeRejectsInvalidProfileOutput() {
        SkyIslandCommunityAssemblyEvidence evidence = evidence(120204L, 2).getFirst();
        SkyIslandCommunityDispersalProfile invalid = ignored -> OptionalDouble.of(1.5);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandCommunityAssemblyAccessibility.evaluate(evidence, invalid));
    }

    private static List<SkyIslandCommunityAssemblyEvidence> evidence(long rootSeed, int memberCount) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed, memberCount), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(50_000L + ordinal, volume), volume));
            ordinal++;
        }
        SkyIslandAuthoredRealizationCatalog catalog = new SkyIslandAuthoredRealizationCatalog(
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed, int memberCount) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, memberCount, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder().propose(
                        request, original, synthesis, ADEQUATE_VERTICAL, SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("dispersal accessibility fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, int memberCount, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "dispersal",
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
