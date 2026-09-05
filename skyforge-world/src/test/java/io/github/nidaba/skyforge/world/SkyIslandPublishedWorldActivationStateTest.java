package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

final class SkyIslandPublishedWorldActivationStateTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void snapshotIdentityBindsExactCanonicalView() {
        SkyIslandCompiledWorldPublication unsignedHigh =
                publication(-1L, 1L, 1_500.0);
        SkyIslandCompiledWorldPublication unsignedLow =
                publication(60001L, 1L, -1_500.0);
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(
                        List.of(unsignedHigh, unsignedLow));

        SkyIslandPublishedWorldSnapshot first =
                SkyIslandPublishedWorldSnapshot.of(7L, view);
        SkyIslandPublishedWorldSnapshot repeated =
                SkyIslandPublishedWorldSnapshot.of(7L, view);

        assertEquals(view.viewIdentity(), first.id().viewIdentity());
        assertEquals(first.id(), repeated.id());
        assertEquals(first.id().canonicalToken(), repeated.id().canonicalToken());
        assertTrue(first.id().canonicalToken().startsWith("sfviewsnap:v1:"));
        assertEquals(unsignedLow.id(), first.id().viewIdentity().get(0));
        assertEquals(unsignedHigh.id(), first.id().viewIdentity().get(1));
        assertEquals(view.publicationCount(), first.publicationCount());
        assertEquals(view.volumeCount(), first.volumeCount());
    }

    @Test
    void snapshotRejectsIdentityFromDifferentAdmittedView() {
        SkyIslandPublishedWorldView first =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(60002L, 1L, -1_500.0)));
        SkyIslandPublishedWorldView second =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(60003L, 1L, 1_500.0)));

        SkyIslandPublishedWorldSnapshotId secondId =
                SkyIslandPublishedWorldSnapshotId.of(1L, second);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedWorldSnapshot(secondId, first));
    }

    @Test
    void snapshotIdentityRejectsInvalidSchemaRevisionAndNoncanonicalViewOrder() {
        SkyIslandCompiledWorldPublication unsignedHigh =
                publication(-1L, 1L, 1_500.0);
        SkyIslandCompiledWorldPublication unsignedLow =
                publication(60004L, 1L, -1_500.0);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldSnapshotId(
                                2,
                                1L,
                                List.of(unsignedLow.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldSnapshotId(
                                SkyIslandPublishedWorldSnapshotId.SCHEMA_VERSION,
                                0L,
                                List.of(unsignedLow.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldSnapshotId(
                                SkyIslandPublishedWorldSnapshotId.SCHEMA_VERSION,
                                1L,
                                List.of(unsignedHigh.id(), unsignedLow.id())));
    }

    @Test
    void initialActivationIsExplicitImmutableAndQueriesExactSnapshot() {
        SkyIslandCompiledWorldPublication left =
                publication(60005L, 1L, -1_500.0);
        SkyIslandCompiledWorldPublication right =
                publication(60006L, 1L, 1_500.0);
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(right, left));
        SkyIslandPublishedWorldActivationState inactive =
                SkyIslandPublishedWorldActivationState.inactive();

        assertFalse(inactive.active());
        assertTrue(inactive.activeSnapshot().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> inactive.query(left.catalog().volumes().get(0).bounds()));

        SkyIslandPublishedWorldActivationState active =
                inactive.activateInitial(view, 10L);

        assertFalse(inactive.active());
        assertTrue(active.active());
        assertEquals(10L, active.requireActive().id().snapshotRevision());
        assertEquals(view.viewIdentity(), active.requireActive().id().viewIdentity());

        WorldBounds query = left.catalog().volumes().get(0).bounds();
        assertEquals(view.query(query), active.query(query));
        assertEquals(
                left.id(),
                active.query(query).get(0).publicationId());

        assertThrows(
                IllegalStateException.class,
                () -> active.activateInitial(view, 11L));
    }

    @Test
    void replacementIsMonotonicCompareAndSwapAndLeavesPriorStateUntouched() {
        SkyIslandAcceptedConvergenceCompilation leftCompilation =
                acceptedCompilation(60007L, -1_500.0);
        SkyIslandCompiledWorldPublisher publisher =
                new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication leftV1 =
                publisher.publish(leftCompilation, 1L);
        SkyIslandCompiledWorldPublication leftV2 =
                publisher.publish(leftCompilation, 2L);
        SkyIslandCompiledWorldPublication right =
                publication(60008L, 1L, 1_500.0);

        SkyIslandPublishedWorldView originalView =
                SkyIslandPublishedWorldView.of(List.of(leftV1, right));
        SkyIslandPublishedWorldView replacementView =
                originalView.replace(leftV1.id(), leftV2);

        SkyIslandPublishedWorldActivationState initial =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(originalView, 20L);
        SkyIslandPublishedWorldSnapshotId initialId =
                initial.requireActive().id();

        SkyIslandPublishedWorldActivationState revised =
                initial.replace(initialId, replacementView, 21L);

        assertEquals(20L, initial.requireActive().id().snapshotRevision());
        assertEquals(originalView.viewIdentity(), initial.requireActive().id().viewIdentity());
        assertEquals(21L, revised.requireActive().id().snapshotRevision());
        assertEquals(replacementView.viewIdentity(), revised.requireActive().id().viewIdentity());
        assertNotEquals(initial.requireActive().id(), revised.requireActive().id());

        assertThrows(
                IllegalStateException.class,
                () -> revised.replace(initialId, replacementView, 22L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        initial.replace(
                                initialId,
                                replacementView,
                                initialId.snapshotRevision()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        SkyIslandPublishedWorldActivationState.inactive()
                                .replace(initialId, replacementView, 21L));
    }

    @Test
    void snapshotIdentityChangesWhenViewIdentityChangesAtSameSnapshotRevision() {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(60009L, 0.0);
        SkyIslandCompiledWorldPublisher publisher =
                new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);

        SkyIslandPublishedWorldSnapshot first =
                SkyIslandPublishedWorldSnapshot.of(
                        30L,
                        SkyIslandPublishedWorldView.of(List.of(v1)));
        SkyIslandPublishedWorldSnapshot second =
                SkyIslandPublishedWorldSnapshot.of(
                        30L,
                        SkyIslandPublishedWorldView.of(List.of(v2)));

        assertEquals(first.id().snapshotRevision(), second.id().snapshotRevision());
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.id().canonicalToken(), second.id().canonicalToken());
    }

    private static SkyIslandCompiledWorldPublication publication(
            long rootSeed,
            long publicationRevision,
            double centerX) {
        return new SkyIslandCompiledWorldPublisher()
                .publish(
                        acceptedCompilation(rootSeed, centerX),
                        publicationRevision);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            double centerX) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, centerX, morphology);
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
        assertEquals(
                SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS,
                convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            double centerX,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth60",
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
