package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void publisherBindsExactCanonicalAcknowledgementSet() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(66001L, 1601L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(66002L, 1602L);
        SkyIslandPublishedWorldCommitAcknowledgementSet set =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(second, first));

        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher()
                        .publish(set, 1L);

        assertEquals(1L, checkpoint.id().checkpointRevision());
        assertEquals(set, checkpoint.acknowledgementSet());
        assertEquals(
                List.of(first.id(), second.id()),
                checkpoint.id().acknowledgementIdentity());
        assertEquals(2, checkpoint.size());
        assertFalse(checkpoint.isEmpty());
        assertEquals(first, checkpoint.forTicket(first.ticket().id()).orElseThrow());
        assertTrue(checkpoint.id().canonicalToken().startsWith("sfackcp:v1:"));
    }

    @Test
    void emptyValidatedSetCanBeCheckpointedExplicitly() {
        SkyIslandPublishedWorldCommitAcknowledgementSet empty =
                SkyIslandPublishedWorldCommitAcknowledgementSet.empty();

        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, empty);

        assertTrue(checkpoint.isEmpty());
        assertEquals(List.of(), checkpoint.id().acknowledgementIdentity());
        assertTrue(checkpoint.id().canonicalToken().endsWith(":0"));
    }

    @Test
    void checkpointIdentityRejectsUnsupportedSchemaRevisionAndNoncanonicalIdentity() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(66003L, 1610L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(66004L, 1611L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                2,
                                1L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.SCHEMA_VERSION,
                                0L,
                                List.of(first.id())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.SCHEMA_VERSION,
                                1L,
                                List.of(second.id(), first.id())));
    }

    @Test
    void checkpointIdentityRejectsDuplicateTicketEvenWithDistinctSequences() {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(66005L);
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(ticket, 1620L, "proof:first");
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(ticket, 1621L, "proof:second");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.SCHEMA_VERSION,
                                3L,
                                List.of(first.id(), second.id())));
    }

    @Test
    void checkpointRejectsIdentityFromDifferentSet() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(66006L, 1630L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(66007L, 1631L);
        SkyIslandPublishedWorldCommitAcknowledgementSet firstSet =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        SkyIslandPublishedWorldCommitAcknowledgementSet secondSet =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(second));

        SkyIslandPublishedWorldCommitAcknowledgementCheckpointId wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.of(4L, secondSet);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpoint(
                                wrongId,
                                firstSet));
    }

    @Test
    void revisionAndSetContentsAreIndependentCheckpointIdentityAxes() {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(66008L, 1640L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(66009L, 1641L);
        SkyIslandPublishedWorldCommitAcknowledgementSet one =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        SkyIslandPublishedWorldCommitAcknowledgementSet two = one.admit(second);

        var sameSetRevisionOne =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(10L, one);
        var sameSetRevisionTwo =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(11L, one);
        var changedSetSameRevision =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(10L, two);

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
    void checkpointPublisherSurfaceDoesNotExposePersistenceOrReplicationOperations() {
        var publicMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher.class
                                        .getDeclaredMethods())
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
                                                || type.getName().contains("Level")));
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        return acknowledgement(
                ticket(rootSeed),
                acknowledgementSequence,
                "proof:" + rootSeed);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            SkyIslandPublishedWorldCommitTicket ticket,
            long acknowledgementSequence,
            String evidenceToken) {
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new TestAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                evidenceToken),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 160L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1500L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1550L);
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
                        "auth66",
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
