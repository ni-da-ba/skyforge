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

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuerTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void currentPreparedConsumptionValidationIssuesExactTicket() {
        Fixture fixture = fixture(69001L);
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer();

        var ticket = issuer.issue(fixture.current(), 2201L);

        assertEquals(2201L, ticket.id().ticketSequence());
        assertEquals(
                fixture.prepared().id(),
                ticket.id().preparedConsumptionId());
        assertEquals(fixture.current(), ticket.admissionValidation());
        assertEquals(fixture.prepared(), ticket.preparedConsumption());
        assertEquals(fixture.prepared().checkpointId(), ticket.checkpointId());
        assertEquals(fixture.prepared().targetId(), ticket.targetId());
        assertTrue(ticket.id().canonicalToken().startsWith("sfackcpticket:v1:"));
        assertTrue(
                ticket.id()
                        .canonicalToken()
                        .contains(fixture.prepared().id().canonicalToken()));
    }

    @Test
    void ticketIdentitySeparatesExplicitAdmissionSequence() {
        Fixture fixture = fixture(69002L);
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer();

        var first = issuer.issue(fixture.current(), 2210L);
        var second = issuer.issue(fixture.current(), 2211L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.preparedConsumption(), second.preparedConsumption());
    }

    @Test
    void invalidTicketIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(69003L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId(
                                2,
                                1L,
                                fixture.prepared().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId
                                        .SCHEMA_VERSION,
                                0L,
                                fixture.prepared().id()));
    }

    @Test
    void staleAndInactivePreparedConsumptionCannotIssueTickets() {
        Fixture fixture = fixture(69004L);
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer();

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                fixture.stale().status());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                fixture.inactive().status());

        assertThrows(
                IllegalStateException.class,
                () -> issuer.issue(fixture.stale(), 2220L));
        assertThrows(
                IllegalStateException.class,
                () -> issuer.issue(fixture.inactive(), 2221L));
    }

    @Test
    void ticketConstructorRejectsIdentityForAnotherPreparedConsumption() {
        Fixture first = fixture(69005L);
        Fixture second = fixture(69006L);

        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId.of(
                        2230L,
                        second.prepared());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket(
                                wrongId,
                                first.current()));
    }

    @Test
    void ticketRetainsAdmissionProvenanceWithoutClaimingLaterCurrentness() {
        Fixture fixture = fixture(69007L);
        var issuer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer();
        var ticket = issuer.issue(fixture.current(), 2240L);

        assertTrue(ticket.admissionValidation().current());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                fixture.stale().status());
        assertEquals(fixture.prepared(), ticket.preparedConsumption());
        assertEquals(fixture.prepared().targetId(), ticket.targetId());
        assertEquals(fixture.prepared().checkpointId(), ticket.checkpointId());
    }

    @Test
    void issuerHasNoRawPreparationActivationOrIoOverload() {
        var publicMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, publicMethods.size());
        var issue = publicMethods.get(0);
        assertEquals("issue", issue.getName());
        assertEquals(2, issue.getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                        .class,
                issue.getParameterTypes()[0]);
        assertEquals(long.class, issue.getParameterTypes()[1]);

        assertFalse(
                Arrays.stream(issue.getParameterTypes())
                        .anyMatch(
                                type ->
                                        type
                                                        == SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption
                                                                .class
                                                || type
                                                        == SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                                                                .class
                                                || type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level")));
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(rootSeed, 2201L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(rootSeed + 1L, 2202L);
        var firstSet = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var secondSet = firstSet.admit(second);
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, firstSet);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, secondSet);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(firstState);
        var preparationValidation = binder.validate(binding, firstState);

        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(preparationValidation, 2100L, target);

        var current = preparer.validateForExecution(prepared, firstState);
        var stale = preparer.validateForExecution(prepared, secondState);
        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                                .inactive());

        return new Fixture(prepared, current, stale, inactive);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = upstreamTicket(rootSeed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new TestAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "proof:" + rootSeed),
                        acknowledgementSequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 220L);
        SkyIslandPublishedWorldSnapshotBinding snapshotBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer workPreparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                workPreparer.prepare(
                        snapshotBinding,
                        2100L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2150L);
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
                        "auth69",
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption prepared,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                    current,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                    stale,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                    inactive) {}
}
