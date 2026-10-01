package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinderTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void externallyAttestedSuccessBindsExactTicketAndTargetProvenance() {
        Fixture fixture = fixture(70001L);
        var attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:success:70001");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                        .bind(fixture.ticket(), attestation, 2401L);

        assertEquals(2401L, acknowledgement.id().acknowledgementSequence());
        assertEquals(fixture.ticket().id(), acknowledgement.id().ticketId());
        assertEquals(fixture.ticket(), acknowledgement.ticket());
        assertEquals(attestation, acknowledgement.attestation());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome.SUCCEEDED,
                acknowledgement.outcome());
        assertEquals(fixture.ticket().preparedConsumption(), acknowledgement.preparedConsumption());
        assertEquals(fixture.ticket().targetId(), acknowledgement.targetId());
        assertTrue(acknowledgement.id().canonicalToken().startsWith("sfackcpack:v1:"));
    }

    @Test
    void externallyAttestedFailureIsEquallyRepresentable() {
        Fixture fixture = fixture(70002L);
        var attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .FAILED,
                        "io-proof:failure:70002");

        var acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                        .bind(fixture.ticket(), attestation, 2410L);

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome.FAILED,
                acknowledgement.outcome());
        assertEquals(fixture.ticket().id(), acknowledgement.attestation().ticketId());
    }

    @Test
    void acknowledgementIdentitySeparatesExplicitSequence() {
        Fixture fixture = fixture(70003L);
        var attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "io-proof:70003");

        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder();
        var first = binder.bind(fixture.ticket(), attestation, 2420L);
        var second = binder.bind(fixture.ticket(), attestation, 2421L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.ticket(), second.ticket());
        assertEquals(first.outcome(), second.outcome());
    }

    @Test
    void invalidIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(70004L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId(
                                2,
                                1L,
                                fixture.ticket().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId
                                        .SCHEMA_VERSION,
                                0L,
                                fixture.ticket().id()));
    }

    @Test
    void mismatchedTicketAndAttestationFailClosed() {
        Fixture first = fixture(70005L);
        Fixture second = fixture(70006L);
        var attestation =
                attestation(
                        second.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "other-ticket");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                                .bind(first.ticket(), attestation, 2430L));
    }

    @Test
    void malformedAttestationFailsClosed() {
        Fixture fixture = fixture(70007L);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                fixture.ticket(),
                                new TestAttestation(
                                        2,
                                        fixture.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        "proof"),
                                2440L));

        assertThrows(
                NullPointerException.class,
                () ->
                        binder.bind(
                                fixture.ticket(),
                                new TestAttestation(
                                        1,
                                        fixture.ticket().id(),
                                        null,
                                        "proof"),
                                2441L));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                fixture.ticket(),
                                new TestAttestation(
                                        1,
                                        fixture.ticket().id(),
                                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                                .SUCCEEDED,
                                        "   "),
                                2442L));
    }

    @Test
    void acknowledgementRejectsIdentityForAnotherTicket() {
        Fixture first = fixture(70008L);
        Fixture second = fixture(70009L);
        var attestation =
                attestation(
                        first.ticket().id(),
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                .SUCCEEDED,
                        "first-proof");
        var wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId
                        .of(2450L, second.ticket());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement(
                                wrongId,
                                first.ticket(),
                                attestation));
    }

    @Test
    void binderCannotInferOrManufactureOutcomeWithoutExternalAttestation() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("bind", methods.get(0).getName());
        assertEquals(3, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket.class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation
                        .class,
                methods.get(0).getParameterTypes()[1]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[2]);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation
            attestation(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId ticketId,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome outcome,
                    String evidenceToken) {
        return new TestAttestation(1, ticketId, outcome, evidenceToken);
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(rootSeed, 2401L);
        var set = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, set);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var binding =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder().bind(activation);
        var validation =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder()
                        .validate(binding, activation);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared = preparer.prepare(validation, 2300L, target);
        var current = preparer.validateForExecution(prepared, activation);
        var ticket =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                        .issue(current, 2350L);
        return new Fixture(ticket);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = upstreamTicket(rootSeed);
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
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 240L);
        SkyIslandPublishedWorldSnapshotBinding snapshotBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer workPreparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                workPreparer.prepare(
                        snapshotBinding,
                        2300L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 2350L);
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
                        "auth70",
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket) {}

    private record TestAttestation(
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
