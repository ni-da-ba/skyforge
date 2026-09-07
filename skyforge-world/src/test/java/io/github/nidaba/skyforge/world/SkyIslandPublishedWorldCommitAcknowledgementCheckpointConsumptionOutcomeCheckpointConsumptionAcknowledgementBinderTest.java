package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinderTest {

    @Test
    void externallyAttestedSuccessBindsExactTicketAndTarget() {
        Fixture f = fixture("primary");
        var attestation =
                new TestAttestation(
                        1,
                        f.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:success");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                        .bind(f.ticket(), attestation, 3601L);

        assertEquals(3601L, acknowledgement.id().acknowledgementSequence());
        assertEquals(f.ticket().id(), acknowledgement.id().ticketId());
        assertEquals(f.ticket(), acknowledgement.ticket());
        assertEquals(attestation, acknowledgement.attestation());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                        .SUCCEEDED,
                acknowledgement.outcome());
        assertEquals(f.ticket().preparedConsumption(), acknowledgement.preparedConsumption());
        assertEquals(f.ticket().targetId(), acknowledgement.targetId());
        assertTrue(acknowledgement.id().canonicalToken().startsWith("sfackcpoutack:v1:"));
    }

    @Test
    void externallyAttestedFailureIsEquallyRepresentable() {
        Fixture f = fixture("primary");
        var attestation =
                new TestAttestation(
                        1,
                        f.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:failure");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                        .bind(f.ticket(), attestation, 3610L);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                        .FAILED,
                acknowledgement.outcome());
    }

    @Test
    void acknowledgementSequenceIsIndependentIdentityAxis() {
        Fixture f = fixture("primary");
        var attestation =
                new TestAttestation(
                        1,
                        f.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof");

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder();
        var first = binder.bind(f.ticket(), attestation, 3620L);
        var second = binder.bind(f.ticket(), attestation, 3621L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.ticket(), second.ticket());
        assertEquals(first.outcome(), second.outcome());
    }

    @Test
    void mismatchedTicketAndAttestationFailClosed() {
        Fixture first = fixture("primary");
        Fixture second = fixture("secondary");
        var attestation =
                new TestAttestation(
                        1,
                        second.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "other-ticket");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                                .bind(first.ticket(), attestation, 3630L));
    }

    @Test
    void malformedAttestationFailsClosed() {
        Fixture f = fixture("primary");
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(
                                        2,
                                        f.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        "proof"),
                                3640L));
        assertThrows(
                NullPointerException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(1, f.ticket().id(), null, "proof"),
                                3641L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(
                                        1,
                                        f.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        " "),
                                3642L));
    }

    @Test
    void acknowledgementRejectsIdentityForAnotherTicket() {
        Fixture first = fixture("primary");
        Fixture second = fixture("secondary");
        var attestation =
                new TestAttestation(
                        1,
                        first.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "proof");
        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                        .of(3650L, second.ticket());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
                                wrongId,
                                first.ticket(),
                                attestation));
    }

    @Test
    void invalidAcknowledgementSchemaAndSequenceFailClosed() {
        Fixture f = fixture("primary");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                                2,
                                1L,
                                f.ticket().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                                1,
                                0L,
                                f.ticket().id()));
    }

    @Test
    void binderCannotInferOrManufactureOutcomeWithoutExternalAttestation() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("bind", methods.get(0).getName());
        assertEquals(3, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                        .class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                        .class,
                methods.get(0).getParameterTypes()[1]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[2]);
    }

    private static Fixture fixture(String targetKey) {
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
                        3501L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);

        var ticket =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                        .issue(validation, 3550L);
        return new Fixture(ticket);
    }

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket) {}

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}
}
