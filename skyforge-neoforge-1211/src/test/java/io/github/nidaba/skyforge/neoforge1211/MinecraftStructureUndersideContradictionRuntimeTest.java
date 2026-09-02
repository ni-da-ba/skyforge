package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class MinecraftStructureUndersideContradictionRuntimeTest {
    @Test
    void activeRuntimeProvesDetachedComponentBelowExactFoundationIsland() throws Exception {
        var adapter = SkyforgeNeoForge1211AccommodationDevRuntime.adapter();
        var volumeId = SkyforgeNeoForge1211AccommodationDevRuntime.catalog().volumes().getFirst().id();

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            BoundingBox surfaceRoot = new BoundingBox(4, 223, 4, 6, 230, 6);
            BoundingBox detachedBelow = new BoundingBox(4, 150, 4, 6, 152, 6);

            var contradiction = MinecraftStructureUndersideContradictionPolicy.evaluate(
                    List.of(surfaceRoot, detachedBelow),
                    223,
                    volumeId);

            assertTrue(contradiction.isPresent());
            assertEquals(volumeId, contradiction.orElseThrow().supportingVolumeId());
            assertEquals(1, contradiction.orElseThrow().separatedComponent().size());
            assertEquals(150, contradiction.orElseThrow().separatedComponent().getFirst().minY());
        }
    }
}
