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
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinderTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void initialActivationAndBindingCaptureExactCheckpoint() {
        Fixture fixture = fixture(67001L);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(fixture.firstCheckpoint());
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();

        var binding = binder.bind(state);
        var validation = binder.validate(binding, state);

        assertEquals(fixture.firstCheckpoint(), binding.checkpoint());
        assertEquals(fixture.firstCheckpoint().id(), binding.checkpointId());
        assertTrue(binding.canonicalToken().startsWith("sfackcpbinding:v1:"));
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT,
                validation.status());
        assertTrue(validation.current());
        validation.requireCurrent();
        assertEquals(
                fixture.firstAcknowledgement(),
                binding.forTicket(fixture.firstAcknowledgement().ticket().id()).orElseThrow());
    }

    @Test
    void inactiveBindFailsExplicitly() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder()
                                .bind(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                                                .inactive()));
    }

    @Test
    void replacementRequiresExactExpectedIdentityAndStrictlyHigherRevision() {
        Fixture fixture = fixture(67002L);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(fixture.firstCheckpoint());

        assertThrows(
                IllegalStateException.class,
                () ->
                        firstState.replace(
                                fixture.secondCheckpoint().id(),
                                fixture.secondCheckpoint()));

        var sameRevisionDifferentContents =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(
                        fixture.firstCheckpoint().id().checkpointRevision(),
                        fixture.secondCheckpoint().acknowledgementSet());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        firstState.replace(
                                fixture.firstCheckpoint().id(),
                                sameRevisionDifferentContents));

        var secondState =
                firstState.replace(
                        fixture.firstCheckpoint().id(),
                        fixture.secondCheckpoint());

        assertEquals(fixture.secondCheckpoint(), secondState.requireActive());
        assertEquals(fixture.firstCheckpoint(), firstState.requireActive());
    }

    @Test
    void oldBindingBecomesStaleButRetainsOldCheckpointAndLookup() {
        Fixture fixture = fixture(67003L);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(fixture.firstCheckpoint());
        var oldBinding = binder.bind(firstState);
        var secondState =
                firstState.replace(
                        fixture.firstCheckpoint().id(),
                        fixture.secondCheckpoint());

        var stale = binder.validate(oldBinding, secondState);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                stale.status());
        assertFalse(stale.current());
        assertEquals(
                Optional.of(fixture.secondCheckpoint().id()),
                stale.currentCheckpointId());
        assertThrows(IllegalStateException.class, stale::requireCurrent);

        assertEquals(fixture.firstCheckpoint().id(), oldBinding.checkpointId());
        assertTrue(
                oldBinding
                        .forTicket(fixture.secondAcknowledgement().ticket().id())
                        .isEmpty());
        assertTrue(
                secondState
                        .requireActive()
                        .forTicket(fixture.secondAcknowledgement().ticket().id())
                        .isPresent());
    }

    @Test
    void inactiveValidationIsDistinctFromStale() {
        Fixture fixture = fixture(67004L);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding =
                binder.bind(
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                                .activateInitial(fixture.firstCheckpoint()));

        var inactive =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive());

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                inactive.status());
        assertTrue(inactive.currentCheckpointId().isEmpty());
        assertThrows(IllegalStateException.class, inactive::requireCurrent);
    }

    @Test
    void impossibleValidationTuplesFailClosed() {
        Fixture fixture = fixture(67005L);
        var binding =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding.of(
                        fixture.firstCheckpoint());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT,
                                Optional.empty()));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                                Optional.of(binding.checkpointId())));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                                Optional.of(fixture.secondCheckpoint().id())));
    }

    @Test
    void bindingSchemaAndActivationReentryFailClosed() {
        Fixture fixture = fixture(67006L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding(
                                2,
                                fixture.firstCheckpoint()));

        var active =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(fixture.firstCheckpoint());
        assertThrows(
                IllegalStateException.class,
                () -> active.activateInitial(fixture.secondCheckpoint()));
    }

    @Test
    void binderPublicSurfaceHasNoRefreshRebindLatestOrRetry() {
        List<String> names =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder.class
                                        .getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .toList();

        assertEquals(List.of("bind", "validate").stream().sorted().toList(), names.stream().sorted().toList());
        assertFalse(names.contains("refresh"));
        assertFalse(names.contains("rebind"));
        assertFalse(names.contains("latest"));
        assertFalse(names.contains("retry"));
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(rootSeed, 1801L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(rootSeed + 1L, 1802L);

        var firstSet =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var secondSet = firstSet.admit(second);

        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, firstSet);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, secondSet);

        assertNotEquals(firstCheckpoint.id(), secondCheckpoint.id());
        return new Fixture(first, second, firstCheckpoint, secondCheckpoint);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new TestAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "proof:" + rootSeed),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 180L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1700L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1750L);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
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
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth67",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
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

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgement firstAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgement secondAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint secondCheckpoint) {}
}
