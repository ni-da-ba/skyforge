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

final class SkyIslandPublishedWorldCommitAcknowledgementBinderTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void externallyAttestedSuccessBindsExactTicketAndProvenance() {
        Fixture fixture = fixture(64001L);
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "backend-proof:success:64001");

        SkyIslandPublishedWorldCommitAcknowledgement acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                        .bind(fixture.ticket(), attestation, 1201L);

        assertEquals(1201L, acknowledgement.id().acknowledgementSequence());
        assertEquals(fixture.ticket().id(), acknowledgement.id().ticketId());
        assertEquals(fixture.ticket(), acknowledgement.ticket());
        assertEquals(attestation, acknowledgement.attestation());
        assertEquals(SkyIslandPublishedWorldCommitOutcome.SUCCEEDED, acknowledgement.outcome());
        assertEquals(fixture.ticket().preparedWork(), acknowledgement.preparedWork());
        assertTrue(acknowledgement.id().canonicalToken().startsWith("sfack:v1:"));
        assertTrue(acknowledgement.id().canonicalToken().contains(fixture.ticket().id().canonicalToken()));
    }

    @Test
    void externallyAttestedFailureIsEquallyRepresentable() {
        Fixture fixture = fixture(64002L);
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitOutcome.FAILED,
                        "backend-proof:failure:64002");

        SkyIslandPublishedWorldCommitAcknowledgement acknowledgement =
                new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                        .bind(fixture.ticket(), attestation, 1210L);

        assertEquals(SkyIslandPublishedWorldCommitOutcome.FAILED, acknowledgement.outcome());
        assertEquals(fixture.ticket().id(), acknowledgement.attestation().ticketId());
    }

    @Test
    void acknowledgementIdentitySeparatesExplicitSequence() {
        Fixture fixture = fixture(64003L);
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                attestation(
                        fixture.ticket().id(),
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:64003");

        var binder = new SkyIslandPublishedWorldCommitAcknowledgementBinder();
        var first = binder.bind(fixture.ticket(), attestation, 1220L);
        var second = binder.bind(fixture.ticket(), attestation, 1221L);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.ticket(), second.ticket());
        assertEquals(first.outcome(), second.outcome());
    }

    @Test
    void invalidAcknowledgementIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(64004L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementId(
                                2,
                                1L,
                                fixture.ticket().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementId(
                                SkyIslandPublishedWorldCommitAcknowledgementId.SCHEMA_VERSION,
                                0L,
                                fixture.ticket().id()));
    }

    @Test
    void mismatchedTicketAndAttestationFailClosed() {
        Fixture first = fixture(64005L);
        Fixture second = fixture(64006L);

        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                attestation(
                        second.ticket().id(),
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:other-ticket");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                                .bind(first.ticket(), attestation, 1230L));
    }

    @Test
    void malformedAttestationFailsClosed() {
        Fixture fixture = fixture(64007L);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementBinder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                fixture.ticket(),
                                new TestAttestation(
                                        2,
                                        fixture.ticket().id(),
                                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                        "proof"),
                                1240L));

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
                                1241L));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        binder.bind(
                                fixture.ticket(),
                                new TestAttestation(
                                        1,
                                        fixture.ticket().id(),
                                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                        "   "),
                                1242L));
    }

    @Test
    void acknowledgementConstructorRejectsIdentityForAnotherTicket() {
        Fixture first = fixture(64008L);
        Fixture second = fixture(64009L);
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation =
                attestation(
                        first.ticket().id(),
                        SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                        "proof:first");

        SkyIslandPublishedWorldCommitAcknowledgementId wrongId =
                SkyIslandPublishedWorldCommitAcknowledgementId.of(
                        1250L,
                        second.ticket());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgement(
                                wrongId,
                                first.ticket(),
                                attestation));
    }

    @Test
    void binderCannotInferOrManufactureOutcomeWithoutAttestation() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementBinder.class
                                        .getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, methods.size());
        assertEquals("bind", methods.get(0).getName());
        assertEquals(3, methods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldCommitTicket.class,
                methods.get(0).getParameterTypes()[0]);
        assertEquals(
                SkyIslandPublishedWorldCommitOutcomeAttestation.class,
                methods.get(0).getParameterTypes()[1]);
        assertEquals(long.class, methods.get(0).getParameterTypes()[2]);
    }

    private static SkyIslandPublishedWorldCommitOutcomeAttestation attestation(
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken) {
        return new TestAttestation(1, ticketId, outcome, evidenceToken);
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 120L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(
                        binding,
                        1000L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                preparer.validateForCommit(work, active);
        SkyIslandPublishedWorldCommitTicket ticket =
                new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1100L);
        return new Fixture(ticket);
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
                        "auth64",
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

    private record Fixture(SkyIslandPublishedWorldCommitTicket ticket) {}

    private record TestAttestation(
            int schemaVersion,
            SkyIslandPublishedWorldCommitTicketId ticketId,
            SkyIslandPublishedWorldCommitOutcome outcome,
            String evidenceToken)
            implements SkyIslandPublishedWorldCommitOutcomeAttestation {}
}
