package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldViewTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void admitsSeparatedPublicationsInCanonicalRegionalOrder() {
        SkyIslandCompiledWorldPublication high =
                publication(-1L, 1L, 1_500.0);
        SkyIslandCompiledWorldPublication low =
                publication(59001L, 1L, -1_500.0);

        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(high, low));

        assertEquals(2, view.publicationCount());
        assertEquals(2, view.volumeCount());
        assertEquals(low.id(), view.publications().get(0).id());
        assertEquals(high.id(), view.publications().get(1).id());
        assertEquals(List.of(low.id(), high.id()), view.viewIdentity());
        assertEquals(low.id(), view.entries().get(0).publicationId());
        assertEquals(high.id(), view.entries().get(1).publicationId());
        for (SkyIslandPublishedWorldEntry entry : view.entries()) {
            assertEquals(entry.volume().id(), entry.supportCertificate().volumeId());
            assertTrue(entry.supportCertificate().queryBoundsContainSupport());
        }

        SkyIslandPublishedWorldView repeated =
                SkyIslandPublishedWorldView.of(List.of(low, high));
        assertEquals(view.viewIdentity(), repeated.viewIdentity());
        assertEquals(
                view.entries().stream().map(entry -> entry.volume().id()).toList(),
                repeated.entries().stream().map(entry -> entry.volume().id()).toList());
    }

    @Test
    void broadQueryBoundsMayOverlapWhenCertifiedSupportIsDisjoint() {
        SkyIslandCompiledWorldPublication first =
                publication(59013L, 1L, 0.0);
        SkyIslandCompiledWorldPublication second =
                publication(59014L, 1L, 680.0);

        SkyIslandWorldVolume firstVolume = first.catalog().volumes().get(0);
        SkyIslandWorldVolume secondVolume = second.catalog().volumes().get(0);
        assertTrue(firstVolume.bounds().intersects(secondVolume.bounds()));

        WorldBounds firstSupport =
                first.supportCertificates().get(0).supportBounds();
        WorldBounds secondSupport =
                second.supportCertificates().get(0).supportBounds();
        assertTrue(!firstSupport.intersects(secondSupport));

        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(second, first));
        assertEquals(2, view.publicationCount());
        assertEquals(2, view.volumeCount());
    }

    @Test
    void duplicateRegionalRootRequiresExplicitSingleVersionSelection() {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(59003L, 0.0);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication first = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication second = publisher.publish(compilation, 2L);

        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SkyIslandPublishedWorldView.of(List.of(first, second)));
        assertTrue(failure.getMessage().contains("exactly one publication per regional root"));
    }

    @Test
    void crossPublicationCertifiedSupportOverlapFailsClosed() {
        SkyIslandCompiledWorldPublication first =
                publication(59004L, 1L, 0.0);
        SkyIslandCompiledWorldPublication second =
                publication(59005L, 1L, 0.0);

        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SkyIslandPublishedWorldView.of(List.of(first, second)));
        assertTrue(failure.getMessage().contains("certified support overlaps or touches"));
    }

    @Test
    void regionQueryCarriesPublicationAndExactCertificateProvenance() {
        SkyIslandCompiledWorldPublication left =
                publication(59006L, 1L, -1_500.0);
        SkyIslandCompiledWorldPublication right =
                publication(59007L, 1L, 1_500.0);
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(right, left));

        SkyIslandWorldVolume leftVolume = left.catalog().volumes().get(0);
        List<SkyIslandPublishedWorldEntry> hits = view.query(leftVolume.bounds());

        assertEquals(1, hits.size());
        SkyIslandPublishedWorldEntry hit = hits.get(0);
        assertEquals(left.id(), hit.publicationId());
        assertEquals(leftVolume, hit.volume());
        assertEquals(
                left.compilation().supportBundle()
                        .certificateFor(leftVolume.id())
                        .orElseThrow(),
                hit.supportCertificate());
        assertEquals(
                left.compilation().supportBundle()
                        .certificateFor(leftVolume.id())
                        .orElseThrow()
                        .supportBounds(),
                hit.certifiedSupportBounds());
    }

    @Test
    void replacementIsExplicitMonotonicAndOriginalViewRemainsImmutable() {
        SkyIslandAcceptedConvergenceCompilation leftCompilation =
                acceptedCompilation(59008L, -1_500.0);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication leftV1 =
                publisher.publish(leftCompilation, 1L);
        SkyIslandCompiledWorldPublication leftV2 =
                publisher.publish(leftCompilation, 2L);
        SkyIslandCompiledWorldPublication right =
                publication(59009L, 1L, 1_500.0);

        SkyIslandPublishedWorldView original =
                SkyIslandPublishedWorldView.of(List.of(leftV1, right));
        SkyIslandPublishedWorldView revised =
                original.replace(leftV1.id(), leftV2);

        assertEquals(leftV1.id(), original.publicationForRoot(59008L).orElseThrow().id());
        assertEquals(leftV2.id(), revised.publicationForRoot(59008L).orElseThrow().id());
        assertNotEquals(original.viewIdentity(), revised.viewIdentity());
        assertEquals(original.volumeCount(), revised.volumeCount());

        assertThrows(
                IllegalStateException.class,
                () -> revised.replace(leftV1.id(), publisher.publish(leftCompilation, 3L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> original.replace(leftV1.id(), leftV1));
        assertThrows(
                IllegalArgumentException.class,
                () -> original.replace(leftV1.id(), right));
    }

    @Test
    void replacementIsReadmittedAgainstOtherPublishedSupport() {
        SkyIslandCompiledWorldPublication left =
                publication(59010L, 1L, -1_500.0);
        SkyIslandCompiledWorldPublication right =
                publication(59011L, 1L, 1_500.0);
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(left, right));

        SkyIslandCompiledWorldPublication collidingReplacement =
                publication(59010L, 2L, 1_500.0);

        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> view.replace(left.id(), collidingReplacement));
        assertTrue(failure.getMessage().contains("certified support overlaps or touches"));
        assertEquals(left.id(), view.publicationForRoot(59010L).orElseThrow().id());
    }

    @Test
    void schemaAndEmptySetAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedWorldView(2, List.of(publication(59012L, 1L, 0.0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandPublishedWorldView.of(List.of()));
    }

    private static SkyIslandCompiledWorldPublication publication(
            long rootSeed, long revision, double centerX) {
        return new SkyIslandCompiledWorldPublisher()
                .publish(acceptedCompilation(rootSeed, centerX), revision);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed, double centerX) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, centerX, morphology);
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
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        assertEquals(
                SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS,
                convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, double centerX, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth59",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
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
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }
}
