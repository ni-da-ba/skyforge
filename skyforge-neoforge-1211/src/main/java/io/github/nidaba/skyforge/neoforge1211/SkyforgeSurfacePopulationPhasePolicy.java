package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Semantic admission policy for Minecraft generation phases that are safe to replay inside one
 * exact Skyforge surface-population domain.
 *
 * <p>This policy deliberately works at generation-step granularity rather than feature identity.
 * Individual vanilla, datapack and modded placed features remain registry-native. Hydrology,
 * underground geology, ores and structures are admitted by their own future domain systems rather
 * than being accidentally pulled through surface ecology.
 */
final class SkyforgeSurfacePopulationPhasePolicy {
    private static final List<GenerationStep.Decoration> ADMITTED =
            List.of(GenerationStep.Decoration.VEGETAL_DECORATION);

    private SkyforgeSurfacePopulationPhasePolicy() {}

    static List<GenerationStep.Decoration> admittedPhases() {
        return ADMITTED;
    }

    static boolean isAdmitted(GenerationStep.Decoration phase) {
        Objects.requireNonNull(phase, "phase");
        return ADMITTED.contains(phase);
    }

    static void requireAdmitted(GenerationStep.Decoration phase) {
        if (!isAdmitted(phase)) {
            throw new IllegalArgumentException("native generation phase is not admitted to exact-volume surface "
                    + "population: " + phase + "; hydrology, underground geology, ores, structures and top-layer "
                    + "thermal/fluid semantics require their own explicit domain policy");
        }
    }
}
