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

final class SkyIslandPublishedWorldCommitTicketIssuerTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void currentPreparedWorkValidationIssuesExactTicket() {
        Fixture fixture = fixture(63001L);
        SkyIslandPublishedWorldCommitTicketIssuer issuer =
                new SkyIslandPublishedWorldCommitTicketIssuer();

        SkyIslandPublishedWorldCommitTicket ticket =
                issuer.issue(fixture.currentValidation(), 901L);

        assertEquals(901L, ticket.id().ticketSequence());
        assertEquals(fixture.work().id(), ticket.id().preparedWorkId());
        assertEquals(fixture.currentValidation(), ticket.admissionValidation());
        assertEquals(fixture.work(), ticket.preparedWork());
        assertEquals(fixture.work().snapshotId(), ticket.snapshotId());
        assertEquals(fixture.work().region(), ticket.region());
        assertEquals(fixture.work().hitCount(), ticket.hitCount());
        assertTrue(ticket.id().canonicalToken().startsWith("sfticket:v1:"));
        assertTrue(ticket.id().canonicalToken().contains(fixture.work().id().canonicalToken()));
    }

    @Test
    void ticketIdentitySeparatesExplicitAdmissionSequence() {
        Fixture fixture = fixture(63002L);
        SkyIslandPublishedWorldCommitTicketIssuer issuer =
                new SkyIslandPublishedWorldCommitTicketIssuer();

        SkyIslandPublishedWorldCommitTicket first =
                issuer.issue(fixture.currentValidation(), 910L);
        SkyIslandPublishedWorldCommitTicket second =
                issuer.issue(fixture.currentValidation(), 911L);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.id().canonicalToken(), second.id().canonicalToken());
        assertEquals(first.preparedWork(), second.preparedWork());
    }

    @Test
    void invalidTicketIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(63003L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitTicketId(
                                2,
                                1L,
                                fixture.work().id()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitTicketId(
                                SkyIslandPublishedWorldCommitTicketId.SCHEMA_VERSION,
                                0L,
                                fixture.work().id()));
    }

    @Test
    void staleAndInactivePreparedWorkCannotIssueTickets() {
        Fixture fixture = fixture(63004L);
        SkyIslandPublishedWorldCommitTicketIssuer issuer =
                new SkyIslandPublishedWorldCommitTicketIssuer();

        assertEquals(
                SkyIslandPublishedWorldBindingStatus.STALE,
                fixture.staleValidation().status());
        assertEquals(
                SkyIslandPublishedWorldBindingStatus.INACTIVE,
                fixture.inactiveValidation().status());

        assertThrows(
                IllegalStateException.class,
                () -> issuer.issue(fixture.staleValidation(), 920L));
        assertThrows(
                IllegalStateException.class,
                () -> issuer.issue(fixture.inactiveValidation(), 921L));
    }

    @Test
    void ticketConstructorRejectsIdentityForDifferentPreparedWork() {
        Fixture first = fixture(63005L);
        Fixture second = fixture(63006L);

        SkyIslandPublishedWorldCommitTicketId wrongId =
                SkyIslandPublishedWorldCommitTicketId.of(
                        930L,
                        second.work());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitTicket(
                                wrongId,
                                first.currentValidation()));
    }

    @Test
    void ticketRetainsCurrentValidationButDoesNotClaimLaterActivationCurrentness() {
        Fixture fixture = fixture(63007L);
        SkyIslandPublishedWorldCommitTicket ticket =
                new SkyIslandPublishedWorldCommitTicketIssuer()
                        .issue(fixture.currentValidation(), 940L);

        assertTrue(ticket.admissionValidation().current());
        assertEquals(
                SkyIslandPublishedWorldBindingStatus.STALE,
                fixture.staleValidation().status());
        assertEquals(fixture.work(), ticket.preparedWork());
        assertEquals(
                fixture.v1().id(),
                ticket.preparedWork().queryEvidence().get(0).publicationId());
        assertEquals(
                fixture.v2().id(),
                fixture.secondActivation()
                        .query(ticket.region())
                        .get(0)
                        .publicationId());
    }

    @Test
    void issuerHasNoRawWorkOrBackendMutationOverload() {
        var publicMethods =
                Arrays.stream(SkyIslandPublishedWorldCommitTicketIssuer.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(1, publicMethods.size());
        assertEquals("issue", publicMethods.get(0).getName());
        assertEquals(2, publicMethods.get(0).getParameterCount());
        assertEquals(
                SkyIslandPublishedWorldPreparedWorkValidation.class,
                publicMethods.get(0).getParameterTypes()[0]);
        assertEquals(long.class, publicMethods.get(0).getParameterTypes()[1]);
        assertFalse(
                Arrays.stream(publicMethods.get(0).getParameterTypes())
                        .anyMatch(
                                type ->
                                        type == SkyIslandPublishedWorldPreparedWork.class
                                                || type == SkyIslandPublishedWorldActivationState.class));
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 = SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 = viewV1.replace(v1.id(), v2);
        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(viewV1, 100L);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 101L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(first);
        WorldBounds region = v1.catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work = preparer.prepare(binding, 800L, region);

        return new Fixture(
                v1,
                v2,
                second,
                work,
                preparer.validateForCommit(work, first),
                preparer.validateForCommit(work, second),
                preparer.validateForCommit(work, SkyIslandPublishedWorldActivationState.inactive()));
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
                        "auth63",
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
            SkyIslandCompiledWorldPublication v1,
            SkyIslandCompiledWorldPublication v2,
            SkyIslandPublishedWorldActivationState secondActivation,
            SkyIslandPublishedWorldPreparedWork work,
            SkyIslandPublishedWorldPreparedWorkValidation currentValidation,
            SkyIslandPublishedWorldPreparedWorkValidation staleValidation,
            SkyIslandPublishedWorldPreparedWorkValidation inactiveValidation) {}
}
