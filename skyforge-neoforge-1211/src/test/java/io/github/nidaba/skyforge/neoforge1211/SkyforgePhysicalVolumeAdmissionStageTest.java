package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeAdmissionStageTest {
    @Test
    void inactiveStagePreservesHistoricalPopulationBehavior() {
        var volumeId = SkyforgeNeoForge1211PopulationDevRuntime.catalog().volumes().getFirst().id();
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }

    @Test
    void plannedVolumeCannotPopulateBeforeWholeVolumeAdmission() throws Exception {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volumeId = catalog.volumes().getFirst().id();

        try (AutoCloseable binding = SkyforgePhysicalVolumeAdmissionStage.install(catalog)) {
            assertNotNull(binding);
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.active());
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
        }

        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }
}
