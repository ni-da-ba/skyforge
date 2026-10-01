package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementSetTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void callerOrderCanonicalizesByAcknowledgementSequence() {
        SkyIslandPublishedWorldCommitAcknowledgement a =
                acknowledgement(65001L, 1401L, SkyIslandPublishedWorldCommitOutcome.SUCCEEDED);
        SkyIslandPublishedWorldCommitAcknowledgement b =
                acknowledgement(65002L, 1402L, SkyIslandPublishedWorldCommitOutcome.FAILED);

        SkyIslandPublishedWorldCommitAcknowledgementSet set =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(b, a));

        assertEquals(List.of(a, b), set.acknowledgements());
        assertEquals(2, set.size());
        assertFalse(set.isEmpty());
    }

    @Test
    void admitReturnsNewImmutableSetAndRetainsOldSet() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(65003L, 1410L, SkyIslandPublishedWorldCommitOutcome.SUCCEEDED);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(65004L, 1411L, SkyIslandPublishedWorldCommitOutcome.FAILED);

        SkyIslandPublishedWorldCommitAcknowledgementSet empty =
                SkyIslandPublishedWorldCommitAcknowledgementSet.empty();
        SkyIslandPublishedWorldCommitAcknowledgementSet one = empty.admit(first);
        SkyIslandPublishedWorldCommitAcknowledgementSet two = one.admit(second);

        assertTrue(empty.isEmpty());
        assertEquals(List.of(first), one.acknowledgements());
        assertEquals(List.of(first, second), two.acknowledgements());
        assertThrows(
                UnsupportedOperationException.class,
                () -> two.acknowledgements().clear());
    }

    @Test
    void exactReplayFailsClosed() {
        SkyIslandPublishedWorldCommitAcknowledgement acknowledgement =
                acknowledgement(65005L, 1420L, SkyIslandPublishedWorldCommitOutcome.SUCCEEDED);

        SkyIslandPublishedWorldCommitAcknowledgementSet set =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(acknowledgement));

        assertThrows(
                IllegalArgumentException.class,
                () -> set.admit(acknowledgement));
    }

    @Test
    void duplicateSameOutcomeForSameTicketFailsClosed() {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(65006L);
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(
                        ticket,
                        1430L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:first");
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(
                        ticket,
                        1431L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementSet.of(
                                List.of(first, second)));
    }

    @Test
    void contradictoryOutcomeForSameTicketFailsClosedWithoutWinnerSelection() {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(65007L);
        SkyIslandPublishedWorldCommitAcknowledgement success =
                acknowledgement(
                        ticket,
                        1440L,
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:success");
        SkyIslandPublishedWorldCommitAcknowledgement failure =
                acknowledgement(
                        ticket,
                        1441L,
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "proof:failure");

        SkyIslandPublishedWorldCommitAcknowledgementSet set =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(success));

        assertThrows(
                IllegalArgumentException.class,
                () -> set.admit(failure));
        assertEquals(success, set.forTicket(ticket.id()).orElseThrow());
    }

    @Test
    void acknowledgementSequenceReuseAcrossDifferentTicketsFailsClosed() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(65008L, 1450L, SkyIslandPublishedWorldCommitOutcome.SUCCEEDED);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(65009L, 1450L, SkyIslandPublishedWorldCommitOutcome.FAILED);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementSet.of(
                                List.of(first, second)));
    }

    @Test
    void exactTicketLookupDoesNotSelectByOutcomeOrSequence() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(65010L, 1460L, SkyIslandPublishedWorldCommitOutcome.SUCCEEDED);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(65011L, 1461L, SkyIslandPublishedWorldCommitOutcome.FAILED);

        SkyIslandPublishedWorldCommitAcknowledgementSet set =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first, second));

        assertEquals(first, set.forTicket(first.ticket().id()).orElseThrow());
        assertEquals(second, set.forTicket(second.ticket().id()).orElseThrow());
        assertTrue(set.forTicket(ticket(65012L).id()).isEmpty());
    }

    @Test
    void invalidSchemaAndNullEntriesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedWorldCommitAcknowledgementSet(2, List.of()));
        assertThrows(
                NullPointerException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementSet(
                                SkyIslandPublishedWorldCommitAcknowledgementSet.SCHEMA_VERSION,
                                Arrays.asList((SkyIslandPublishedWorldCommitAcknowledgement) null)));
    }

    @Test
    void publicSurfaceHasNoReplaceLatestOrWinnerOperation() {
        var publicMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementSet.class
                                        .getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName)
                        .toList();

        assertFalse(publicMethods.contains("replace"));
        assertFalse(publicMethods.contains("latest"));
        assertFalse(publicMethods.contains("winner"));
        assertFalse(publicMethods.contains("upsert"));
        assertTrue(publicMethods.contains("admit"));
        assertTrue(publicMethods.contains("forTicket"));
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence,
            SkyIslandPublishedWorldCommitOutcome outcome) {
        return acknowledgement(
                ticket(rootSeed),
                acknowledgementSequence,
                outcome,
                "backend-proof:" + rootSeed + ":" + outcome);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            SkyIslandPublishedWorldCommitTicket ticket,
            long acknowledgementSequence,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken) {
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                new TestAttestation(1, ticket.id(), outcome, evidenceToken);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(ticket, attestation, acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 140L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1300L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1350L);
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
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        assertEquals(
                SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS,
                convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth65",
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

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}
}
