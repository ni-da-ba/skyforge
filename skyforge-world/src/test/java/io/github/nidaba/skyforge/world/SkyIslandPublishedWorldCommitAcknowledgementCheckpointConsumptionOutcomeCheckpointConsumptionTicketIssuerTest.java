package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuerTest {

    @Test
    void currentPreparedConsumptionIssuesExactAdmissionTicket() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        var ticket = issuer.issue(f.current(), 3401L);

        assertEquals(3401L, ticket.id().ticketSequence());
        assertEquals(f.prepared().id(), ticket.id().preparedConsumptionId());
        assertEquals(f.prepared(), ticket.preparedConsumption());
        assertEquals(f.prepared().checkpointId(), ticket.checkpointId());
        assertEquals(f.prepared().targetId(), ticket.targetId());
        assertEquals(f.current(), ticket.admissionValidation());
        assertTrue(ticket.id().canonicalToken().startsWith("sfackcpoutticket:v1:"));
        assertTrue(ticket.id().canonicalToken().contains(f.prepared().id().canonicalToken()));
    }

    @Test
    void explicitTicketSequenceIsIndependentAdmissionIdentityAxis() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        var first = issuer.issue(f.current(), 3410L);
        var second = issuer.issue(f.current(), 3411L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.preparedConsumption(), second.preparedConsumption());
    }

    @Test
    void staleAndInactivePreparedConsumptionCannotIssueTicket() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        assertThrows(IllegalStateException.class, () -> issuer.issue(f.stale(), 3420L));
        assertThrows(IllegalStateException.class, () -> issuer.issue(f.inactive(), 3421L));
    }

    @Test
    void invalidTicketSchemaAndSequenceFailClosed() {
        Fixture f = fixture();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                                2,
                                1L,
                                f.prepared().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                                1,
                                0L,
                                f.prepared().id()));
    }

    @Test
    void ticketRejectsIdentityForAnotherPreparedConsumption() {
        Fixture f = fixture();
        var otherTarget =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "secondary");
        var otherPrepared =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer()
                        .prepare(f.prepared().preparationValidation(), 3431L, otherTarget);
        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                        .of(3430L, otherPrepared);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
                                wrongId,
                                f.current()));
    }

    @Test
    void ticketCapturesAdmissionWithoutIoOutcomeClaim() {
        Fixture f = fixture();
        var ticket =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                        .issue(f.current(), 3440L);

        var componentNames =
                Arrays.stream(ticket.getClass().getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();

        assertEquals(java.util.List.of("id", "admissionValidation"), componentNames);
        assertFalse(componentNames.contains("success"));
        assertFalse(componentNames.contains("outcome"));
        assertFalse(componentNames.contains("persisted"));
        assertFalse(componentNames.contains("durable"));
    }

    @Test
    void issuerHasNoRawPreparationRefreshRetryOrIoShortcut() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("issue", methods.get(0).getName());
        assertEquals(2, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                        .class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[1]);
    }

    private static Fixture fixture() {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, set);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        binder.validate(binding, firstState),
                        3301L,
                        target);

        return new Fixture(
                prepared,
                preparer.validateForExecution(prepared, firstState),
                preparer.validateForExecution(prepared, secondState),
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()));
    }

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                    prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    inactive) {}
}
