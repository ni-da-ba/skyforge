package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamilySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.volume.MorphologyFamilyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Numerical acceptance for the first five primary suspended-landform families. */
final class MorphologyFamilySuspendedVolumeAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double SUSPENSION = 256.0;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyFamilySpecimenRemainsOneFiniteSuspendedMass(
            MorphologyFamilyReferenceCorpus.Member member) {
        CompiledSkyIslandVolume compiled = new MorphologyFamilySkyIslandVolumeRecipe().compile(
                MorphologyFamilyReferenceCorpus.descriptor(member), member.family());
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());
        assertTrue(metrics.bounds().minimumY() > SuspendedVolumeReferenceDomain.grid().minimumY());
        assertTrue(metrics.bounds().maximumY() < SuspendedVolumeReferenceDomain.grid().maximumY());
    }

    @Test
    void eachSharedSeedProducesFiveDistinctPrimaryFootprintMasks() {
        for (long seed : new long[] {Long.MIN_VALUE, 0L, 0x534b59464f524745L}) {
            Set<String> masks = new HashSet<>();
            for (MorphologyFamily family : MorphologyFamily.values()) {
                MorphologyFamilyReferenceCorpus.Member member = MorphologyFamilyReferenceCorpus.members()
                        .stream()
                        .filter(candidate -> candidate.seed() == seed && candidate.family() == family)
                        .findFirst()
                        .orElseThrow();
                CompiledSkyIslandVolume compiled = new MorphologyFamilySkyIslandVolumeRecipe().compile(
                        MorphologyFamilyReferenceCorpus.descriptor(member), family);
                ScalarField2 upper = new ReferenceEvaluator().field2(compiled.upperSurfaceGraph());
                StringBuilder mask = new StringBuilder();
                for (int z = -336; z <= 336; z += 12) {
                    for (int x = -336; x <= 336; x += 12) {
                        mask.append(upper.sample(new Coordinate2(x, z)) > SUSPENSION ? '1' : '0');
                    }
                }
                masks.add(mask.toString());
            }
            assertEquals(
                    MorphologyFamily.values().length,
                    masks.size(),
                    "families must remain numerically separable for seed=" + seed);
        }
    }

    private static Stream<MorphologyFamilyReferenceCorpus.Member> members() {
        return MorphologyFamilyReferenceCorpus.members().stream();
    }
}
