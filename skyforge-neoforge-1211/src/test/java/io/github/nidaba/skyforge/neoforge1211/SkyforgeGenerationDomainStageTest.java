package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeGenerationDomainStageTest {
    @Test
    void baseWorldIsImplicitAndIslandOwnershipRequiresExplicitScope() {
        var volumeId = SkyforgeNeoForge1211DevRuntime.catalog().volumes().getFirst().id();

        assertTrue(SkyforgeGenerationDomainStage.isBaseWorld());
        assertTrue(SkyforgeGenerationDomainStage.activeIslandVolumeId().isEmpty());

        try (var scope = SkyforgeGenerationDomainStage.openIsland(volumeId)) {
            scope.requireActive();
            assertEquals(volumeId, SkyforgeGenerationDomainStage.activeIslandVolumeId().orElseThrow());
            assertThrows(
                    IllegalStateException.class,
                    () -> SkyforgeGenerationDomainStage.openIsland(volumeId),
                    "an island population pass must never nest another terrain owner implicitly");
        }

        assertTrue(SkyforgeGenerationDomainStage.isBaseWorld());
        assertTrue(SkyforgeGenerationDomainStage.activeIslandVolumeId().isEmpty());
    }
}
