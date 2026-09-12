package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * C21 production authority boundary for native Create underground resources.
 *
 * <p>This predicate is called only by exact-volume Skyforge native population. It therefore leaves
 * ordinary Overworld/Create generation untouched while preventing biome-modifier-injected Create
 * ore features from competing with Skyforge-authored resource realization inside owned volumes.
 */
final class SkyforgeCreateUndergroundResourceAuthority {
    private SkyforgeCreateUndergroundResourceAuthority() {}

    static boolean admits(ResourceLocation featureKey, GenerationStep.Decoration generationStep) {
        Objects.requireNonNull(featureKey, "featureKey");
        Objects.requireNonNull(generationStep, "generationStep");
        return generationStep != GenerationStep.Decoration.UNDERGROUND_ORES
                || !featureKey.getNamespace().equals("create");
    }
}
