package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.recipes.island.SeededIslandRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FixedSeedReferenceCorpusTest {
    @Test
    void exposesTheAcceptedOrderedSixSeedSuite() {
        List<Long> seeds = FixedSeedReferenceCorpus.members().stream()
                .map(FixedSeedReferenceCorpus.Member::seed)
                .toList();

        assertEquals(List.of(
                Long.MIN_VALUE,
                -1L,
                0L,
                1L,
                0x534b59464f524745L,
                Long.MAX_VALUE), seeds);
        assertThrows(
                UnsupportedOperationException.class,
                () -> FixedSeedReferenceCorpus.members().add(
                        new FixedSeedReferenceCorpus.Member("unexpected", 2L)));
    }

    @Test
    void everyMemberUsesTheSameFullAmplitudeMorphologyContract() {
        for (FixedSeedReferenceCorpus.Member member : FixedSeedReferenceCorpus.members()) {
            var descriptor = FixedSeedReferenceCorpus.descriptor(member);
            var base = SignalFreeReferenceCorpus.standardDescriptor();
            assertEquals(member.seed(), descriptor.seed());
            assertEquals(1.0, descriptor.signalAmplitude());
            assertEquals(base.nominalRadius(), descriptor.nominalRadius());
            assertEquals(base.maximumElevation(), descriptor.maximumElevation());
            assertEquals(base.ridgeAzimuth(), descriptor.ridgeAzimuth());
            assertTrue(SeededIslandRecipe.MAXIMUM_RELATIVE_DISPLACEMENT < 1.0);
        }
    }
}
