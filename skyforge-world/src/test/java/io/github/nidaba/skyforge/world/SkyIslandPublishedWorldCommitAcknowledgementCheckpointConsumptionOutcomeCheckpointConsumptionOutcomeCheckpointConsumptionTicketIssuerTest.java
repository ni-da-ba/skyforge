package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuerTest {

    @Test
    void currentPreparedConsumptionIssuesExactAdmissionTicket() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        var ticket = issuer.issue(f.current(), 4601L);

        assertEquals(4601L, ticket.id().ticketSequence());
        assertEquals(f.prepared().id(), ticket.id().preparedConsumptionId());
        assertEquals(f.prepared(), ticket.preparedConsumption());
        assertEquals(f.prepared().checkpointId(), ticket.checkpointId());
        assertEquals(f.prepared().targetId(), ticket.targetId());
        assertEquals(f.current(), ticket.admissionValidation());
        assertTrue(ticket.id().canonicalToken().startsWith("sfackcpoutcpoutticket:v1:"));
        assertTrue(ticket.id().canonicalToken().contains(f.prepared().id().canonicalToken()));
    }

    @Test
    void explicitTicketSequenceIsIndependentAdmissionIdentityAxis() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        var first = issuer.issue(f.current(), 4610L);
        var second = issuer.issue(f.current(), 4611L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.preparedConsumption(), second.preparedConsumption());
    }

    @Test
    void staleAndInactivePreparedConsumptionCannotIssueTicket() {
        Fixture f = fixture();
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer();

        assertThrows(IllegalStateException.class, () -> issuer.issue(f.stale(), 4620L));
        assertThrows(IllegalStateException.class, () -> issuer.issue(f.inactive(), 4621L));
    }

    @Test
    void invalidTicketSchemaAndSequenceFailClosed() {
        Fixture f = fixture();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                                2,
                                1L,
                                f.prepared().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                                1,
                                0L,
                                f.prepared().id()));
    }

    @Test
    void ticketRejectsIdentityForAnotherPreparedConsumption() {
        Fixture f = fixture();
        var otherTarget =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "secondary");
        var otherPrepared =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer()
                        .prepare(f.prepared().preparationValidation(), 4631L, otherTarget);
        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                        .of(4630L, otherPrepared);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
                                wrongId,
                                f.current()));
    }

    @Test
    void ticketCapturesAdmissionWithoutIoOutcomeClaim() {
        Fixture f = fixture();
        var ticket =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                        .issue(f.current(), 4640L);

        var componentNames =
                Arrays.stream(ticket.getClass().getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();

        assertEquals(List.of("id", "admissionValidation"), componentNames);
        assertFalse(componentNames.contains("success"));
        assertFalse(componentNames.contains("outcome"));
        assertFalse(componentNames.contains("persisted"));
        assertFalse(componentNames.contains("durable"));
    }

    @Test
    void issuerHasNoRawPreparationRefreshRetryOrIoShortcut() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("issue", methods.get(0).getName());
        assertEquals(2, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                        .class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[1]);
    }

    private static Fixture fixture() {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, set);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(binder.validate(binding, firstState), 4650L, target);

        return new Fixture(
                prepared,
                preparer.validateForExecution(prepared, firstState),
                preparer.validateForExecution(prepared, secondState),
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()));
    }

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                    prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                    inactive) {}
}
