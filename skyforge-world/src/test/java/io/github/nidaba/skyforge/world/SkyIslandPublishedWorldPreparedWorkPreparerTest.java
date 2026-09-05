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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedWorldPreparedWorkPreparerTest {
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void prepareCapturesExactBindingRegionIdentityAndQueryEvidence() {
        Fixture fixture = fixture(62001L);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        WorldBounds region = fixture.v1().catalog().volumes().get(0).bounds();

        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(fixture.binding(), 101L, region);

        assertEquals(101L, work.id().workSequence());
        assertEquals(fixture.binding().snapshotId(), work.snapshotId());
        assertEquals(region, work.region());
        assertEquals(fixture.binding().query(region), work.queryEvidence());
        assertEquals(work.queryEvidence().size(), work.hitCount());
        assertTrue(work.id().canonicalToken().startsWith("sfwork:v1:"));
        assertTrue(work.id().canonicalToken().contains(fixture.binding().snapshotId().canonicalToken()));
    }

    @Test
    void preparedWorkIdentitySeparatesSequenceRegionAndSnapshotAxes() {
        Fixture first = fixture(62002L);
        Fixture second = fixture(62003L);
        WorldBounds region = first.v1().catalog().volumes().get(0).bounds();
        WorldBounds otherRegion =
                new WorldBounds(
                        region.minimumX() + 1.0,
                        region.maximumX(),
                        region.minimumY(),
                        region.maximumY(),
                        region.minimumZ(),
                        region.maximumZ());

        SkyIslandPublishedWorldPreparedWorkId base =
                SkyIslandPublishedWorldPreparedWorkId.of(200L, first.binding(), region);
        SkyIslandPublishedWorldPreparedWorkId otherSequence =
                SkyIslandPublishedWorldPreparedWorkId.of(201L, first.binding(), region);
        SkyIslandPublishedWorldPreparedWorkId otherRegionId =
                SkyIslandPublishedWorldPreparedWorkId.of(200L, first.binding(), otherRegion);
        SkyIslandPublishedWorldPreparedWorkId otherSnapshot =
                SkyIslandPublishedWorldPreparedWorkId.of(200L, second.binding(), region);

        assertNotEquals(base, otherSequence);
        assertNotEquals(base, otherRegionId);
        assertNotEquals(base, otherSnapshot);
        assertNotEquals(base.canonicalToken(), otherSequence.canonicalToken());
        assertNotEquals(base.canonicalToken(), otherRegionId.canonicalToken());
        assertNotEquals(base.canonicalToken(), otherSnapshot.canonicalToken());
    }

    @Test
    void invalidIdentitySchemaAndSequenceFailClosed() {
        Fixture fixture = fixture(62004L);
        WorldBounds region = fixture.v1().catalog().volumes().get(0).bounds();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldPreparedWorkId(
                                2,
                                1L,
                                fixture.binding().snapshotId(),
                                region));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldPreparedWorkId(
                                SkyIslandPublishedWorldPreparedWorkId.SCHEMA_VERSION,
                                0L,
                                fixture.binding().snapshotId(),
                                region));
    }

    @Test
    void preparedWorkRejectsSnapshotMismatchAndForgedEvidence() {
        Fixture first = fixture(62005L);
        Fixture second = fixture(62006L);
        WorldBounds region = first.v1().catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWorkId firstId =
                SkyIslandPublishedWorldPreparedWorkId.of(300L, first.binding(), region);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldPreparedWork(
                                firstId,
                                second.binding(),
                                second.binding().query(region)));

        List<SkyIslandPublishedWorldEntry> exactEvidence =
                first.binding().query(region);
        List<SkyIslandPublishedWorldEntry> forged =
                exactEvidence.isEmpty()
                        ? List.of(second.binding().query(second.v1().catalog().volumes().get(0).bounds()).get(0))
                        : List.of();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldPreparedWork(
                                firstId,
                                first.binding(),
                                forged));
    }

    @Test
    void queryEvidenceIsImmutableAndCannotBeChangedAfterPreparation() {
        Fixture fixture = fixture(62007L);
        WorldBounds region = fixture.v1().catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWorkId id =
                SkyIslandPublishedWorldPreparedWorkId.of(400L, fixture.binding(), region);
        List<SkyIslandPublishedWorldEntry> source =
                new ArrayList<>(fixture.binding().query(region));

        SkyIslandPublishedWorldPreparedWork work =
                new SkyIslandPublishedWorldPreparedWork(id, fixture.binding(), source);
        source.clear();

        assertEquals(fixture.binding().query(region), work.queryEvidence());
        assertThrows(
                UnsupportedOperationException.class,
                () -> work.queryEvidence().clear());
    }

    @Test
    void commitValidationTracksExactBindingCurrentnessWithoutRefresh() {
        Fixture fixture = fixture(62008L);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();
        WorldBounds region = fixture.v1().catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWork work =
                preparer.prepare(fixture.binding(), 500L, region);

        SkyIslandPublishedWorldPreparedWorkValidation current =
                preparer.validateForCommit(work, fixture.firstActivation());
        assertEquals(SkyIslandPublishedWorldBindingStatus.CURRENT, current.status());
        assertTrue(current.current());
        current.requireCurrent();

        SkyIslandPublishedWorldPreparedWorkValidation stale =
                preparer.validateForCommit(work, fixture.secondActivation());
        assertEquals(SkyIslandPublishedWorldBindingStatus.STALE, stale.status());
        assertFalse(stale.current());
        assertEquals(fixture.binding(), stale.bindingValidation().binding());
        assertThrows(IllegalStateException.class, stale::requireCurrent);

        SkyIslandPublishedWorldPreparedWorkValidation inactive =
                preparer.validateForCommit(
                        work,
                        SkyIslandPublishedWorldActivationState.inactive());
        assertEquals(SkyIslandPublishedWorldBindingStatus.INACTIVE, inactive.status());
        assertFalse(inactive.current());
        assertThrows(IllegalStateException.class, inactive::requireCurrent);

        assertEquals(fixture.binding().snapshotId(), work.snapshotId());
        assertEquals(fixture.v1().id(), work.queryEvidence().get(0).publicationId());
        assertEquals(
                fixture.v2().id(),
                fixture.secondActivation().query(region).get(0).publicationId());
    }

    @Test
    void preparedWorkValidationRejectsValidationFromAnotherBinding() {
        Fixture first = fixture(62009L);
        Fixture second = fixture(62010L);
        SkyIslandPublishedWorldPreparedWorkPreparer preparer =
                new SkyIslandPublishedWorldPreparedWorkPreparer();

        WorldBounds firstRegion = first.v1().catalog().volumes().get(0).bounds();
        SkyIslandPublishedWorldPreparedWork firstWork =
                preparer.prepare(first.binding(), 600L, firstRegion);
        SkyIslandPublishedWorldBindingValidation secondValidation =
                new SkyIslandPublishedWorldSnapshotBinder()
                        .validate(second.binding(), second.firstActivation());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandPublishedWorldPreparedWorkValidation(
                                firstWork,
                                secondValidation));
    }

    private static Fixture fixture(long rootSeed) {
        SkyIslandAcceptedConvergenceCompilation compilation =
                acceptedCompilation(rootSeed, 0.0);
        SkyIslandCompiledWorldPublisher publisher =
                new SkyIslandCompiledWorldPublisher();
        SkyIslandCompiledWorldPublication v1 = publisher.publish(compilation, 1L);
        SkyIslandCompiledWorldPublication v2 = publisher.publish(compilation, 2L);
        SkyIslandPublishedWorldView viewV1 =
                SkyIslandPublishedWorldView.of(List.of(v1));
        SkyIslandPublishedWorldView viewV2 =
                viewV1.replace(v1.id(), v2);

        SkyIslandPublishedWorldActivationState first =
                SkyIslandPublishedWorldActivationState.inactive()
                        .activateInitial(viewV1, 80L);
        SkyIslandPublishedWorldActivationState second =
                first.replace(first.requireActive().id(), viewV2, 81L);
        SkyIslandPublishedWorldSnapshotBinding binding =
                new SkyIslandPublishedWorldSnapshotBinder().bind(first);

        return new Fixture(v1, v2, first, second, binding);
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            double centerX) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology =
                new ProviderMorphologySpec(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        0.0,
                        0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, centerX, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
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
                new SkyIslandSupportConvergenceExecutor()
                        .executeOnce(proposal, registry);
        assertEquals(
                SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS,
                convergence.outcome());
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            double centerX,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth62",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(),
                        360.0,
                        0.0,
                        0.0,
                        List.of(morphology),
                        new SkyIslandGroupLayout.Cluster(
                                800.0, 0.0, 0.0, 0.0),
                        440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
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
            SkyIslandPublishedWorldActivationState firstActivation,
            SkyIslandPublishedWorldActivationState secondActivation,
            SkyIslandPublishedWorldSnapshotBinding binding) {}
}
