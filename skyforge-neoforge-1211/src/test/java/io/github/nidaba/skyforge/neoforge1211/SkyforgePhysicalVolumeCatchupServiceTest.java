package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeCatchupServiceTest {
    @Test
    void cavePumpStopsAtHardQuantumCap() {
        var calls = new AtomicInteger();

        var result = SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                () -> {
                    calls.incrementAndGet();
                    return true;
                },
                () -> 0L,
                3,
                8L);

        assertEquals(3, result.workedQuanta());
        assertEquals(3, calls.get());
    }

    @Test
    void cavePumpStopsBeforeStartingWorkAfterElapsedBudget() {
        var calls = new AtomicInteger();
        LongSupplier clock = clock(0L, 4L, 8L, 8L);

        var result = SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                () -> {
                    calls.incrementAndGet();
                    return true;
                },
                clock,
                128,
                8L);

        assertEquals(2, result.workedQuanta());
        assertEquals(2, calls.get());
        assertEquals(8L, result.elapsedNanos());
    }

    @Test
    void cavePumpAlwaysAllowsOneQuantumForForwardProgress() {
        var calls = new AtomicInteger();
        LongSupplier clock = clock(0L, 20L, 20L);

        var result = SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                () -> {
                    calls.incrementAndGet();
                    return true;
                },
                clock,
                128,
                8L);

        assertEquals(1, result.workedQuanta());
        assertEquals(1, calls.get());
        assertEquals(20L, result.elapsedNanos());
    }

    @Test
    void cavePumpStopsWhenCanonicalScanCannotProgress() {
        var calls = new AtomicInteger();

        var result = SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                () -> {
                    calls.incrementAndGet();
                    return false;
                },
                () -> 0L,
                128,
                8L);

        assertEquals(0, result.workedQuanta());
        assertEquals(1, calls.get());
    }

    @Test
    void cavePumpRejectsUnboundedConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                        () -> true,
                        () -> 0L,
                        0,
                        8L));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgePhysicalVolumeCatchupService.pumpComposedCaveQuanta(
                        () -> true,
                        () -> 0L,
                        1,
                        0L));
    }

    private static LongSupplier clock(long... readings) {
        ArrayDeque<Long> values = new ArrayDeque<>();
        for (long reading : readings) {
            values.add(reading);
        }
        return () -> {
            if (values.isEmpty()) {
                throw new AssertionError("test clock exhausted");
            }
            return values.removeFirst();
        };
    }
}
