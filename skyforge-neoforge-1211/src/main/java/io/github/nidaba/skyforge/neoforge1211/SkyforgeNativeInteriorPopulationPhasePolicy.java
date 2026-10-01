package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Phase-level authorization for production native interior population after final composed caves.
 *
 * <p>Core underground phases remain in Minecraft generation-step order. VEGETAL_DECORATION is
 * included only for the cave-dependent route split by SF-IMP-0079; ordinary surface vegetation is
 * still executed by the earlier surface-population lifecycle and is not replayed here.
 */
final class SkyforgeNativeInteriorPopulationPhasePolicy {
    private static final List<GenerationStep.Decoration> ADMITTED = List.of(
            GenerationStep.Decoration.LAKES,
            GenerationStep.Decoration.LOCAL_MODIFICATIONS,
            GenerationStep.Decoration.UNDERGROUND_ORES,
            GenerationStep.Decoration.UNDERGROUND_DECORATION,
            GenerationStep.Decoration.FLUID_SPRINGS,
            GenerationStep.Decoration.VEGETAL_DECORATION);

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
