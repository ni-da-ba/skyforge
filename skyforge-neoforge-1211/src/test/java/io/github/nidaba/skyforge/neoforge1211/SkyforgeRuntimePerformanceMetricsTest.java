package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyforgeRuntimePerformanceMetricsTest {
    @Test
    void nearestRankPercentilesAreDeterministicAtAcceptanceBoundaries() {
        List<Long> ordered = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);

        assertEquals(10L, SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(ordered, 50));
        assertEquals(19L, SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(ordered, 95));
        assertEquals(20L, SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(ordered, 99));
        assertEquals(0L, SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(List.of(), 50));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(ordered, 0));
    }
}
