package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeComposedCaveStageTest {
    @Test
    void stageRequiresPhysicalAdmissionBinding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertThrows(
                IllegalStateException.class,
                () -> SkyforgeComposedCaveStage.install(List.of(
                        new SkyforgeComposedCavePlan(fixture.volume(), fixture.field()))));
        assertFalse(SkyforgeComposedCaveStage.active());
    }

    @Test
    void installExpandsExactlyTheAdmissionFootprintAndStartsAllPending() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volumeId = fixture.volume().id();

        try (AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog())) {
            assertNotNull(admission);
            int required = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId).size();
            assertTrue(required > 0);

            try (AutoCloseable composed = SkyforgeComposedCaveStage.install(List.of(
                    new SkyforgeComposedCavePlan(fixture.volume(), fixture.field())))) {
                assertNotNull(composed);
                var aggregate = SkyforgeComposedCaveStage.snapshot();
                var exact = SkyforgeComposedCaveStage.snapshot(volumeId);

                assertEquals(required, aggregate.totalObligations());
                assertEquals(required, aggregate.pendingObligations());
                assertEquals(0, aggregate.completedObligations());
                assertEquals(0, aggregate.emptyObligations());
                assertEquals(aggregate, exact);
                assertEquals(
                        SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId),
                        SkyforgeComposedCaveStage.pendingChunkKeys());

                var expectedOrder = new ArrayList<>(
                        SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId));
                expectedOrder.sort(Comparator
                        .comparingInt((Long key) -> ChunkPos.getX(key))
                        .thenComparingInt(key -> ChunkPos.getZ(key)));
                assertEquals(
                        expectedOrder,
                        new ArrayList<>(SkyforgeComposedCaveStage.pendingChunkKeys()),
                        "bounded catch-up scheduling must preserve deterministic plan order");
            }
        }

        assertFalse(SkyforgeComposedCaveStage.active());
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
    }

    @Test
    void duplicateExactVolumePlanIsRejectedBeforeBinding() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var plan = new SkyforgeComposedCavePlan(fixture.volume(), fixture.field());

        try (AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog())) {
            assertNotNull(admission);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SkyforgeComposedCaveStage.install(List.of(plan, plan)));
            assertFalse(SkyforgeComposedCaveStage.active());
        }
    }
}
