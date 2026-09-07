package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandRegionalIsolationProfilerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303932L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void profilesExactBindingInCanonicalAssociationOrderDeterministically() {
        Fixture fixture = fixture(92001L, 3, 0.0, 0.0, 1.0);
        SkyIslandRegionalIsolationProfiler profiler = new SkyIslandRegionalIsolationProfiler();

        SkyIslandRegionalIsolationProfile first = profiler.profile(fixture.binding());
        SkyIslandRegionalIsolationProfile second = profiler.profile(fixture.binding());

        assertEquals(fixture.binding().publication().id(), first.publicationId());
        assertEquals(AUTHORED_WORLD, first.authoredWorldSeed());
        assertEquals(3, first.islandCount());
        assertEquals(
                fixture.binding().associationCatalog().associations(),
                first.islands().stream()
                        .map(SkyIslandRegionalIsolationEntry::association)
                        .toList());

        assertEquals(first.islands(), second.islands());
        assertOptionalEquals(
                first.minimumNearestCenterDistance(),
                second.minimumNearestCenterDistance(),
                0.0);
        assertOptionalEquals(
                first.meanNearestCenterDistance(),
                second.meanNearestCenterDistance(),
                0.0);
        assertOptionalEquals(
                first.maximumNearestCenterDistance(),
                second.maximumNearestCenterDistance(),
                0.0);
        assertOptionalEquals(
                first.minimumNearestNominalRadialGap(),
                second.minimumNearestNominalRadialGap(),
                0.0);
        assertOptionalEquals(
                first.meanNearestNominalRadialGap(),
                second.meanNearestNominalRadialGap(),
                0.0);
        assertOptionalEquals(
                first.maximumNearestNominalRadialGap(),
                second.maximumNearestNominalRadialGap(),
                0.0);

        assertTrue(first.islands().stream().allMatch(SkyIslandRegionalIsolationEntry::hasNeighbor));
    }

    @Test
    void nearestNeighborUsesNominalGapThenCenterDistanceThenCanonicalOrder() {
        SkyIslandRegionalIsolationProfile profile =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92002L, 3, 0.0, 0.0, 1.0).binding());

        // Three equal-radius chain members have equal left/right gaps at the middle member.
        // Canonical AUTH-0046 order therefore resolves the exact tie toward the first member.
        SkyIslandRegionalIsolationEntry first = profile.islands().get(0);
        SkyIslandRegionalIsolationEntry middle = profile.islands().get(1);
        SkyIslandRegionalIsolationEntry last = profile.islands().get(2);

        assertEquals(
                middle.nearestNeighbor().orElseThrow().association(),
                first.association());
        assertEquals(
                first.nearestNeighbor().orElseThrow().association(),
                middle.association());
        assertEquals(
                last.nearestNeighbor().orElseThrow().association(),
                middle.association());

        for (SkyIslandRegionalIsolationEntry entry : profile.islands()) {
            SkyIslandRegionalIsolationNeighbor neighbor =
                    entry.nearestNeighbor().orElseThrow();
            double expectedCenter = centerDistance(
                    entry.association(), neighbor.association());
            double expectedGap = Math.max(
                    0.0,
                    expectedCenter
                            - entry.association().authoredDescriptor().nominalRadius()
                            - neighbor.association().authoredDescriptor().nominalRadius());
            assertEquals(expectedCenter, neighbor.centerDistance(), 0.0);
            assertEquals(expectedGap, neighbor.nominalRadialGap(), 0.0);
        }
    }

    @Test
    void singleIslandPublicationHasNoFabricatedNeighborOrAggregateDistance() {
        SkyIslandRegionalIsolationProfile single =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92003L, 1, 0.0, 0.0, 1.0).binding());

        assertEquals(1, single.islandCount());
        assertFalse(single.islands().getFirst().hasNeighbor());
        assertTrue(single.minimumNearestCenterDistance().isEmpty());
        assertTrue(single.meanNearestCenterDistance().isEmpty());
        assertTrue(single.maximumNearestCenterDistance().isEmpty());
        assertTrue(single.minimumNearestNominalRadialGap().isEmpty());
        assertTrue(single.meanNearestNominalRadialGap().isEmpty());
        assertTrue(single.maximumNearestNominalRadialGap().isEmpty());
    }

    @Test
    void uniformRegionalTranslationPreservesIsolationEvidence() {
        SkyIslandRegionalIsolationProfile origin =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92004L, 3, 0.0, 0.0, 1.0).binding());
        SkyIslandRegionalIsolationProfile translated =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92004L, 3, 12_500.0, -8_750.0, 1.0).binding());

        assertEquals(origin.islandCount(), translated.islandCount());
        for (int index = 0; index < origin.islandCount(); index++) {
            SkyIslandRegionalIsolationNeighbor a =
                    origin.islands().get(index).nearestNeighbor().orElseThrow();
            SkyIslandRegionalIsolationNeighbor b =
                    translated.islands().get(index).nearestNeighbor().orElseThrow();
            assertEquals(
                    a.association().realizedVolumeId(),
                    b.association().realizedVolumeId());
            assertEquals(a.centerDistance(), b.centerDistance(), 1.0e-9);
            assertEquals(a.nominalRadialGap(), b.nominalRadialGap(), 1.0e-9);
        }
    }

    @Test
    void uniformHorizontalScaleDoublesRawIsolationDistances() {
        SkyIslandRegionalIsolationProfile small =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92005L, 3, 0.0, 0.0, 1.0).binding());
        SkyIslandRegionalIsolationProfile large =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92005L, 3, 0.0, 0.0, 2.0).binding());

        assertEquals(small.islandCount(), large.islandCount());
        for (int index = 0; index < small.islandCount(); index++) {
            SkyIslandRegionalIsolationNeighbor a =
                    small.islands().get(index).nearestNeighbor().orElseThrow();
            SkyIslandRegionalIsolationNeighbor b =
                    large.islands().get(index).nearestNeighbor().orElseThrow();
            assertEquals(
                    a.association().realizedVolumeId(),
                    b.association().realizedVolumeId());
            assertEquals(a.centerDistance() * 2.0, b.centerDistance(), 1.0e-9);
            assertEquals(a.nominalRadialGap() * 2.0, b.nominalRadialGap(), 1.0e-9);
        }
    }

    @Test
    void entryRejectsSubstitutedDistanceEvidence() {
        SkyIslandRegionalIsolationProfile profile =
                new SkyIslandRegionalIsolationProfiler()
                        .profile(fixture(92006L, 3, 0.0, 0.0, 1.0).binding());
        SkyIslandRegionalIsolationEntry entry = profile.islands().getFirst();
        SkyIslandRegionalIsolationNeighbor valid = entry.nearestNeighbor().orElseThrow();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandRegionalIsolationEntry(
                        entry.association(),
                        Optional.of(new SkyIslandRegionalIsolationNeighbor(
                                valid.association(),
                                valid.centerDistance() + 1.0,
                                valid.nominalRadialGap()))));
    }

    @Test
    void publicProfilerAcceptsOnlyExactPublishedBinding() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandRegionalIsolationProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandPublishedAuthoredRealizationBinding.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandRegionalIsolationProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static void assertOptionalEquals(
            java.util.OptionalDouble first,
            java.util.OptionalDouble second,
            double tolerance) {
        assertEquals(first.isPresent(), second.isPresent());
        if (first.isPresent()) {
            assertEquals(first.getAsDouble(), second.getAsDouble(), tolerance);
        }
    }

    private static double centerDistance(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var a = first.realizedVolume().compiledVolume().descriptor();
        var b = second.realizedVolume().compiledVolume().descriptor();
        return Math.hypot(b.centerX() - a.centerX(), b.centerZ() - a.centerZ());
    }

    private static Fixture fixture(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerZ,
            double scale) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(
                                acceptedCompilation(
                                        rootSeed,
                                        memberCount,
                                        centerX,
                                        centerZ,
                                        scale),
                                1L);

        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(30_000L + ordinal, volume),
                    volume));
            ordinal++;
        }

        Collections.reverse(associations);
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations);
        return new Fixture(
                new SkyIslandPublishedAuthoredRealizationBinding(
                        publication, catalog));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 92L, islandKey));
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
            int memberCount,
            double centerX,
            double centerZ,
            double scale) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request =
                request(
                        rootSeed,
                        memberCount,
                        centerX,
                        centerZ,
                        scale,
                        morphology);
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
                    "AUTH-0092 isolation fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerZ,
            double scale,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth92",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(scale),
                        360.0 * scale,
                        48.0,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0 * scale,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0 * scale);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
                centerZ,
                320.0,
                500.0 * scale,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0 * scale,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor(double scale) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0 * scale,
                48.0,
                64.0,
                24.0 * scale,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0 * scale);
    }

    private record Fixture(
            SkyIslandPublishedAuthoredRealizationBinding binding) {}
}
