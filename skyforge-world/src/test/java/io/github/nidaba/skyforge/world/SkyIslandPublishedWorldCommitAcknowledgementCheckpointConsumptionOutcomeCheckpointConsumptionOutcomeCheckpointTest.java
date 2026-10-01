package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointTest {

    @Test
    void publisherBindsExactCanonicalAcknowledgementSet() {
        var first = acknowledgement("alpha", 4001L);
        var second = acknowledgement("beta", 4002L);
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second, first));

        var checkpoint =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher()
                        .publish(set, 1L);

        assertEquals(1L, checkpoint.id().checkpointRevision());
        assertEquals(set, checkpoint.acknowledgementSet());
        assertEquals(List.of(first.id(), second.id()), checkpoint.id().acknowledgementIdentity());
        assertEquals(2, checkpoint.size());
        assertFalse(checkpoint.isEmpty());
        assertEquals(first, checkpoint.forTicket(first.ticket().id()).orElseThrow());
        assertTrue(checkpoint.id().canonicalToken().startsWith("sfackcpoutcpoutcp:v1:"));
    }

    @Test
    void emptyValidatedSetCanBeCheckpointedExplicitly() {
        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();

        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, empty);

        assertTrue(checkpoint.isEmpty());
        assertEquals(List.of(), checkpoint.id().acknowledgementIdentity());
        assertTrue(checkpoint.id().canonicalToken().endsWith(":0"));
    }

    @Test
    void identityRejectsUnsupportedSchemaRevisionAndNoncanonicalIdentity() {
        var first = acknowledgement("schema-a", 4010L);
        var second = acknowledgement("schema-b", 4011L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                2,
                                1L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                0L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                1L,
                                List.of(second.id(), first.id())));
    }

    @Test
    void identityRejectsDuplicateTicketEvenWithDistinctSequences() {
        var ticket = ticket("duplicate");
        var first = acknowledgement(ticket, 4020L, "storage-proof:first");
        var second = acknowledgement(ticket, 4021L, "storage-proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                3L,
                                List.of(first.id(), second.id())));
    }

    @Test
    void checkpointRejectsIdentityFromDifferentSet() {
        var first = acknowledgement("forged-a", 4030L);
        var second = acknowledgement("forged-b", 4031L);
        var firstSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var secondSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second));

        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                        .of(4L, secondSet);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint(
                                wrongId,
                                firstSet));
    }

    @Test
    void revisionAndSetContentsAreIndependentCheckpointIdentityAxes() {
        var first = acknowledgement("identity-a", 4040L);
        var second = acknowledgement("identity-b", 4041L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);

        var sameSetRevisionOne =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(10L, one);
        var sameSetRevisionTwo =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(11L, one);
        var changedSetSameRevision =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(10L, two);

        assertNotEquals(sameSetRevisionOne.id(), sameSetRevisionTwo.id());
        assertNotEquals(sameSetRevisionOne.id(), changedSetSameRevision.id());
        assertEquals(
                sameSetRevisionOne.id().checkpointRevision(),
                changedSetSameRevision.id().checkpointRevision());
        assertNotEquals(
                sameSetRevisionOne.id().acknowledgementIdentity(),
                changedSetSameRevision.id().acknowledgementIdentity());
    }

    @Test
    void publisherSurfaceDoesNotExposeStorageOrReplicationOperations() {
        var publicMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, publicMethods.size());
        assertEquals("publish", publicMethods.get(0).getName());
        assertFalse(
                Arrays.stream(publicMethods.get(0).getParameterTypes())
                        .anyMatch(
                                type ->
                                        type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level")));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(String targetKey, long sequence) {
        return acknowledgement(ticket(targetKey), sequence, "storage-proof:" + targetKey);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                    ticket,
                            long sequence,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new Attestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                evidenceToken),
                        sequence);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket(String targetKey) {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();
        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = checkpointBinder.bind(state);

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        checkpointBinder.validate(binding, state),
                        3975L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);

        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 3980L);
    }

    private record Attestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}
}
