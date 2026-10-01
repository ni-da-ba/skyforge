package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SkyIslandVegetationFunctionalGroupProfileTest {
    private static final long AUTHORED_WORLD = 0x46554E4347524F55L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private static SkyIslandCommunityDisturbanceEvidence disturbanceEvidence;
    private static SkyIslandLocalPosition position;

    @BeforeAll
    static void buildFixture() {
        SkyIslandCommunityAssemblyEvidence assemblyEvidence = evidence(121601L).getFirst();
        disturbanceEvidence = SkyIslandCommunityDisturbanceEvidence.of(
                assemblyEvidence,
                new SkyIslandCommunityDisturbanceEvent(0.7, 60.0));
        position = new SkyIslandLocalPosition(0.0, 0.0);
    }

    @Test
    void weightedMeanTransformIsExactAndNormalized() {
        SkyIslandCommunityStructureRealization structure =
                new SkyIslandCommunityStructureRealization(
                        0.8, 0.6, 0.4, 0.2, 0.9, 0.7, 0.5, 0.3, 0.1);
        SkyIslandFunctionalGroupStructuralAffinity affinity =
                new SkyIslandFunctionalGroupStructuralAffinity(
                        1.0, 2.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        double support = SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE
                .support(Optional.of(structure), affinity)
                .orElseThrow();

        assertEquals((0.8 + 2.0 * 0.6 + 0.4) / 4.0, support, 0.0);
        assertTrue(support >= 0.0 && support <= 1.0);
    }

    @Test
    void explicitAffinityPolicyChangesSupportWithoutChangingEcology() {
        SkyIslandMultiCommunityRealizationComposition composition = resolvedComposition();

        SkyIslandVegetationFunctionalGroupProfile canopyBiased =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
        SkyIslandVegetationFunctionalGroupProfile groundBiased =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        var canopy = canopyBiased.evaluate(composition);
        var ground = groundBiased.evaluate(composition);

        assertEquals(composition, canopy.composition());
        assertEquals(composition.position(), canopy.position());
        assertTrue(canopy.resolved());
        assertTrue(ground.resolved());
        assertTrue(
                Double.doubleToLongBits(canopy.structuralNicheSupport().orElseThrow())
                        != Double.doubleToLongBits(ground.structuralNicheSupport().orElseThrow()));
    }

    @Test
    void unresolvedAggregateRealizationPropagatesToUnresolvedNicheSupport() {
        SkyIslandMultiCommunityRealizationComposition composition = unresolvedComposition();
        SkyIslandVegetationFunctionalGroupProfile profile =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.SHRUB,
                        uniformAffinity(),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        SkyIslandVegetationFunctionalGroupEvaluation evaluation =
                profile.evaluate(composition);

        assertFalse(composition.resolved());
        assertFalse(evaluation.resolved());
        assertTrue(evaluation.structuralNicheSupport().isEmpty());
        assertEquals(composition, evaluation.composition());
    }

    @Test
    void functionalGroupIdentityDoesNotCarryAutomaticAffinity() {
        SkyIslandVegetationFunctionalGroupProfile first =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.GRASS_FORB,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
        SkyIslandVegetationFunctionalGroupProfile second =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.GRASS_FORB,
                        new SkyIslandFunctionalGroupStructuralAffinity(
                                0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
                        SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);

        assertEquals(first.functionalGroup(), second.functionalGroup());
        assertTrue(!first.structuralAffinity().equals(second.structuralAffinity()));
    }

    @Test
    void invalidStructuralAffinityWeightsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandFunctionalGroupStructuralAffinity(
                        Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void invalidCustomTransformOutputFailsAtProfileBoundary() {
        SkyIslandVegetationFunctionalGroupProfile invalid =
                new SkyIslandVegetationFunctionalGroupProfile(
                        SkyIslandVegetationFunctionalGroup.FERN_GROUNDCOVER,
                        uniformAffinity(),
                        (realization, affinity) -> OptionalDouble.of(1.5));

        assertThrows(
                IllegalArgumentException.class,
                () -> invalid.evaluate(resolvedComposition()));
    }

    @Test
    void spatialPatchEvaluationIsDeterministicAndRetainsExactEcology() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandVegetationSpatialPatchProfile spatial =
                new SkyIslandVegetationSpatialPatchProfile(
                        SkyIslandEcologicalPatchSignalProfile.current(
                                0x1221A11L,
                                "ecology.patch.shrub",
                                96.0),
                        SkyIslandPatchinessRetentionSpatialTransform.INSTANCE);

        SkyIslandVegetationSpatialPatchEvaluation first = spatial.evaluate(group);
        SkyIslandVegetationSpatialPatchEvaluation second = spatial.evaluate(group);

        assertEquals(
                Double.doubleToLongBits(first.rawSignal()),
                Double.doubleToLongBits(second.rawSignal()));
        assertEquals(first.state(), second.state());
        assertEquals(group, first.functionalGroupEvaluation());
        assertEquals(group.position(), first.position());
        assertEquals(spatial, first.spatialProfile());
    }

    @Test
    void zeroPatchinessExactlyPreservesUpstreamNicheSupport() {
        SkyIslandMultiCommunityRealizationComposition composition =
                withPatchiness(resolvedComposition(), 0.0);
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(composition);
        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221B22L, "ecology.patch.zero", 80.0).evaluate(group);

        SkyIslandFunctionalGroupSpatialPatchState state =
                evaluation.state().orElseThrow();
        assertEquals(1.0, state.retention(), 0.0);
        assertEquals(
                Double.doubleToLongBits(group.structuralNicheSupport().orElseThrow()),
                Double.doubleToLongBits(state.spatialNicheSupport()));
    }

    @Test
    void zeroUpstreamSupportCannotBeCreatedBySpatialSignal() {
        SkyIslandVegetationFunctionalGroupEvaluation base =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandVegetationFunctionalGroupEvaluation zero =
                new SkyIslandVegetationFunctionalGroupEvaluation(
                        base.profile(),
                        base.composition(),
                        OptionalDouble.of(0.0));

        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221C33L, "ecology.patch.zero-support", 72.0)
                        .evaluate(zero);

        assertEquals(0.0, evaluation.state().orElseThrow().spatialNicheSupport(), 0.0);
    }

    @Test
    void unresolvedUpstreamEcologyPropagatesToUnresolvedSpatialState() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(unresolvedComposition());

        SkyIslandVegetationSpatialPatchEvaluation evaluation =
                spatialProfile(0x1221D44L, "ecology.patch.unresolved", 64.0)
                        .evaluate(group);

        assertFalse(group.resolved());
        assertFalse(evaluation.resolved());
        assertTrue(evaluation.state().isEmpty());
        assertTrue(Double.isFinite(evaluation.rawSignal()));
    }

    @Test
    void retentionTransformIsExactAndNeverAmplifiesSupport() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        double support = group.structuralNicheSupport().orElseThrow();
        double patchiness =
                group.composition().aggregateRealization().orElseThrow().patchinessPotential();

        SkyIslandFunctionalGroupSpatialPatchState low =
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE
                        .spatialize(group, -1.0)
                        .orElseThrow();
        SkyIslandFunctionalGroupSpatialPatchState high =
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE
                        .spatialize(group, 1.0)
                        .orElseThrow();

        assertEquals(0.0, low.normalizedSignal(), 0.0);
        assertEquals(1.0 - patchiness, low.retention(), 1.0e-15);
        assertEquals(support * (1.0 - patchiness), low.spatialNicheSupport(), 1.0e-15);
        assertEquals(1.0, high.normalizedSignal(), 0.0);
        assertEquals(1.0, high.retention(), 0.0);
        assertEquals(support, high.spatialNicheSupport(), 1.0e-15);
        assertTrue(low.spatialNicheSupport() <= support);
        assertTrue(high.spatialNicheSupport() <= support);
    }

    @Test
    void explicitSignalIdentityChangesPatternWithoutChangingEcology() {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(resolvedComposition());
        SkyIslandEcologicalPatchSignalProfile first =
                SkyIslandEcologicalPatchSignalProfile.current(
                        0x1221E55L,
                        "ecology.patch.first",
                        80.0);
        SkyIslandEcologicalPatchSignalProfile second =
                SkyIslandEcologicalPatchSignalProfile.current(
                        0x1221E55L,
                        "ecology.patch.second",
                        80.0);

        SkyIslandVegetationSpatialPatchEvaluation firstEvaluation =
                new SkyIslandVegetationSpatialPatchProfile(
                                first,
                                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE)
                        .evaluate(group);
        SkyIslandVegetationSpatialPatchEvaluation secondEvaluation =
                new SkyIslandVegetationSpatialPatchProfile(
                                second,
                                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE)
                        .evaluate(group);

        assertTrue(
                Double.doubleToLongBits(firstEvaluation.rawSignal())
                        != Double.doubleToLongBits(secondEvaluation.rawSignal()));
        assertEquals(group, firstEvaluation.functionalGroupEvaluation());
        assertEquals(group, secondEvaluation.functionalGroupEvaluation());
    }

    @Test
    void candidateLatticeIsDeterministicAndRetainsExactProvenance() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                candidateProfile(
                        0x1223A11L,
                        "ecology.candidate.shrub",
                        24.0,
                        4.0);

        SkyIslandEcologicalPlacementCandidate first = profile.candidate(7L, -5L);
        SkyIslandEcologicalPlacementCandidate second = profile.candidate(7L, -5L);

        assertEquals(first, second);
        assertEquals(profile, first.latticeProfile());
        assertEquals(7L, first.cellX());
        assertEquals(-5L, first.cellZ());
        assertTrue(Double.isFinite(first.position().x()));
        assertTrue(Double.isFinite(first.position().z()));
        assertTrue(first.admissionValue() >= 0.0 && first.admissionValue() < 1.0);
        assertEquals(16.0, profile.guaranteedMinimumSpacing(), 0.0);
    }

    @Test
    void fabricatedCandidateGeometryOrAdmissionFailsClosed() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                candidateProfile(
                        0x1223A12L,
                        "ecology.candidate.provenance",
                        24.0,
                        4.0);
        SkyIslandEcologicalPlacementCandidate canonical = profile.candidate(3L, -4L);
        SkyIslandLocalPosition shifted =
                new SkyIslandLocalPosition(
                        canonical.position().x() + 0.25,
                        canonical.position().z());
        double alteredAdmission = canonical.admissionValue() == 0.0
                ? Math.nextUp(canonical.admissionValue())
                : Math.nextDown(canonical.admissionValue());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalPlacementCandidate(
                        profile,
                        canonical.cellX(),
                        canonical.cellZ(),
                        shifted,
                        canonical.admissionValue()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalPlacementCandidate(
                        profile,
                        canonical.cellX(),
                        canonical.cellZ(),
                        canonical.position(),
                        alteredAdmission));
    }

    @Test
    void candidateIdentityChangesRealizationWithoutChangingSpacingPolicy() {
        SkyIslandEcologicalCandidateLatticeProfile first =
                candidateProfile(
                        0x1223B22L,
                        "ecology.candidate.first",
                        20.0,
                        3.0);
        SkyIslandEcologicalCandidateLatticeProfile second =
                candidateProfile(
                        0x1223B22L,
                        "ecology.candidate.second",
                        20.0,
                        3.0);

        SkyIslandEcologicalPlacementCandidate firstCandidate = first.candidate(2L, 3L);
        SkyIslandEcologicalPlacementCandidate secondCandidate = second.candidate(2L, 3L);

        assertEquals(first.cellPitch(), second.cellPitch(), 0.0);
        assertEquals(first.maxAxisJitter(), second.maxAxisJitter(), 0.0);
        assertTrue(
                !firstCandidate.position().equals(secondCandidate.position())
                        || Double.doubleToLongBits(firstCandidate.admissionValue())
                                != Double.doubleToLongBits(secondCandidate.admissionValue()));
    }

    @Test
    void jitteredLatticePreservesGuaranteedMinimumSpacing() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                candidateProfile(
                        0x1223C33L,
                        "ecology.candidate.spacing",
                        20.0,
                        3.0);
        ArrayList<SkyIslandEcologicalPlacementCandidate> candidates = new ArrayList<>();
        for (long x = -3L; x <= 3L; x++) {
            for (long z = -3L; z <= 3L; z++) {
                candidates.add(profile.candidate(x, z));
            }
        }

        double minimum = profile.guaranteedMinimumSpacing();
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                SkyIslandLocalPosition a = candidates.get(first).position();
                SkyIslandLocalPosition b = candidates.get(second).position();
                double distance = Math.hypot(a.x() - b.x(), a.z() - b.z());
                assertTrue(distance + 1.0e-12 >= minimum);
            }
        }
    }

    @Test
    void supportThresholdAdmissionIsExactAndMonotone() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1223D44L,
                        "ecology.candidate.threshold",
                        18.0,
                        2.0);
        SkyIslandVegetationPlacementProfile placement =
                placementProfile(candidateProfile);
        SkyIslandEcologicalPlacementCandidate candidate = placement.candidate(4L, -2L);
        double threshold = candidate.admissionValue();
        double aboveThreshold = Math.nextUp(threshold);

        SkyIslandVegetationPlacementCandidateEvaluation zero =
                placement.evaluate(spatialEvaluationAt(candidate.position(), 0.0), candidate);
        SkyIslandVegetationPlacementCandidateEvaluation equal =
                placement.evaluate(spatialEvaluationAt(candidate.position(), threshold), candidate);
        SkyIslandVegetationPlacementCandidateEvaluation above =
                placement.evaluate(
                        spatialEvaluationAt(candidate.position(), aboveThreshold),
                        candidate);
        SkyIslandVegetationPlacementCandidateEvaluation unit =
                placement.evaluate(spatialEvaluationAt(candidate.position(), 1.0), candidate);

        assertFalse(zero.admissionState().orElseThrow().admitted());
        assertFalse(equal.admissionState().orElseThrow().admitted());
        assertTrue(above.admissionState().orElseThrow().admitted());
        assertTrue(unit.admissionState().orElseThrow().admitted());
        assertEquals(candidate, unit.candidate());
        assertEquals(placement, unit.placementProfile());
        assertEquals(candidate.position(), unit.position());
    }

    @Test
    void unresolvedSpatialSupportPropagatesToUnresolvedCandidateAdmission() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1223E55L,
                        "ecology.candidate.unresolved",
                        22.0,
                        4.0);
        SkyIslandVegetationPlacementProfile placement =
                placementProfile(candidateProfile);
        SkyIslandEcologicalPlacementCandidate candidate = placement.candidate(-2L, 6L);

        SkyIslandVegetationPlacementCandidateEvaluation evaluation =
                placement.evaluate(
                        unresolvedSpatialEvaluationAt(candidate.position()),
                        candidate);

        assertFalse(evaluation.spatialPatchEvaluation().resolved());
        assertFalse(evaluation.resolved());
        assertTrue(evaluation.admissionState().isEmpty());
    }

    @Test
    void mismatchedCandidatePositionFailsClosed() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1223F66L,
                        "ecology.candidate.position",
                        16.0,
                        2.0);
        SkyIslandVegetationPlacementProfile placement =
                placementProfile(candidateProfile);
        SkyIslandEcologicalPlacementCandidate candidate = placement.candidate(1L, 1L);
        SkyIslandLocalPosition mismatch =
                new SkyIslandLocalPosition(
                        candidate.position().x() + 1.0,
                        candidate.position().z());

        assertThrows(
                IllegalArgumentException.class,
                () -> placement.evaluate(spatialEvaluationAt(mismatch, 1.0), candidate));
    }

    @Test
    void candidateFromDifferentPlacementProfileFailsClosed() {
        SkyIslandEcologicalCandidateLatticeProfile first =
                candidateProfile(
                        0x1223077L,
                        "ecology.candidate.owner-first",
                        16.0,
                        2.0);
        SkyIslandEcologicalCandidateLatticeProfile second =
                candidateProfile(
                        0x1223077L,
                        "ecology.candidate.owner-second",
                        16.0,
                        2.0);
        SkyIslandVegetationPlacementProfile placement = placementProfile(first);
        SkyIslandEcologicalPlacementCandidate foreign = second.candidate(0L, 0L);

        assertThrows(
                IllegalArgumentException.class,
                () -> placement.evaluate(
                        spatialEvaluationAt(foreign.position(), 1.0),
                        foreign));
    }

    @Test
    void customAdmissionCannotCreatePresenceFromZeroSpatialSupport() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1223078L,
                        "ecology.candidate.invalid-admission",
                        16.0,
                        2.0);
        SkyIslandVegetationPlacementProfile invalid =
                new SkyIslandVegetationPlacementProfile(
                        candidateProfile,
                        (spatial, candidate) -> Optional.of(
                                new SkyIslandVegetationPlacementAdmissionState(true)));
        SkyIslandEcologicalPlacementCandidate candidate = invalid.candidate(0L, 0L);

        assertThrows(
                IllegalArgumentException.class,
                () -> invalid.evaluate(
                        spatialEvaluationAt(candidate.position(), 0.0),
                        candidate));
    }

    @Test
    void candidateWindowCompositionMatchesDirectPlacementEvaluation() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1227A11L,
                        "ecology.window.shrub",
                        10.0,
                        2.0);
        SkyIslandVegetationPlacementProfile placement = placementProfile(candidateProfile);
        SkyIslandVegetationSpatialPatchSampler sampler = localPosition -> {
            if (localPosition.z() < 0.0) {
                return unresolvedSpatialEvaluationAt(localPosition);
            }
            return spatialEvaluationAt(
                    localPosition,
                    localPosition.x() < 0.0 ? 0.0 : 1.0);
        };
        SkyIslandVegetationCandidateWindowProfile profile =
                new SkyIslandVegetationCandidateWindowProfile(
                        placement,
                        sampler);
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(
                        -60.0,
                        60.0,
                        -60.0,
                        60.0);

        SkyIslandVegetationCandidateWindowEvaluation first = profile.evaluate(window);
        SkyIslandVegetationCandidateWindowEvaluation second = profile.evaluate(window);
        SkyIslandEcologicalCandidateWindowResult directQuery =
                candidateProfile.query(window);

        assertEquals(first, second);
        assertEquals(profile, first.profile());
        assertEquals(directQuery, first.candidateQuery());
        assertEquals(directQuery.candidates().size(), first.evaluations().size());

        for (int index = 0; index < directQuery.candidates().size(); index++) {
            SkyIslandEcologicalPlacementCandidate candidate =
                    directQuery.candidates().get(index);
            SkyIslandVegetationPlacementCandidateEvaluation direct =
                    placement.evaluate(
                            sampler.sample(candidate.position()),
                            candidate);
            assertEquals(candidate, first.evaluations().get(index).candidate());
            assertEquals(direct, first.evaluations().get(index));
        }

        assertFalse(first.admitted().isEmpty());
        assertFalse(first.rejected().isEmpty());
        assertFalse(first.unresolved().isEmpty());
        assertEquals(
                first.evaluations().size(),
                first.admitted().size()
                        + first.rejected().size()
                        + first.unresolved().size());
    }

    @Test
    void candidateWindowCompositionFailsClosedOnWrongSamplePosition() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1227B22L,
                        "ecology.window.position",
                        12.0,
                        2.0);
        SkyIslandVegetationPlacementProfile placement = placementProfile(candidateProfile);
        SkyIslandVegetationCandidateWindowProfile profile =
                new SkyIslandVegetationCandidateWindowProfile(
                        placement,
                        localPosition -> spatialEvaluationAt(
                                new SkyIslandLocalPosition(
                                        localPosition.x() + 1.0,
                                        localPosition.z()),
                                1.0));
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(
                        -30.0,
                        30.0,
                        -30.0,
                        30.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> profile.evaluate(window));
    }

    @Test
    void zeroSupportCandidateWindowCannotCreateAdmission() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1227C33L,
                        "ecology.window.zero-support",
                        11.0,
                        2.0);
        SkyIslandVegetationCandidateWindowProfile profile =
                new SkyIslandVegetationCandidateWindowProfile(
                        placementProfile(candidateProfile),
                        localPosition -> spatialEvaluationAt(localPosition, 0.0));
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(
                        -40.0,
                        40.0,
                        -40.0,
                        40.0);

        SkyIslandVegetationCandidateWindowEvaluation evaluation =
                profile.evaluate(window);

        assertFalse(evaluation.evaluations().isEmpty());
        assertTrue(evaluation.admitted().isEmpty());
        assertTrue(evaluation.unresolved().isEmpty());
        assertEquals(evaluation.evaluations(), evaluation.rejected());
    }

    @Test
    void candidateWindowResultRejectsReorderedOrIncompleteEvaluationLists() {
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile =
                candidateProfile(
                        0x1227D44L,
                        "ecology.window.provenance",
                        10.0,
                        1.0);
        SkyIslandVegetationCandidateWindowProfile profile =
                new SkyIslandVegetationCandidateWindowProfile(
                        placementProfile(candidateProfile),
                        localPosition -> spatialEvaluationAt(localPosition, 1.0));
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(
                        -30.0,
                        30.0,
                        -30.0,
                        30.0);
        SkyIslandVegetationCandidateWindowEvaluation canonical =
                profile.evaluate(window);
        assertTrue(canonical.evaluations().size() >= 2);

        ArrayList<SkyIslandVegetationPlacementCandidateEvaluation> reordered =
                new ArrayList<>(canonical.evaluations());
        SkyIslandVegetationPlacementCandidateEvaluation first = reordered.get(0);
        reordered.set(0, reordered.get(1));
        reordered.set(1, first);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandVegetationCandidateWindowEvaluation(
                        profile,
                        canonical.candidateQuery(),
                        reordered));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandVegetationCandidateWindowEvaluation(
                        profile,
                        canonical.candidateQuery(),
                        canonical.evaluations().subList(
                                0,
                                canonical.evaluations().size() - 1)));
    }

    @Test
    void invalidCandidateLatticeParametersFailClosed() {
        SkyIslandEcologicalCandidateLatticeProfile valid =
                candidateProfile(
                        0x1223088L,
                        "ecology.candidate.valid",
                        16.0,
                        2.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandEcologicalCandidateLatticeProfile.current(
                        1L, "ecology.candidate.zero-pitch", 0.0, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandEcologicalCandidateLatticeProfile.current(
                        1L, "ecology.candidate.nan-pitch", Double.NaN, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandEcologicalCandidateLatticeProfile.current(
                        1L, "ecology.candidate.negative-jitter", 16.0, -1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandEcologicalCandidateLatticeProfile.current(
                        1L, "ecology.candidate.half-pitch", 16.0, 8.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateLatticeProfile(
                        valid.samplerVersion() + 1,
                        valid.seedVersion(),
                        valid.rootSeed(),
                        valid.namespace(),
                        valid.cellPitch(),
                        valid.maxAxisJitter()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateLatticeProfile(
                        valid.samplerVersion(),
                        valid.seedVersion() + 1,
                        valid.rootSeed(),
                        valid.namespace(),
                        valid.cellPitch(),
                        valid.maxAxisJitter()));
    }

    private static SkyIslandVegetationFunctionalGroupProfile functionalGroupProfile() {
        return new SkyIslandVegetationFunctionalGroupProfile(
                SkyIslandVegetationFunctionalGroup.SHRUB,
                uniformAffinity(),
                SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE);
    }

    private static SkyIslandVegetationSpatialPatchProfile spatialProfile(
            long rootSeed,
            String namespace,
            double scale) {
        return new SkyIslandVegetationSpatialPatchProfile(
                SkyIslandEcologicalPatchSignalProfile.current(rootSeed, namespace, scale),
                SkyIslandPatchinessRetentionSpatialTransform.INSTANCE);
    }

    private static SkyIslandEcologicalCandidateLatticeProfile candidateProfile(
            long rootSeed,
            String namespace,
            double cellPitch,
            double maxAxisJitter) {
        return SkyIslandEcologicalCandidateLatticeProfile.current(
                rootSeed,
                namespace,
                cellPitch,
                maxAxisJitter);
    }

    private static SkyIslandVegetationPlacementProfile placementProfile(
            SkyIslandEcologicalCandidateLatticeProfile candidateProfile) {
        return new SkyIslandVegetationPlacementProfile(
                candidateProfile,
                SkyIslandSupportThresholdVegetationPlacementAdmissionTransform.INSTANCE);
    }

    private static SkyIslandVegetationSpatialPatchEvaluation spatialEvaluationAt(
            SkyIslandLocalPosition localPosition,
            double support) {
        SkyIslandMultiCommunityRealizationComposition composition =
                withPatchiness(resolvedComposition(localPosition), 0.0);
        SkyIslandVegetationFunctionalGroupEvaluation base =
                functionalGroupProfile().evaluate(composition);
        SkyIslandVegetationFunctionalGroupEvaluation forced =
                new SkyIslandVegetationFunctionalGroupEvaluation(
                        base.profile(),
                        base.composition(),
                        OptionalDouble.of(support));
        return spatialProfile(
                        0x1223099L,
                        "ecology.patch.placement-fixture",
                        64.0)
                .evaluate(forced);
    }

    private static SkyIslandVegetationSpatialPatchEvaluation unresolvedSpatialEvaluationAt(
            SkyIslandLocalPosition localPosition) {
        SkyIslandVegetationFunctionalGroupEvaluation group =
                functionalGroupProfile().evaluate(unresolvedComposition(localPosition));
        return spatialProfile(
                        0x1223099L,
                        "ecology.patch.placement-fixture",
                        64.0)
                .evaluate(group);
    }

    private static SkyIslandMultiCommunityRealizationComposition withPatchiness(
            SkyIslandMultiCommunityRealizationComposition composition,
            double patchiness) {
        SkyIslandCommunityStructureRealization source =
                composition.aggregateRealization().orElseThrow();
        SkyIslandCommunityStructureRealization patched =
                new SkyIslandCommunityStructureRealization(
                        source.vegetationDensity(),
                        source.canopyCover(),
                        source.canopyHeightPotential(),
                        source.understoryDensity(),
                        source.groundCover(),
                        source.biomassPotential(),
                        patchiness,
                        source.organicSurfaceAccumulationPotential(),
                        source.deadwoodPotential());
        return new SkyIslandMultiCommunityRealizationComposition(
                composition.realizationSet(),
                composition.coexistenceWeights(),
                composition.compositor(),
                Optional.of(patched));
    }

    private static SkyIslandFunctionalGroupStructuralAffinity uniformAffinity() {
        return new SkyIslandFunctionalGroupStructuralAffinity(
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private static SkyIslandMultiCommunityRealizationComposition resolvedComposition() {
        return resolvedComposition(position);
    }

    private static SkyIslandMultiCommunityRealizationComposition resolvedComposition(
            SkyIslandLocalPosition localPosition) {
        SkyIslandCommunityRealizationEvaluation woodland =
                realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8, localPosition);
        SkyIslandCommunityRealizationEvaluation grass =
                realization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.5, localPosition);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));
        return SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);
    }

    private static SkyIslandMultiCommunityRealizationComposition unresolvedComposition() {
        return unresolvedComposition(position);
    }

    private static SkyIslandMultiCommunityRealizationComposition unresolvedComposition(
            SkyIslandLocalPosition localPosition) {
        SkyIslandCommunityRealizationEvaluation woodland =
                realization(SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.8, localPosition);
        SkyIslandCommunityRealizationEvaluation grass =
                unresolvedRealization(SkyIslandCommunityArchetype.OPEN_HERBACEOUS, localPosition);
        SkyIslandMultiCommunityRealizationSet set =
                new SkyIslandMultiCommunityRealizationSet(List.of(woodland, grass));
        return SkyIslandMultiCommunityRealizationComposition.evaluate(
                set,
                new SkyIslandCommunityCoexistenceWeights(Map.of(
                        SkyIslandCommunityArchetype.CLOSED_WOODLAND, 0.5,
                        SkyIslandCommunityArchetype.OPEN_HERBACEOUS, 0.3)),
                SkyIslandWeightedAdditiveRealizationCompositor.INSTANCE);
    }

    private static SkyIslandCommunityRealizationEvaluation realization(
            SkyIslandCommunityArchetype community,
            double forcedSupport) {
        return realization(community, forcedSupport, position);
    }

    private static SkyIslandCommunityRealizationEvaluation realization(
            SkyIslandCommunityArchetype community,
            double forcedSupport,
            SkyIslandLocalPosition localPosition) {
        SkyIslandCommunityAssemblyEvaluation base = assembly(community, localPosition);
        return realizationProfile().evaluate(
                withSupport(base, OptionalDouble.of(forcedSupport)));
    }

    private static SkyIslandCommunityRealizationEvaluation unresolvedRealization(
            SkyIslandCommunityArchetype community) {
        return unresolvedRealization(community, position);
    }

    private static SkyIslandCommunityRealizationEvaluation unresolvedRealization(
            SkyIslandCommunityArchetype community,
            SkyIslandLocalPosition localPosition) {
        return realizationProfile().evaluate(
                withSupport(assembly(community, localPosition), OptionalDouble.empty()));
    }

    private static SkyIslandCommunityAssemblyEvaluation assembly(
            SkyIslandCommunityArchetype community) {
        return assembly(community, position);
    }

    private static SkyIslandCommunityAssemblyEvaluation assembly(
            SkyIslandCommunityArchetype community,
            SkyIslandLocalPosition localPosition) {
        return new SkyIslandCommunityAssemblyProfile(
                        community,
                        new SkyIslandExponentialDispersalProfile(1_000.0),
                        new SkyIslandExponentialSuccessionProfile(100.0),
                        new SkyIslandWeightedSuccessionAffinityProfile(0.25, 0.75),
                        SkyIslandMultiplicativeAssemblyCombiner.INSTANCE)
                .evaluate(disturbanceEvidence, localPosition);
    }

    private static SkyIslandCommunityAssemblyEvaluation withSupport(
            SkyIslandCommunityAssemblyEvaluation base,
            OptionalDouble support) {
        return new SkyIslandCommunityAssemblyEvaluation(
                base.profile(),
                base.disturbanceEvidence(),
                base.position(),
                base.localSuitability(),
                base.dispersalAccessibility(),
                base.successionAssessment(),
                base.successionAffinity(),
                support);
    }

    private static SkyIslandCommunityRealizationProfile realizationProfile() {
        return new SkyIslandCommunityRealizationProfile(
                new SkyIslandCommunityStructureCapacity(
                        0.8, 0.7, 0.6, 0.5, 0.9, 0.75, 0.4, 0.55, 0.3),
                SkyIslandLinearSupportRealizationTransform.INSTANCE);
    }

    private static List<SkyIslandCommunityAssemblyEvidence> evidence(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(121_600L + ordinal, volume),
                    volume));
            ordinal++;
        }
        SkyIslandAuthoredRealizationCatalog catalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations);
        SkyIslandAuthoredRealizationIsolationProfile isolation =
                new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        return new SkyIslandCommunityAssemblyEvidenceBinder()
                .bindCurrentSemantics(isolation);
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 12L, 120L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        var morphology = realized.hasSemanticMorphologyFamily()
                ? realized.morphologyFamily()
                : base.morphologyFamily();
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                morphology,
                realized.nominalRadius(),
                base.reliefBudget(),
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
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
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException(
                    "functional-group niche fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology);
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "functional-group-niche",
                        SkyIslandGroupRole.ANCHOR,
                        volumeDescriptor(),
                        360.0,
                        48.0,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor volumeDescriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0,
                48.0,
                64.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0);
    }
}
