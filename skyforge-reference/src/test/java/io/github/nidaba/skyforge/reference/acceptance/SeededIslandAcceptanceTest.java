package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.SeededIslandRecipe;
import io.github.nidaba.skyforge.recipes.island.SignalFreeIslandRecipe;
import io.github.nidaba.skyforge.reference.SignalFreeReferenceCorpus;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidence;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidenceGenerator;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executable seeded identity gate for the first bounded signal family. */
final class SeededIslandAcceptanceTest {
    private static final int FAST_RESOLUTION = 257;
    private static final long[] FIXED_SEEDS = {
        Long.MIN_VALUE,
        -1L,
        0L,
        1L,
        0x534b59464f524745L,
        Long.MAX_VALUE
    };

    private final SeededIslandRecipe seededRecipe = new SeededIslandRecipe();
    private final SignalFreeIslandRecipe signalFreeRecipe = new SignalFreeIslandRecipe();
    private final IslandEvidenceGenerator generator = new IslandEvidenceGenerator();

    @Test
    void sfIsl007PreservesIdentityAcrossTheFixedSeedSuite() {
        IslandDescriptor canonical = SignalFreeReferenceCorpus.standardDescriptor();
        GridSpec grid = fastGrid(canonical);
        IslandEvidence base = generator.generate(
                signalFreeRecipe.compile(canonical), grid, SamplingOrder.FORWARD);
        Set<String> seededHeightHashes = new HashSet<>();

        for (long seed : FIXED_SEEDS) {
            IslandDescriptor descriptor = withSignal(canonical, seed, 1.0);
            IslandEvidence seeded = generator.generate(
                    seededRecipe.compile(descriptor), grid, SamplingOrder.FORWARD);
            seededHeightHashes.add(seeded.height().sha256());

            assertAll(
                    () -> assertEquals(1, seeded.metrics().connectedLandComponents()),
                    () -> assertEquals(0, seeded.metrics().boundaryLandSampleCount()),
                    () -> assertEquals(base.landMask().sha256(), seeded.landMask().sha256()),
                    () -> assertEquals(base.metrics(), seeded.metrics()),
                    () -> assertTrue(seeded.heightStatistics().minimum() < 0.0),
                    () -> assertTrue(
                            seeded.heightStatistics().maximum()
                                    >= canonical.maximumElevation()
                                            * (1.0 - SeededIslandRecipe.MAXIMUM_RELATIVE_DISPLACEMENT)),
                    () -> assertTrue(
                            seeded.heightStatistics().maximum()
                                    <= canonical.maximumElevation()
                                            * (1.0 + SeededIslandRecipe.MAXIMUM_RELATIVE_DISPLACEMENT)));
        }

        assertEquals(FIXED_SEEDS.length, seededHeightHashes.size());
    }

    @Test
    void seededEvidenceIsIndependentOfSamplingSchedule() {
        IslandDescriptor descriptor = withSignal(
                SignalFreeReferenceCorpus.standardDescriptor(),
                0x534b59464f524745L,
                0.8);
        GridSpec grid = fastGrid(descriptor);
        var compiled = seededRecipe.compile(descriptor);
        IslandEvidence forward = generator.generate(compiled, grid, SamplingOrder.FORWARD);
        IslandEvidence reversed = generator.generate(compiled, grid, SamplingOrder.REVERSED);
        IslandEvidence parallel = generator.generate(compiled, grid, SamplingOrder.PARALLEL);

        assertAll(
                () -> assertEquals(forward.height().sha256(), reversed.height().sha256()),
                () -> assertEquals(forward.height().sha256(), parallel.height().sha256()),
                () -> assertEquals(forward.slope().sha256(), reversed.slope().sha256()),
                () -> assertEquals(forward.slope().sha256(), parallel.slope().sha256()),
                () -> assertEquals(forward.metrics(), reversed.metrics()),
                () -> assertEquals(forward.metrics(), parallel.metrics()));
    }

    private static IslandDescriptor withSignal(
            IslandDescriptor source,
            long seed,
            double amplitude) {
        return new IslandDescriptor(
                source.schemaVersion(),
                seed,
                source.centerX(),
                source.centerZ(),
                source.nominalRadius(),
                source.maximumElevation(),
                source.coastalFalloff(),
                source.ridgeAzimuth(),
                source.ridgeStrength(),
                amplitude,
                source.signalScale());
    }

    private static GridSpec fastGrid(IslandDescriptor descriptor) {
        double halfWidth = IslandEvidenceGenerator.STANDARD_HALF_WIDTH_FACTOR * descriptor.nominalRadius();
        return new GridSpec(
                descriptor.centerX() - halfWidth,
                descriptor.centerX() + halfWidth,
                descriptor.centerZ() - halfWidth,
                descriptor.centerZ() + halfWidth,
                FAST_RESOLUTION,
                FAST_RESOLUTION);
    }
}
