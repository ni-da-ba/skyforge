package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.*;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.*;
import io.github.nidaba.skyforge.recipes.skyisland.group.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparerTest {
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void targetIdentityIsExplicitCanonicalAndFailClosed() {
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");

        assertEquals("audit", target.namespace());
        assertEquals("primary", target.key());
        assertTrue(target.canonicalToken().startsWith("sfackcpouttarget:v1:"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId(
                                2, "audit", "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of(" ", "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                                .of(" audit ", "primary"));
    }

    @Test
    void currentValidationPreparesExactCheckpointAndTarget() {
        Fixture f = fixture(74001L);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "east");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();

        var prepared = preparer.prepare(f.currentValidation(), 3201L, target);

        assertEquals(3201L, prepared.id().preparationSequence());
        assertEquals(f.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(f.firstCheckpoint(), prepared.checkpoint());
        assertEquals(target, prepared.targetId());
        assertEquals(f.firstBinding(), prepared.binding());
        assertEquals(f.currentValidation(), prepared.preparationValidation());
        assertTrue(prepared.id().canonicalToken().startsWith("sfackcpoutprep:v1:"));
    }

    @Test
    void identitySeparatesSequenceTargetAndCheckpoint() {
        Fixture first = fixture(74002L);
        Fixture second = fixture(74003L);
        var targetA =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "east");
        var targetB =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "west");

        var base =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3210L, first.firstBinding(), targetA);
        var sequenceChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3211L, first.firstBinding(), targetA);
        var targetChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3210L, first.firstBinding(), targetB);
        var checkpointChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3210L, second.firstBinding(), targetA);

        assertNotEquals(base, sequenceChanged);
        assertNotEquals(base, targetChanged);
        assertNotEquals(base, checkpointChanged);
    }

    @Test
    void staleAndInactiveValidationCannotPrepare() {
        Fixture f = fixture(74004L);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");

        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(f.staleValidation(), 3220L, target));
        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(f.inactiveValidation(), 3221L, target));
    }

    @Test
    void preparedConsumptionRejectsMismatchedIdentityBindingAndValidation() {
        Fixture first = fixture(74005L);
        Fixture second = fixture(74006L);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3230L, first.firstBinding(), target);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                                id,
                                second.firstBinding(),
                                second.currentValidation()));

        var secondId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(3231L, second.firstBinding(), target);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                                secondId,
                                second.firstBinding(),
                                first.currentValidation()));
    }

    @Test
    void executionValidationTracksCurrentStaleAndInactiveWithoutChangingPreparedUnit() {
        Fixture f = fixture(74007L);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                        .of("audit", "primary");
        var prepared = preparer.prepare(f.currentValidation(), 3240L, target);

        var current = preparer.validateForExecution(prepared, f.firstState());
        assertTrue(current.current());
        current.requireCurrent();

        var stale = preparer.validateForExecution(prepared, f.secondState());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus.STALE,
                stale.status());
        assertThrows(IllegalStateException.class, stale::requireCurrent);

        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
                        .INACTIVE,
                inactive.status());
        assertThrows(IllegalStateException.class, inactive::requireCurrent);

        assertEquals(f.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(target, prepared.targetId());
    }

    @Test
    void preparerHasNoRawBindingOrIoShortcut() {
        var methods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(2, methods.size());
        var prepare =
                methods.stream().filter(m -> m.getName().equals("prepare")).findFirst().orElseThrow();
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                        .class,
                prepare.getParameterTypes()[0]);
        assertFalse(
                Arrays.stream(prepare.getParameterTypes())
                        .anyMatch(
                                type ->
                                        type
                                                        == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                                                                .class
                                                || type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level")));
    }

    private static Fixture fixture(long seed) {
        var first = outcomeAcknowledgement(seed, 3201L);
        var second = outcomeAcknowledgement(seed + 1L, 3202L);
        var one =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                        .of(List.of(first));
        var two = one.admit(second);
        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(1L, one);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                        .of(2L, two);
        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                        .inactive()
                        .activateInitial(firstCheckpoint);
        var secondState = firstState.replace(firstCheckpoint.id(), secondCheckpoint);
        var binder =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();
        var binding = binder.bind(firstState);
        return new Fixture(
                firstCheckpoint,
                secondCheckpoint,
                firstState,
                secondState,
                binding,
                binder.validate(binding, firstState),
                binder.validate(binding, secondState),
                binder.validate(
                        binding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                                .inactive()));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                    outcomeAcknowledgement(long seed, long sequence) {
        var ticket = ioTicket(seed);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder()
                .bind(
                        ticket,
                        new IoAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome
                                        .SUCCEEDED,
                                "io-proof:" + seed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ioTicket(
            long seed) {
        var upstream = upstreamAcknowledgement(seed, 3201L);
        var set = SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(upstream));
        var checkpoint = SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, set);
        var activation =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(checkpoint);
        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var binding = binder.bind(activation);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var prepared =
                preparer.prepare(
                        binder.validate(binding, activation),
                        3100L,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                                "replica", "primary"));
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketIssuer()
                .issue(preparer.validateForExecution(prepared, activation), 3150L);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement upstreamAcknowledgement(
            long seed, long sequence) {
        var ticket = upstreamTicket(seed);
        return new SkyIslandPublishedWorldCommitAcknowledgementBinder()
                .bind(
                        ticket,
                        new UpstreamAttestation(
                                1,
                                ticket.id(),
                                SkyIslandPublishedWorldCommitOutcome.SUCCEEDED,
                                "upstream:" + seed),
                        sequence);
    }

    private static SkyIslandPublishedWorldCommitTicket upstreamTicket(long seed) {
        var publication = new SkyIslandCompiledWorldPublisher().publish(compilation(seed), 1L);
        var view = SkyIslandPublishedWorldView.of(List.of(publication));
        var active = SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 320L);
        var binding = new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        var preparer = new SkyIslandPublishedWorldPreparedWorkPreparer();
        var work =
                preparer.prepare(
                        binding,
                        3100L,
                        publication.catalog().volumes().get(0).bounds());
        return new SkyIslandPublishedWorldCommitTicketIssuer()
                .issue(preparer.validateForCommit(work, active), 3150L);
    }

    private static SkyIslandAcceptedConvergenceCompilation compilation(long seed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        var request = request(seed, morphology);
        var original = new SkyIslandArchipelagoPlanner().plan(request);
        var synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        var proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        var convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        assertEquals(SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS, convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long seed, ProviderMorphologySpec morphology) {
        var template =
                new SkyIslandGroupTemplate(
                        "auth74",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                seed,
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

    private record Fixture(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                    firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                    secondCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                    firstState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                    secondState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                    firstBinding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                    currentValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                    staleValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                    inactiveValidation) {}
}
