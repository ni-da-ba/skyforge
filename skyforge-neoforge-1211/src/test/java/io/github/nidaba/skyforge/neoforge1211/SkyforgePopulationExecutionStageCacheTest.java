package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationExecutionStageCacheTest {
    @Test
    void repeatedOwnershipQueriesReuseCompiledGeometryWithinOneExecution() {
        long rootSeed = 0x534b59464f524745L;
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(
                rootSeed,
                "ownership-cache",
                0,
                0,
                rootSeed ^ 0x4341434845L);
        SkyforgePopulationOperation operation = SkyforgePopulationOperation.create(
                volumeId,
                new ChunkPos(0, 0),
                ResourceLocation.fromNamespaceAndPath("minecraft", "patch_grass_plain"),
                GenerationStep.Decoration.VEGETAL_DECORATION.ordinal(),
                0);

        AtomicInteger ownerCalls = new AtomicInteger();
        AtomicInteger foreignCalls = new AtomicInteger();
        BlockPos owned = new BlockPos(1, 100, 1);
        BlockPos exterior = new BlockPos(3, 100, 3);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var scope = SkyforgePopulationExecutionStage.openForTest(
                        operation,
                        position -> {
                            ownerCalls.incrementAndGet();
                            return position.equals(owned);
                        },
                        position -> {
                            foreignCalls.incrementAndGet();
                            return false;
                        },
                        0)) {
            domain.requireActive();
            var execution = scope.execution();

            assertTrue(execution.isVisible(owned));
            assertTrue(execution.isVisible(owned.immutable()));
            assertEquals(1, ownerCalls.get(), "same packed owner coordinate must be evaluated once");

            assertFalse(execution.canWrite(exterior));
            assertFalse(execution.canWrite(exterior.immutable()));
            assertEquals(2, ownerCalls.get(), "exterior owner coordinate must also be evaluated once");
            assertEquals(1, foreignCalls.get(), "same foreign coordinate must be evaluated once");
        }
    }
}
