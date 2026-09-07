package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSetTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void callerOrderCanonicalizesByAcknowledgementSequence() {
        var a = acknowledgement(71001L, 2601L, outcomeSucceeded());
        var b = acknowledgement(71002L, 2602L, outcomeFailed());

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(b, a));

        assertEquals(List.of(a, b), set.acknowledgements());
        assertEquals(2, set.size());
        assertFalse(set.isEmpty());
    }

    @Test
    void admitReturnsNewImmutableSetAndRetainsOldSet() {
        var first = acknowledgement(71003L, 2610L, outcomeSucceeded());
        var second = acknowledgement(71004L, 2611L, outcomeFailed());

        var empty =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
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
        var acknowledgement = acknowledgement(71005L, 2620L, outcomeSucceeded());
        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(acknowledgement));

        assertThrows(IllegalArgumentException.class, () -> set.admit(acknowledgement));
    }

    @Test
    void duplicateSameOutcomeForSameIoTicketFailsClosed() {
        var ticket = ticket(71006L);
        var first =
                acknowledgement(ticket, 2630L, outcomeSucceeded(), "io-proof:first");
        var second =
                acknowledgement(ticket, 2631L, outcomeSucceeded(), "io-proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                .of(List.of(first, second)));
    }

    @Test
    void contradictoryOutcomeForSameIoTicketFailsClosedWithoutWinnerSelection() {
        var ticket = ticket(71007L);
        var success =
                acknowledgement(ticket, 2640L, outcomeSucceeded(), "io-proof:success");
        var failure =
                acknowledgement(ticket, 2641L, outcomeFailed(), "io-proof:failure");

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(success));

        assertThrows(IllegalArgumentException.class, () -> set.admit(failure));
        assertEquals(success, set.forTicket(ticket.id()).orElseThrow());
    }

    @Test
    void acknowledgementSequenceReuseAcrossDifferentIoTicketsFailsClosed() {
        var first = acknowledgement(71008L, 2650L, outcomeSucceeded());
        var second = acknowledgement(71009L, 2650L, outcomeFailed());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                .of(List.of(first, second)));
    }

    @Test
    void exactTicketLookupDoesNotSelectByOutcomeOrSequence() {
        var first = acknowledgement(71010L, 2660L, outcomeSucceeded());
        var second = acknowledgement(71011L, 2661L, outcomeFailed());

        var set =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first, second));

        assertEquals(first, set.forTicket(first.ticket().id()).orElseThrow());
        assertEquals(second, set.forTicket(second.ticket().id()).orElseThrow());
        assertTrue(set.forTicket(ticket(71012L).id()).isEmpty());
    }

    @Test
    void invalidSchemaAndNullEntriesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
                                2,
                                List.of()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                                        .SCHEMA_VERSION,
                                Arrays.asList(
                                        (SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement)
                                                null)));
    }

    @Test
    void publicSurfaceHasNoReplaceLatestWinnerOrUpsertOperation() {
        var names =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            long rootSeed,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                    outcome) {
        return acknowledgement(
                ticket(rootSeed),
                acknowledgementSequence,
                outcome,
                "io-proof:" + rootSeed + ":" + outcome);
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    acknowledgement(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket
                                    ticket,
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                    outcome,
                            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(1, ticket.id(), outcome, evidenceToken),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
            outcomeSucceeded() {
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome.SUCCEEDED;
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
            outcomeFailed() {
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome.FAILED;
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket(
            long rootSeed) {
        var upstreamAck = upstreamAcknowledgement(rootSeed, 2601L);
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
        var prepared = preparer.prepare(bindingValidation, 2500L, target);
        var current = preparer.validateForExecution(prepared, activation);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(current, 2550L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        var ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream-proof:" + rootSeed),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        var compilation = acceptedCompilation(rootSeed);
        var publisher = new SkyIslandCompiledWorldPublisher();
        var publication = publisher.publish(compilation, 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 260L);
        var snapshotBinding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var workPreparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                workPreparer.prepare(
                        snapshotBinding,
                        2500L,
                        publication.catalog().volumes().get(0).bounds());
        var validation = workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2550L);
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
                        "auth71",
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
