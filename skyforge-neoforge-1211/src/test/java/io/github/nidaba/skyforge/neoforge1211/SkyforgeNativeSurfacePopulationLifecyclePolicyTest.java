package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyforgeNativeSurfacePopulationLifecyclePolicyTest {
    @Test
    void physicallyAdmittedWorldGenPopulationDefersToStableChunk() {
        assertTrue(SkyforgeNativeSurfacePopulationStage.shouldDeferToStableChunk(true, true));
        assertFalse(SkyforgeNativeSurfacePopulationStage.shouldDeferToStableChunk(true, false));
        assertFalse(SkyforgeNativeSurfacePopulationStage.shouldDeferToStableChunk(false, true));
        assertFalse(SkyforgeNativeSurfacePopulationStage.shouldDeferToStableChunk(false, false));
    }
}
