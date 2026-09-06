package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinderTest {

    @Test
    void initialActivationAndBindingCaptureExactOutcomeCheckpointConsumptionOutcomeCheckpoint() {
        Fixture f = fixture();
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(state);
        var validation = binder.validate(binding, state);

        assertEquals(f.firstCheckpoint(), binding.checkpoint());
        assertEquals(f.firstCheckpoint().id(), binding.checkpointId());
        assertTrue(binding.canonicalToken().startsWith("sfackcpoutcpoutbinding:v1:"));
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus.CURRENT,
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
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder()
                                .bind(
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                                .inactive()));
    }

    @Test
    void replacementRequiresExactExpectedIdentityAndStrictHigherRevision() {
        Fixture f = fixture();
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());

        assertThrows(
                IllegalStateException.class,
                () -> firstState.replace(f.secondCheckpoint().id(), f.secondCheckpoint()));

        var sameRevisionChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(
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
        Fixture f = fixture();
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        var oldBinding = binder.bind(firstState);
        var secondState =
                firstState.replace(f.firstCheckpoint().id(), f.secondCheckpoint());

        var stale = binder.validate(oldBinding, secondState);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus.STALE,
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
        Fixture f = fixture();
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding =
                binder.bind(
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()
                                .activateInitial(f.firstCheckpoint()));
        var validation =
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus.INACTIVE,
                validation.status());
        assertTrue(validation.currentCheckpointId().isEmpty());
        assertThrows(IllegalStateException.class, validation::requireCurrent);
    }

    @Test
    void impossibleValidationTuplesFailClosed() {
        Fixture f = fixture();
        var binding =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                        .of(f.firstCheckpoint());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .CURRENT,
                                Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .STALE,
                                Optional.of(binding.checkpointId())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation(
                                binding,
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                                        .INACTIVE,
                                Optional.of(f.secondCheckpoint().id())));
    }

    @Test
    void schemaReentryAndHiddenRefreshSurfaceFailClosed() {
        Fixture f = fixture();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding(
                                2, f.firstCheckpoint()));

        var active =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(f.firstCheckpoint());
        assertThrows(
                IllegalStateException.class,
                () -> active.activateInitial(f.secondCheckpoint()));

        List<String> names =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder
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

    private static Fixture fixture() {
        var first = acknowledgement("first", 4201L);
        var second = acknowledgement("second", 4202L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        return new Fixture(
                first,
                second,
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one),
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(String targetKey, long sequence) {
        var ticket = ticket(targetKey);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new Attestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                "storage-proof:" + targetKey),
                        sequence);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket(String targetKey) {
        var upstreamSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var upstreamCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, upstreamSet);
        var upstreamState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(upstreamCheckpoint);
        var upstreamBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var upstreamBinding = upstreamBinder.bind(upstreamState);

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        upstreamBinder.validate(upstreamBinding, upstreamState),
                        4175L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, upstreamState);

        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 4180L);
    }

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    firstAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    secondAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    secondCheckpoint) {}

    private record Attestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}
}
