package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.*;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.*;
import io.github.nidaba.skyforge.recipes.skyisland.group.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinderTest {
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void initialActivationAndBindingCaptureExactOutcomeCheckpoint() {
        Fixture f = fixture(73001L);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(state);
        var validation = binder.validate(binding, state);

        assertEquals(f.firstCheckpoint(), binding.checkpoint());
        assertEquals(f.firstCheckpoint().id(), binding.checkpointId());
        assertTrue(binding.canonicalToken().startsWith("sfackcpoutbinding:v1:"));
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus.CURRENT,
                validation.status());
        assertTrue(validation.current());
        validation.requireCurrent();
        assertEquals(
                f.firstAcknowledgement(),
                binding.forTicket(f.firstAcknowledgement().ticket().id()).orElseThrow());
    }

    @Test
    void inactiveBindFailsExplicitly() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder()
                                .bind(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                                .inactive()));
    }

    @Test
    void replacementRequiresExactExpectedIdentityAndStrictHigherRevision() {
        Fixture f = fixture(73002L);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());

        assertThrows(
                IllegalStateException.class,
                () -> firstState.replace(f.secondCheckpoint().id(), f.secondCheckpoint()));

        var sameRevisionChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint.of(
                        f.firstCheckpoint().id().checkpointRevision(),
                        f.secondCheckpoint().acknowledgementSet());
        assertThrows(
                IllegalArgumentException.class,
                () -> firstState.replace(f.firstCheckpoint().id(), sameRevisionChanged));

        var secondState =
                firstState.replace(f.firstCheckpoint().id(), f.secondCheckpoint());
        assertEquals(f.firstCheckpoint(), firstState.requireActive());
        assertEquals(f.secondCheckpoint(), secondState.requireActive());
    }

    @Test
    void oldBindingBecomesStaleButRetainsOldOutcomeSet() {
        Fixture f = fixture(73003L);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        var oldBinding = binder.bind(firstState);
        var secondState =
                firstState.replace(f.firstCheckpoint().id(), f.secondCheckpoint());

        var stale = binder.validate(oldBinding, secondState);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus.STALE,
                stale.status());
        assertFalse(stale.current());
        assertEquals(Optional.of(f.secondCheckpoint().id()), stale.currentCheckpointId());
        assertThrows(IllegalStateException.class, stale::requireCurrent);
        assertEquals(f.firstCheckpoint().id(), oldBinding.checkpointId());
        assertTrue(oldBinding.forTicket(f.secondAcknowledgement().ticket().id()).isEmpty());
        assertTrue(
                secondState
                        .requireActive()
                        .forTicket(f.secondAcknowledgement().ticket().id())
                        .isPresent());
    }

    @Test
    void inactiveValidationIsDistinct() {
        Fixture f = fixture(73004L);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding =
                binder.bind(
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()
                                .activateInitial(f.firstCheckpoint()));
        var validation =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus.INACTIVE,
                validation.status());
        assertTrue(validation.currentCheckpointId().isEmpty());
        assertThrows(IllegalStateException.class, validation::requireCurrent);
    }

    @Test
    void impossibleValidationTuplesFailClosed() {
        Fixture f = fixture(73005L);
        var binding =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                        .of(f.firstCheckpoint());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .CURRENT,
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .STALE,
                                Optional.of(binding.checkpointId())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .INACTIVE,
                                Optional.of(f.secondCheckpoint().id())));
    }

    @Test
    void schemaReentryAndHiddenRefreshSurfaceFailClosed() {
        Fixture f = fixture(73006L);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding(
                                2, f.firstCheckpoint()));

        var active =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        assertThrows(
                IllegalStateException.class,
                () -> active.activateInitial(f.secondCheckpoint()));

        List<String> names =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .sorted()
                        .toList();
        assertEquals(List.of("bind", "validate"), names);
        assertFalse(names.contains("refresh"));
        assertFalse(names.contains("rebind"));
        assertFalse(names.contains("latest"));
        assertFalse(names.contains("retry"));
    }

    private static Fixture fixture(long rootSeed) {
        var first = outcomeAcknowledgement(rootSeed, 3001L);
        var second = outcomeAcknowledgement(rootSeed + 1L, 3002L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        return new Fixture(
                first,
                second,
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one),
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    outcomeAcknowledgement(long rootSeed, long sequence) {
        var ticket = ioTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                "io-proof:" + rootSeed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ioTicket(
            long rootSeed) {
        var upstream = upstreamAcknowledgement(rootSeed, 3001L);
        var upstreamSet = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(upstream));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, upstreamSet);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(activation);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica", "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(binder.validate(binding, activation), 2900L, target);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(preparer.validateForExecution(prepared, activation), 2950L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long rootSeed, long sequence) {
        var ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream:" + rootSeed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        var publication = new SkyIslandCompiledWorldPublisher().publish(compilation(rootSeed), 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 300L);
        var snapshotBinding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var preparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                preparer.prepare(
                        snapshotBinding,
                        2900L,
                        publication.catalog().volumes().get(0).bounds());
        return new SkyIslandPublishedWorldCommitTicketIssuer()
                .issue(preparer.validateForCommit(work, active), 2950L);
    }

    private static SkyIslandAcceptedConvergenceCompilation compilation(long seed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        var request = request(seed, morphology);
        var original = new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        var proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        var convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        assertEquals(SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS, convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long seed, ProviderMorphologySpec morphology) {
        var template =
                new SkyIslandGroupTemplate(
                        "auth73",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                seed,
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

    private record IoAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation {}

    private record UpstreamAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    firstAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    secondAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                    firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                    secondCheckpoint) {}
}
