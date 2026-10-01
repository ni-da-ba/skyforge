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
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldSnapshotBinderTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void bindCapturesExactActiveSnapshotAndQueriesItDirectly() {
        SkyIslandCompiledWorldPublication publication =
                publication(61001L, 1L, 0.0);
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(view, 11L);

        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();
        SkyIslandPublishedWorldSnapshotBinding binding =
                binder.bind(active);

        assertEquals(active.requireActive(), binding.snapshot());
        assertEquals(active.requireActive().id(), binding.snapshotId());
        assertTrue(binding.canonicalToken().startsWith("sfbinding:v1:sfviewsnap:v1:"));

        WorldBounds region = publication.catalog().volumes().get(0).bounds();
        assertEquals(active.query(region), binding.query(region));
    }

    @Test
    void bindFailsWhenActivationIsInactive() {
        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();

        assertThrows(
                IllegalStateException.class,
                () -> binder.bind(SkyIslandPublishedWorldActivationState.inactive()));
    }

    @Test
    void currentValidationNamesExactBoundSnapshot() {
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(61002L, 1L, 0.0)));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(view, 12L);
        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();
        SkyIslandPublishedWorldSnapshotBinding binding = binder.bind(active);

        SkyIslandPublishedWorldBindingValidation validation =
                binder.validate(binding, active);

        assertEquals(SkyIslandPublishedWorldBindingStatus.CURRENT, validation.status());
        assertTrue(validation.current());
        assertEquals(Optional.of(binding.snapshotId()), validation.currentSnapshotId());
        validation.requireCurrent();
    }

    @Test
    void capturedBindingNeverSilentlyRefreshesAfterActivationReplacement() {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(61003L, 0.0);
        SkyIslandCompiledWorldPublisher publisher =
                new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 =
                SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 =
                viewV1.replace(v1.id(), v2);

        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(viewV1, 20L);
        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();
        SkyIslandPublishedWorldSnapshotBinding binding =
                binder.bind(first);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 21L);

        assertEquals(first.requireActive().id(), binding.snapshotId());
        assertNotEquals(second.requireActive().id(), binding.snapshotId());

        WorldBounds region = v1.catalog().volumes().get(0).bounds();
        assertEquals(viewV1.query(region), binding.query(region));
        assertNotEquals(
                binding.query(region).get(0).publicationId(),
                second.query(region).get(0).publicationId());

        SkyIslandPublishedWorldBindingValidation stale =
                binder.validate(binding, second);
        assertEquals(SkyIslandPublishedWorldBindingStatus.STALE, stale.status());
        assertFalse(stale.current());
        assertEquals(Optional.of(second.requireActive().id()), stale.currentSnapshotId());
        assertThrows(IllegalStateException.class, stale::requireCurrent);
    }

    @Test
    void inactiveValidationIsDistinctFromStaleValidation() {
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(61004L, 1L, 0.0)));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(view, 30L);
        SkyIslandPublishedWorldSnapshotBinder binder =
                new SkyIslandPublishedWorldSnapshotBinder();
        SkyIslandPublishedWorldSnapshotBinding binding = binder.bind(active);

        SkyIslandPublishedWorldBindingValidation validation =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldActivationState.inactive());

        assertEquals(SkyIslandPublishedWorldBindingStatus.INACTIVE, validation.status());
        assertFalse(validation.current());
        assertTrue(validation.currentSnapshotId().isEmpty());
        assertThrows(IllegalStateException.class, validation::requireCurrent);
    }

    @Test
    void validationResultRejectsImpossibleStatusAndIdentityCombinations() {
        SkyIslandPublishedWorldView firstView =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(61005L, 1L, -1_500.0)));
        SkyIslandPublishedWorldView secondView =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(61006L, 1L, 1_500.0)));
        SkyIslandPublishedWorldSnapshot first =
                SkyIslandPublishedWorldSnapshot.of(40L, firstView);
        SkyIslandPublishedWorldSnapshot second =
                SkyIslandPublishedWorldSnapshot.of(41L, secondView);
        SkyIslandPublishedWorldSnapshotBinding binding =
                SkyIslandPublishedWorldSnapshotBinding.of(first);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldBindingValidation(
                                binding,
                                SkyIslandPublishedWorldBindingStatus.CURRENT,
                                Optional.of(second.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldBindingValidation(
                                binding,
                                SkyIslandPublishedWorldBindingStatus.STALE,
                                Optional.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldBindingValidation(
                                binding,
                                SkyIslandPublishedWorldBindingStatus.INACTIVE,
                                Optional.of(first.id())));
    }

    @Test
    void bindingSchemaIsExplicitAndSnapshotIdentityRemainsVisible() {
        SkyIslandPublishedWorldView view =
                SkyIslandPublishedWorldView.of(
                        List.of(publication(61007L, 1L, 0.0)));
        SkyIslandPublishedWorldSnapshot snapshot =
                SkyIslandPublishedWorldSnapshot.of(50L, view);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldSnapshotBinding(
                                2,
                                snapshot));

        SkyIslandPublishedWorldSnapshotBinding binding =
                SkyIslandPublishedWorldSnapshotBinding.of(snapshot);
        assertEquals(
                "sfbinding:v1:" + snapshot.id().canonicalToken(),
                binding.canonicalToken());
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
                        "auth61",
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
