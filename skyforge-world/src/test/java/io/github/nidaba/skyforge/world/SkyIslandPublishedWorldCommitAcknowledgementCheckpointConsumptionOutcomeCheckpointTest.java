package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.*;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void publisherBindsExactCanonicalOutcomeSet() {
        var first = acknowledgement(72001L, 2801L);
        var second = acknowledgement(72002L, 2802L);
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second, first));

        var checkpoint =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPublisher()
                        .publish(set, 1L);

        assertEquals(1L, checkpoint.id().checkpointRevision());
        assertEquals(set, checkpoint.acknowledgementSet());
        assertEquals(List.of(first.id(), second.id()), checkpoint.id().acknowledgementIdentity());
        assertEquals(2, checkpoint.size());
        assertFalse(checkpoint.isEmpty());
        assertEquals(first, checkpoint.forTicket(first.ticket().id()).orElseThrow());
        assertTrue(checkpoint.id().canonicalToken().startsWith("sfackcpoutcp:v1:"));
    }

    @Test
    void emptyValidatedSetCanBeCheckpointedExplicitly() {
        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .empty();

        var checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, empty);

        assertTrue(checkpoint.isEmpty());
        assertEquals(List.of(), checkpoint.id().acknowledgementIdentity());
        assertTrue(checkpoint.id().canonicalToken().endsWith(":0"));
    }

    @Test
    void identityRejectsUnsupportedSchemaRevisionAndNoncanonicalIdentity() {
        var first = acknowledgement(72003L, 2810L);
        var second = acknowledgement(72004L, 2811L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
                                2,
                                1L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                0L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                1L,
                                List.of(second.id(), first.id())));
    }

    @Test
    void identityRejectsDuplicateIoTicketEvenWithDistinctSequences() {
        var ticket = ticket(72005L);
        var first = acknowledgement(ticket, 2820L, "io-proof:first");
        var second = acknowledgement(ticket, 2821L, "io-proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
                                1,
                                3L,
                                List.of(first.id(), second.id())));
    }

    @Test
    void checkpointRejectsIdentityFromDifferentSet() {
        var first = acknowledgement(72006L, 2830L);
        var second = acknowledgement(72007L, 2831L);
        var firstSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var secondSet =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(second));

        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
                        .of(4L, secondSet);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint(
                                wrongId,
                                firstSet));
    }

    @Test
    void revisionAndSetContentsAreIndependentCheckpointIdentityAxes() {
        var first = acknowledgement(72008L, 2840L);
        var second = acknowledgement(72009L, 2841L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);

        var sameSetRevisionOne =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(10L, one);
        var sameSetRevisionTwo =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(11L, one);
        var changedSetSameRevision =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
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
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPublisher
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    acknowledgement(long rootSeed, long sequence) {
        return acknowledgement(ticket(rootSeed), sequence, "io-proof:" + rootSeed);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket
                                    ticket,
                            long sequence,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                evidenceToken),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket(
            long rootSeed) {
        var upstreamAck = upstreamAcknowledgement(rootSeed, 2801L);
        var set = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(upstreamAck));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, set);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var checkpointBinder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = checkpointBinder.bind(activation);
        var bindingValidation = checkpointBinder.validate(binding, activation);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(bindingValidation, 2700L, target);
        var current = preparer.validateForExecution(prepared, activation);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(current, 2750L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long rootSeed,
            long sequence) {
        var ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream-proof:" + rootSeed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        var compilation = acceptedCompilation(rootSeed);
        var publisher = new SkyIslandCompiledWorldPublisher();
        var publication = publisher.publish(compilation, 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 280L);
        var snapshotBinding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var workPreparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                workPreparer.prepare(
                        snapshotBinding,
                        2700L,
                        publication.catalog().volumes().get(0).bounds());
        var validation = workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2750L);
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
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        var proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        var convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        assertEquals(SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS, convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth72",
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
}
