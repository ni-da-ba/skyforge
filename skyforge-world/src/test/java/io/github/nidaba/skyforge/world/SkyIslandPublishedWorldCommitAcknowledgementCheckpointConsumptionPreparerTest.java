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

final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparerTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void targetIdentityIsExplicitCanonicalAndFailClosed() {
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");

        assertEquals("replica", target.namespace());
        assertEquals("primary", target.key());
        assertTrue(target.canonicalToken().startsWith("sfackcptarget:v1:"));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId(
                                2,
                                "replica",
                                "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                                " ",
                                "primary"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                                " replica ",
                                "primary"));
    }

    @Test
    void currentValidationPreparesExactCheckpointAndTarget() {
        Fixture fixture = fixture(68001L);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "east");
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();

        var prepared = preparer.prepare(fixture.currentValidation(), 2001L, target);

        assertEquals(2001L, prepared.id().preparationSequence());
        assertEquals(fixture.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(fixture.firstCheckpoint(), prepared.checkpoint());
        assertEquals(target, prepared.targetId());
        assertEquals(fixture.firstBinding(), prepared.binding());
        assertEquals(fixture.currentValidation(), prepared.preparationValidation());
        assertTrue(prepared.id().canonicalToken().startsWith("sfackcpprep:v1:"));
        assertTrue(prepared.id().canonicalToken().contains(target.canonicalToken()));
    }

    @Test
    void identitySeparatesPreparationSequenceTargetAndCheckpoint() {
        Fixture first = fixture(68002L);
        Fixture second = fixture(68003L);
        var targetA =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "east");
        var targetB =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "west");

        var base =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2010L,
                        first.firstBinding(),
                        targetA);
        var sequenceChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2011L,
                        first.firstBinding(),
                        targetA);
        var targetChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2010L,
                        first.firstBinding(),
                        targetB);
        var checkpointChanged =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2010L,
                        second.firstBinding(),
                        targetA);

        assertNotEquals(base, sequenceChanged);
        assertNotEquals(base, targetChanged);
        assertNotEquals(base, checkpointChanged);
    }

    @Test
    void invalidPreparedIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(68004L);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId(
                                2,
                                1L,
                                fixture.firstCheckpoint().id(),
                                target));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId
                                        .SCHEMA_VERSION,
                                0L,
                                fixture.firstCheckpoint().id(),
                                target));
    }

    @Test
    void staleAndInactiveValidationCannotPrepare() {
        Fixture fixture = fixture(68005L);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");

        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                fixture.staleValidation().status());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                fixture.inactiveValidation().status());

        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(fixture.staleValidation(), 2020L, target));
        assertThrows(
                IllegalStateException.class,
                () -> preparer.prepare(fixture.inactiveValidation(), 2021L, target));
    }

    @Test
    void preparedConsumptionRejectsMismatchedIdentityBindingAndPreparationValidation() {
        Fixture first = fixture(68006L);
        Fixture second = fixture(68007L);
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");

        var firstId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2030L,
                        first.firstBinding(),
                        target);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption(
                                firstId,
                                second.firstBinding(),
                                second.currentValidation()));

        var secondId =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        2031L,
                        second.firstBinding(),
                        target);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption(
                                secondId,
                                second.firstBinding(),
                                first.currentValidation()));
    }

    @Test
    void executionValidationTracksCurrentStaleAndInactiveWithoutChangingPreparedUnit() {
        Fixture fixture = fixture(68008L);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var prepared = preparer.prepare(fixture.currentValidation(), 2040L, target);

        var current = preparer.validateForExecution(prepared, fixture.firstState());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT,
                current.status());
        assertTrue(current.current());
        current.requireCurrent();

        var stale = preparer.validateForExecution(prepared, fixture.secondState());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                stale.status());
        assertFalse(stale.current());
        assertThrows(IllegalStateException.class, stale::requireCurrent);

        var inactive =
                preparer.validateForExecution(
                        prepared,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                                .inactive());
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                inactive.status());
        assertFalse(inactive.current());
        assertThrows(IllegalStateException.class, inactive::requireCurrent);

        assertEquals(fixture.firstCheckpoint().id(), prepared.checkpointId());
        assertEquals(target, prepared.targetId());
    }

    @Test
    void preparedValidationRejectsValidationFromAnotherBinding() {
        Fixture first = fixture(68009L);
        Fixture second = fixture(68010L);
        var preparer =
                new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer();
        var target =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId.of(
                        "replica",
                        "primary");
        var prepared = preparer.prepare(first.currentValidation(), 2050L, target);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation(
                                prepared,
                                second.currentValidation()));
    }

    @Test
    void preparerHasNoRawBindingOrIoShortcut() {
        var publicMethods =
                Arrays.stream(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer
                                        .class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .toList();

        assertEquals(2, publicMethods.size());
        assertTrue(publicMethods.stream().anyMatch(method -> method.getName().equals("prepare")));
        assertTrue(
                publicMethods.stream()
                        .anyMatch(method -> method.getName().equals("validateForExecution")));

        var prepare =
                publicMethods.stream()
                        .filter(method -> method.getName().equals("prepare"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation.class,
                prepare.getParameterTypes()[0]);
        assertFalse(
                Arrays.stream(prepare.getParameterTypes())
                        .anyMatch(
                                type ->
                                        type
                                                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding
                                                        .class));

        assertTrue(
                publicMethods.stream()
                        .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                        .noneMatch(
                                type ->
                                        type.getName().contains("File")
                                                || type.getName().contains("Path")
                                                || type.getName().contains("Socket")
                                                || type.getName().contains("Level")));
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandPublishedWorldCommitAcknowledgement first =
                acknowledgement(rootSeed, 2001L);
        SkyIslandPublishedWorldCommitAcknowledgement second =
                acknowledgement(rootSeed + 1L, 2002L);

        var firstSet =
                SkyIslandPublishedWorldCommitAcknowledgementSet.of(List.of(first));
        var secondSet = firstSet.admit(second);

        var firstCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(1L, firstSet);
        var secondCheckpoint =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(2L, secondSet);

        var firstState =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState.inactive()
                        .activateInitial(firstCheckpoint);
        var secondState =
                firstState.replace(firstCheckpoint.id(), secondCheckpoint);

        var binder = new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();
        var firstBinding = binder.bind(firstState);
        var currentValidation = binder.validate(firstBinding, firstState);
        var staleValidation = binder.validate(firstBinding, secondState);
        var inactiveValidation =
                binder.validate(
                        firstBinding,
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                                .inactive());

        return new Fixture(
                firstCheckpoint,
                secondCheckpoint,
                firstState,
                secondState,
                firstBinding,
                currentValidation,
                staleValidation,
                inactiveValidation);
    }

    private static SkyIslandPublishedWorldCommitAcknowledgement acknowledgement(
            long rootSeed,
            long acknowledgementSequence) {
        SkyIslandPublishedWorldCommitTicket ticket = ticket(rootSeed);
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

    private static SkyIslandPublishedWorldCommitTicket ticket(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation = acceptedCompilation(rootSeed);
        SkyIslandCompiledWorldPublisher publisher = new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication publication = publisher.publish(compilation, 1L);
        SkyIslandPublishedWorldView view = SkyIslandPublishedWorldView.of(List.of(publication));
        SkyIslandPublishedWorldActivationState active =
                SkyIslandPublishedWorldActivationState.inactive().activateInitial(view, 200L);
        SkyIslandPublishedWorldSnapshotBinding snapshotBinding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(active);
        SkyIslandPublishedWorldPreparedWorkPreparer workPreparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        SkyIslandPublishedWorldPreparedWork work =
                workPreparer.prepare(
                        snapshotBinding,
                        1900L,
                        publication.catalog().volumes().get(0).bounds());
        SkyIslandPublishedWorldPreparedWorkValidation validation =
                workPreparer.validateForCommit(work, active);
        return new SkyIslandPublishedWorldCommitTicketIssuer().issue(validation, 1950L);
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
                        "auth68",
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint firstCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint secondCheckpoint,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState firstState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState secondState,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding firstBinding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation currentValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation staleValidation,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation inactiveValidation) {}
}
