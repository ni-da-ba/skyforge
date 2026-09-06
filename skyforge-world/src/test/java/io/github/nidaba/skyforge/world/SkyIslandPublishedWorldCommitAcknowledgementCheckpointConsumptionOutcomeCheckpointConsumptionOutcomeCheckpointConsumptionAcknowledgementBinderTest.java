package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinderTest {

    @Test
    void externallyAttestedSuccessBindsExactTicketAndTarget() {
        Fixture f = fixture("primary");
        var attestation =
                new TestAttestation(
                        1,
                        f.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof:success");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                        .bind(f.ticket(), attestation, 4720L);

        assertEquals(4720L, acknowledgement.id().acknowledgementSequence());
        assertEquals(f.ticket().id(), acknowledgement.id().ticketId());
        assertEquals(f.ticket(), acknowledgement.ticket());
        assertEquals(attestation, acknowledgement.attestation());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                        .SUCCEEDED,
                acknowledgement.outcome());
        assertEquals(f.ticket().preparedConsumption(), acknowledgement.preparedConsumption());
        assertEquals(f.ticket().targetId(), acknowledgement.targetId());
        assertTrue(acknowledgement.id().canonicalToken().startsWith("sfackcpoutcpoutack:v1:"));
    }

    @Test
    void externallyAttestedFailureIsEquallyRepresentable() {
        Fixture f = fixture("primary");
        var attestation =
                new TestAttestation(
                        1,
                        f.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .FAILED,
                        "storage-proof:failure");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                        .bind(f.ticket(), attestation, 4721L);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
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
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "storage-proof");

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder();
        var first = binder.bind(f.ticket(), attestation, 4730L);
        var second = binder.bind(f.ticket(), attestation, 4731L);

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
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "other-ticket");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder()
                                .bind(first.ticket(), attestation, 4740L));
    }

    @Test
    void malformedAttestationFailsClosed() {
        Fixture f = fixture("primary");
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(
                                        2,
                                        f.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        "proof"),
                                4750L));
        assertThrows(
                NullPointerException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(1, f.ticket().id(), null, "proof"),
                                4751L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                f.ticket(),
                                new TestAttestation(
                                        1,
                                        f.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        " "),
                                4752L));
    }

    @Test
    void acknowledgementRejectsIdentityForAnotherTicket() {
        Fixture first = fixture("primary");
        Fixture second = fixture("secondary");
        var attestation =
                new TestAttestation(
                        1,
                        first.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "proof");
        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                        .of(4760L, second.ticket());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
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
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                                2,
                                1L,
                                f.ticket().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                                1,
                                0L,
                                f.ticket().id()));
    }

    @Test
    void binderCannotInferOrManufactureOutcomeWithoutExternalAttestation() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("bind", methods.get(0).getName());
        assertEquals(3, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                        .class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                        .class,
                methods.get(0).getParameterTypes()[1]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[2]);
        assertEquals(
                List.of("bind"),
                methods.stream().map(java.lang.reflect.Method::getName).sorted().toList());
    }

    private static Fixture fixture(String targetKey) {
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                        .empty();
        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, set);
        var state =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = checkpointBinder.bind(state);

        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        checkpointBinder.validate(binding, state),
                        4701L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of("audit", targetKey));
        var validation = preparer.validateForExecution(prepared, state);

        var ticket =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketIssuer()
                        .issue(validation, 4710L);
        return new Fixture(ticket);
    }

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                    ticket) {}

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    ticketId,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
                    outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation {}
}
