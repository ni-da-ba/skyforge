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
import org.junit.jupiter.api.Test;

final class SkyIslandCommunitySuccessionProfileTest {
    private static final long AUTHORED_WORLD = 0x535543434553534EL;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void disturbanceEventValidatesSemanticMagnitudeAndElapsedTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityDisturbanceEvent(-0.01, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityDisturbanceEvent(1.01, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityDisturbanceEvent(0.5, -1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityDisturbanceEvent(Double.NaN, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandCommunityDisturbanceEvent(0.5, Double.POSITIVE_INFINITY));
    }

    @Test
    void zeroElapsedTimePreservesSeverityAndHasZeroRecoveryProgress() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120401L);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.72, 0.0));
        SkyIslandExponentialSuccessionProfile profile =
                new SkyIslandExponentialSuccessionProfile(100.0);

        SkyIslandCommunitySuccessionState state =
                profile.state(disturbance).orElseThrow();

        assertEquals(Double.doubleToLongBits(0.72),
                Double.doubleToLongBits(state.residualDisturbance()));
        assertEquals(0.0, state.recoveryProgress(), 0.0);
    }

    @Test
    void elapsedTimeMonotonicallyReducesResidualAndIncreasesRecovery() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120402L);
        SkyIslandExponentialSuccessionProfile profile =
                new SkyIslandExponentialSuccessionProfile(80.0);

        SkyIslandCommunitySuccessionState early = profile.state(
                        SkyIslandCommunityDisturbanceEvidence.of(
                                assembly,
                                new SkyIslandCommunityDisturbanceEvent(0.9, 10.0)))
                .orElseThrow();
        SkyIslandCommunitySuccessionState late = profile.state(
                        SkyIslandCommunityDisturbanceEvidence.of(
                                assembly,
                                new SkyIslandCommunityDisturbanceEvent(0.9, 240.0)))
                .orElseThrow();

        assertTrue(late.residualDisturbance() < early.residualDisturbance());
        assertTrue(late.recoveryProgress() > early.recoveryProgress());
    }

    @Test
    void missingDisturbanceEvidenceRemainsUnresolved() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120403L);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.unresolved(assembly);
        SkyIslandExponentialSuccessionProfile profile =
                new SkyIslandExponentialSuccessionProfile(120.0);

        SkyIslandCommunitySuccessionAssessment assessment =
                SkyIslandCommunitySuccessionAssessment.evaluate(disturbance, profile);

        assertFalse(disturbance.hasDisturbanceEvidence());
        assertTrue(assessment.state().isEmpty());
        assertFalse(assessment.resolved());
        assertEquals(assembly, assessment.disturbanceEvidence().assemblyEvidence());
    }

    @Test
    void sameEventMayUseDifferentExplicitRecoveryProfiles() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120404L);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.8, 50.0));
        SkyIslandExponentialSuccessionProfile fast =
                new SkyIslandExponentialSuccessionProfile(20.0);
        SkyIslandExponentialSuccessionProfile slow =
                new SkyIslandExponentialSuccessionProfile(200.0);

        SkyIslandCommunitySuccessionState fastState =
                fast.state(disturbance).orElseThrow();
        SkyIslandCommunitySuccessionState slowState =
                slow.state(disturbance).orElseThrow();

        assertTrue(fastState.residualDisturbance() < slowState.residualDisturbance());
        assertTrue(fastState.recoveryProgress() > slowState.recoveryProgress());
    }

    @Test
    void successionAssessmentBindsExactEvidenceAndProfile() {
        SkyIslandCommunityAssemblyEvidence assembly = evidence(120405L);
        SkyIslandCommunityDisturbanceEvidence disturbance =
                SkyIslandCommunityDisturbanceEvidence.of(
                        assembly,
                        new SkyIslandCommunityDisturbanceEvent(0.6, 75.0));
        SkyIslandExponentialSuccessionProfile profile =
                new SkyIslandExponentialSuccessionProfile(90.0);

        SkyIslandCommunitySuccessionAssessment assessment =
                SkyIslandCommunitySuccessionAssessment.evaluate(disturbance, profile);

        assertEquals(disturbance, assessment.disturbanceEvidence());
        assertEquals(profile, assessment.successionProfile());
        assertEquals(profile.state(disturbance), assessment.state());
        assertTrue(assessment.resolved());
    }

    @Test
    void characteristicRecoveryTimeMustBeFiniteAndPositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandExponentialSuccessionProfile(0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandExponentialSuccessionProfile(-1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandExponentialSuccessionProfile(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandExponentialSuccessionProfile(Double.POSITIVE_INFINITY));
    }

    private static SkyIslandCommunityAssemblyEvidence evidence(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed), 1L);
        SkyIslandWorldVolume volume = publication.catalog().volumes().getFirst();
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored(60_000L + rootSeed, volume),
                        volume);
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        List.of(association));
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        return new SkyIslandCommunityAssemblyEvidenceBinder()
                .bindCurrentSemantics(isolation)
                .getFirst();
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
                    "succession recovery fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "succession",
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
