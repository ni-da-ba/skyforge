package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSetTest {

    @Test
    void callerOrderCanonicalizesByAcknowledgementSequence() {
        var a = acknowledgement("alpha", 3801L, succeeded());
        var b = acknowledgement("beta", 3802L, failed());

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(b, a));

        assertEquals(List.of(a, b), set.acknowledgements());
        assertEquals(2, set.size());
        assertFalse(set.isEmpty());
    }

    @Test
    void admitReturnsNewImmutableSetAndRetainsOldSet() {
        var first = acknowledgement("immutable-a", 3810L, succeeded());
        var second = acknowledgement("immutable-b", 3811L, failed());

        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();
        var one = empty.admit(first);
        var two = one.admit(second);

        assertTrue(empty.isEmpty());
        assertEquals(List.of(first), one.acknowledgements());
        assertEquals(List.of(first, second), two.acknowledgements());
        assertThrows(UnsupportedOperationException.class, () -> two.acknowledgements().clear());
    }

    @Test
    void exactReplayFailsClosed() {
        var acknowledgement = acknowledgement("replay", 3820L, succeeded());
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(acknowledgement));

        assertThrows(IllegalArgumentException.class, () -> set.admit(acknowledgement));
    }

    @Test
    void duplicateSameOutcomeForSameTicketFailsClosed() {
        var ticket = ticket("duplicate");
        var first = acknowledgement(ticket, 3830L, succeeded(), "storage-proof:first");
        var second = acknowledgement(ticket, 3831L, succeeded(), "storage-proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                .of(List.of(first, second)));
    }

    @Test
    void contradictoryOutcomeForSameTicketFailsClosedWithoutWinnerSelection() {
        var ticket = ticket("contradiction");
        var success = acknowledgement(ticket, 3840L, succeeded(), "storage-proof:success");
        var failure = acknowledgement(ticket, 3841L, failed(), "storage-proof:failure");

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(success));

        assertThrows(IllegalArgumentException.class, () -> set.admit(failure));
        assertEquals(success, set.forTicket(ticket.id()).orElseThrow());
    }

    @Test
    void acknowledgementSequenceReuseAcrossDifferentTicketsFailsClosed() {
        var first = acknowledgement("sequence-a", 3850L, succeeded());
        var second = acknowledgement("sequence-b", 3850L, failed());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                .of(List.of(first, second)));
    }

    @Test
    void exactTicketLookupDoesNotSelectByOutcomeOrSequence() {
        var first = acknowledgement("lookup-a", 3860L, succeeded());
        var second = acknowledgement("lookup-b", 3861L, failed());

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first, second));

        assertEquals(first, set.forTicket(first.ticket().id()).orElseThrow());
        assertEquals(second, set.forTicket(second.ticket().id()).orElseThrow());
        assertTrue(set.forTicket(ticket("lookup-missing").id()).isEmpty());
    }

    @Test
    void invalidSchemaAndNullEntriesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
                                2,
                                List.of()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .SCHEMA_VERSION,
                                Arrays.asList(
                                        (SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement)
                                                null)));
    }

    @Test
    void publicSurfaceHasNoReplaceLatestWinnerOrUpsertOperation() {
        var names =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .toList();

        assertFalse(names.contains("replace"));
        assertFalse(names.contains("latest"));
        assertFalse(names.contains("winner"));
        assertFalse(names.contains("upsert"));
        assertTrue(names.contains("admit"));
        assertTrue(names.contains("forTicket"));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            String targetKey,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                    outcome) {
        return acknowledgement(
                ticket(targetKey),
                acknowledgementSequence,
                outcome,
                "storage-proof:" + targetKey + ":" + outcome);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                    ticket,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                    outcome,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new Attestation(1, ticket.id(), outcome, evidenceToken),
                        acknowledgementSequence);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    succeeded() {
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                .SUCCEEDED;
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    failed() {
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                .FAILED;
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
                        3750L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);

        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                .issue(validation, 3775L);
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
