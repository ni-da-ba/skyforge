package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparerTest {

    @Test
    void targetIdentityIsExplicitCanonicalAndFailClosed() {
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");

        assertEquals("audit", target.namespace());
        assertEquals("primary", target.key());
        assertTrue(target.canonicalToken().startsWith("sfackcpoutcpouttarget:v1:"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId(
                                2, "audit", "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of(" ", "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of(" audit ", "primary"));
    }

    @Test
    void currentValidationPreparesExactCheckpointAndTarget() {
        Fixture f = fixture();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "east");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();

        var prepared = preparer.prepare(f.currentValidation(), 4401L, target);

        assertEquals(4401L, prepared.id().preparationSequence());
        assertEquals(f.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(f.firstCheckpoint(), prepared.checkpoint());
        assertEquals(target, prepared.targetId());
        assertEquals(f.firstBinding(), prepared.binding());
        assertEquals(f.currentValidation(), prepared.preparationValidation());
        assertTrue(prepared.id().canonicalToken().startsWith("sfackcpoutcpoutprep:v1:"));
    }

    @Test
    void identitySeparatesSequenceTargetAndCheckpoint() {
        Fixture f = fixture();
        var targetA =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "east");
        var targetB =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "west");

        var base =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4410L, f.firstBinding(), targetA);
        var sequenceChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4411L, f.firstBinding(), targetA);
        var targetChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4410L, f.firstBinding(), targetB);
        var secondBinding =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder()
                        .bind(f.secondState());
        var checkpointChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4410L, secondBinding, targetA);

        assertNotEquals(base, sequenceChanged);
        assertNotEquals(base, targetChanged);
        assertNotEquals(base, checkpointChanged);
    }

    @Test
    void staleAndInactiveValidationCannotPrepare() {
        Fixture f = fixture();
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");

        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(f.staleValidation(), 4420L, target));
        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(f.inactiveValidation(), 4421L, target));
    }

    @Test
    void preparedConsumptionRejectsMismatchedIdentityBindingAndValidation() {
        Fixture f = fixture();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4430L, f.firstBinding(), target);
        var secondBinding =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder()
                        .bind(f.secondState());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                                id,
                                secondBinding,
                                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder()
                                        .validate(secondBinding, f.secondState())));

        var secondId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(4431L, secondBinding, target);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                                secondId,
                                secondBinding,
                                f.currentValidation()));
    }

    @Test
    void executionValidationTracksCurrentStaleAndInactiveWithoutChangingPreparedUnit() {
        Fixture f = fixture();
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var prepared = preparer.prepare(f.currentValidation(), 4440L, target);

        var current = preparer.validateForExecution(prepared, f.firstState());
        assertTrue(current.current());
        current.requireCurrent();

        var stale = preparer.validateForExecution(prepared, f.secondState());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus.STALE,
                stale.status());
        assertThrows(IllegalStateException.class, stale::requireCurrent);

        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus.INACTIVE,
                inactive.status());
        assertThrows(IllegalStateException.class, inactive::requireCurrent);

        assertEquals(f.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(target, prepared.targetId());
    }

    @Test
    void preparerHasNoRawBindingOrIoShortcut() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(2, methods.size());
        var prepare =
                methods.stream().filter(m -> m.getName().equals("prepare")).findFirst().orElseThrow();
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                        .class,
                prepare.getParameterTypes()[0]);
        assertFalse(
                Arrays.stream(prepare.getParameterTypes())
                        .anyMatch(
                                type ->
                                        type
                                                        == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                                                                .class
                                                || type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level")));
    }

    private static Fixture fixture() {
        var first = acknowledgement("first", 4451L);
        var second = acknowledgement("second", 4452L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);

        return new Fixture(
                first,
                second,
                firstCheckpoint,
                secondCheckpoint,
                firstState,
                secondState,
                binding,
                binder.validate(binding, firstState),
                binder.validate(binding, secondState),
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()));
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
                        4460L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(preparer.validateForExecution(prepared, upstreamState), 4470L);
    }

    private record Attestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    firstAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    secondAcknowledgement,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    secondCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                    firstState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                    secondState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                    firstBinding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    currentValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    staleValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                    inactiveValidation) {}
}
