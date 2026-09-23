package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeCatchupServiceTest {
    @Test
    void terrainPumpStopsAtHardChunkCap() {
        var calls = new AtomicInteger();

        var result = SkyforgePhysicalVolumeCatchupService.pumpTerrainCatchupChunks(
                () -> {
                    calls.incrementAndGet();
                    return true;
                },
                () -> 0L,
                2,
                8L);

        assertEquals(2, result.workedQuanta());
        assertEquals(2, calls.get());
    }

    @Test
    void terrainPumpAlwaysAllowsOneSlowChunkThenYields() {
        var calls = new AtomicInteger();
        LongSupplier clock = clock(0L, 20L, 20L);

        var result = SkyforgePhysicalVolumeCatchupService.pumpTerrainCatchupChunks(
                () -> {
                    calls.incrementAndGet();
                    return true;
                },
                clock,
                64,
                8L);

        assertEquals(1, result.workedQuanta());
        assertEquals(1, calls.get());
        assertEquals(20L, result.elapsedNanos());
    }

    @Test
    void terrainPumpStopsWhenCanonicalChunkCannotProgress() {
        var calls = new AtomicInteger();

        var result = SkyforgePhysicalVolumeCatchupService.pumpTerrainCatchupChunks(
                () -> {
                    calls.incrementAndGet();
                    return false;
                },
                () -> 0L,
                64,
                8L);

        assertEquals(0, result.workedQuanta());
        assertEquals(1, calls.get());
    }

    @Test
    void canonicalPopulationChunkKeysSortByChunkXThenZ() {
        long a = new ChunkPos(1, -2).toLong();
        long b = new ChunkPos(-1, 4).toLong();
        long c = new ChunkPos(-1, -3).toLong();
        long d = new ChunkPos(0, 9).toLong();

        assertEquals(
                List.of(c, b, d, a),
                SkyforgePhysicalVolumeCatchupService.canonicalPopulationChunkKeys(
                        Set.of(a, b, c, d)));
    }

    @Test
    void populationDependenciesRequireEveryEarlierCanonicalChunk() {
        long farEarlier = new ChunkPos(-6, 0).toLong();
        long nearbyEarlier = new ChunkPos(-2, 0).toLong();
        long candidate = new ChunkPos(0, 0).toLong();
        long later = new ChunkPos(1, 0).toLong();
        List<Long> canonical = List.of(farEarlier, nearbyEarlier, candidate, later);

        assertEquals(
                List.of(farEarlier, nearbyEarlier),
                SkyforgePhysicalVolumeCatchupService.earlierPopulationKeys(
                        candidate,
                        canonical));
    }


    @Test
    void finalSurfaceWaitsForWholeVolumeCaveTopology() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volumeId = fixture.volume().id();
        assertTrue(SkyforgePhysicalVolumeCatchupService.caveTopologyReadyForVolume(volumeId));

        try (AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog());
                AutoCloseable caves = SkyforgeComposedCaveStage.install(List.of(
                        new SkyforgeComposedCavePlan(fixture.volume(), fixture.field())))) {
            assertTrue(admission != null);
            assertTrue(caves != null);
            assertFalse(
                    SkyforgePhysicalVolumeCatchupService.caveTopologyReadyForVolume(volumeId),
                    "final surface representation must not precede pending cave topology");
        }

        assertTrue(SkyforgePhysicalVolumeCatchupService.caveTopologyReadyForVolume(volumeId));
    }

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
