package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeInteriorPopulationPlanTest {
    private static final SkyIslandWorldVolumeId VOLUME_ID =
            new SkyIslandWorldVolumeId(69L, "interior", 0, 0, 6900L);

    @Test
    void acceptedInteriorPhasesPreserveNativeOrderAndEndWithRoutedPostCaveVegetation() {
        List<GenerationStep.Decoration> phases =
                SkyforgeNativeInteriorPopulationPhasePolicy.admittedPhases();

        assertEquals(
                List.of(
                        GenerationStep.Decoration.LAKES,
                        GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                        GenerationStep.Decoration.UNDERGROUND_ORES,
                        GenerationStep.Decoration.UNDERGROUND_DECORATION,
                        GenerationStep.Decoration.FLUID_SPRINGS,
                        GenerationStep.Decoration.VEGETAL_DECORATION),
                phases);

        int previous = -1;
        for (GenerationStep.Decoration phase : phases) {
            assertTrue(phase.ordinal() > previous);
            previous = phase.ordinal();
        }
    }

    @Test
    void acceptedPlanUsesOnlyAuthorizedPostCavePhases() {
        var plan = SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(VOLUME_ID, 2);

        assertEquals(VOLUME_ID, plan.volumeId());
        assertEquals(SkyforgeNativeInteriorPopulationPhasePolicy.admittedPhases(), plan.phases());
        assertEquals(2, plan.maximumAttachmentDepth());
    }

    @Test
    void planRejectsNonInteriorSurfacePhaseAndOutOfOrderPhases() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeNativeInteriorPopulationPlan(
                        VOLUME_ID,
                        List.of(GenerationStep.Decoration.TOP_LAYER_MODIFICATION),
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeNativeInteriorPopulationPlan(
                        VOLUME_ID,
                        List.of(
                                GenerationStep.Decoration.VEGETAL_DECORATION,
                                GenerationStep.Decoration.FLUID_SPRINGS),
                        0));
    }

    @Test
    void inactiveStageHasNoPendingWork() {
        assertFalse(SkyforgeNativeInteriorPopulationStage.active());
        assertTrue(SkyforgeNativeInteriorPopulationStage.pendingChunkKeys().isEmpty());
        assertEquals(
                new SkyforgeNativeInteriorPopulationStage.Snapshot(0, 0, 0, 0),
                SkyforgeNativeInteriorPopulationStage.snapshot());
    }
}
