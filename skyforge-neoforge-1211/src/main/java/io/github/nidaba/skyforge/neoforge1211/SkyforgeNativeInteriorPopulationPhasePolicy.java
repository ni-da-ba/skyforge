package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Phase-level authorization for production native interior population after final composed caves.
 *
 * <p>Only phases already accepted independently by SF-IMP-0059/0060/0062/0063/0064 are admitted.
 * Feature identity remains registry-native and no structure, authored-material, aquifer, or
 * top-layer policy is introduced here.
 */
final class SkyforgeNativeInteriorPopulationPhasePolicy {
    private static final List<GenerationStep.Decoration> ADMITTED = List.of(
            GenerationStep.Decoration.LAKES,
            GenerationStep.Decoration.LOCAL_MODIFICATIONS,
            GenerationStep.Decoration.UNDERGROUND_ORES,
            GenerationStep.Decoration.UNDERGROUND_DECORATION,
            GenerationStep.Decoration.FLUID_SPRINGS);

    static {
        int previous = -1;
        for (GenerationStep.Decoration phase : ADMITTED) {
            if (phase.ordinal() <= previous) {
                throw new ExceptionInInitializerError(
                        "native interior phases must preserve Minecraft generation-step order");
            }
            previous = phase.ordinal();
        }
    }

    private SkyforgeNativeInteriorPopulationPhasePolicy() {}

    static List<GenerationStep.Decoration> admittedPhases() {
        return ADMITTED;
    }

    static boolean isAdmitted(GenerationStep.Decoration phase) {
        Objects.requireNonNull(phase, "phase");
        return ADMITTED.contains(phase);
    }

    static void requireAdmitted(GenerationStep.Decoration phase) {
        if (!isAdmitted(phase)) {
            throw new IllegalArgumentException(
                    "native generation phase is not admitted to post-cave exact-volume interior population: "
                            + phase);
        }
    }
}
