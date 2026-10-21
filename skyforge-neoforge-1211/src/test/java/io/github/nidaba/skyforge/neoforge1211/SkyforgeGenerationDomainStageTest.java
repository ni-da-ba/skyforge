package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeGenerationDomainStageTest {
    @Test
    void baseWorldIsImplicitAndIslandOwnershipRequiresExplicitScope() {
        var unrelatedId = new io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId(
                19L, "unrelated", 0, 0, 20L);
        var volumeId = SkyforgeNeoForge1211DevRuntime.catalog().volumes().getFirst().id();

        assertTrue(SkyforgeGenerationDomainStage.isBaseWorld());
        assertTrue(SkyforgeGenerationDomainStage.activeIslandVolumeId().isEmpty());

        try (var scope = SkyforgeGenerationDomainStage.openIsland(volumeId)) {
            scope.requireActive();
            SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);
            assertEquals(volumeId, SkyforgeGenerationDomainStage.activeIslandVolumeId().orElseThrow());
            assertThrows(
                    IllegalStateException.class,
                    () -> SkyforgeGenerationDomainStage.openIsland(volumeId),
                    "an island population pass must never nest another terrain owner implicitly");
            assertThrows(
                    IllegalStateException.class,
                    () -> SkyforgeGenerationDomainStage.requireExactIslandVolume(unrelatedId));
        }

        assertThrows(IllegalStateException.class, () -> SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId));
        assertTrue(SkyforgeGenerationDomainStage.isBaseWorld());
        assertTrue(SkyforgeGenerationDomainStage.activeIslandVolumeId().isEmpty());
    }
}
