package io.github.nidaba.skyforge.neoforge1211;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/** Installs Skyforge's conservative correction into Minecraft's shared terrain-matching projection. */
final class SkyforgeTerrainProjectionBootstrap {
    private static final System.Logger LOGGER = System.getLogger(SkyforgeTerrainProjectionBootstrap.class.getName());

    private SkyforgeTerrainProjectionBootstrap() {}

    static synchronized void install() {
        List<StructureProcessor> current = StructureTemplatePool.Projection.TERRAIN_MATCHING.processors;
        if (current.stream().anyMatch(SkyforgeTerrainScopedGravityProcessor.class::isInstance)) {
            return;
        }

        long vanillaGravityCount = current.stream()
                .filter(processor -> processor.getClass() == GravityProcessor.class)
                .count();
        if (vanillaGravityCount != 1L) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Skyforge left terrain-matching projection unchanged because its shared processor list no longer "
                            + "contains exactly one vanilla GravityProcessor. Another mod may own this seam: "
                            + current);
            return;
        }

        ArrayList<StructureProcessor> replacement = new ArrayList<>(current.size());
        for (StructureProcessor processor : current) {
            replacement.add(processor.getClass() == GravityProcessor.class
                    ? SkyforgeTerrainScopedGravityProcessor.INSTANCE
                    : processor);
        }
        StructureTemplatePool.Projection.TERRAIN_MATCHING.processors = ImmutableList.copyOf(replacement);
    }

    static boolean installed() {
        return StructureTemplatePool.Projection.TERRAIN_MATCHING.processors.stream()
                .anyMatch(SkyforgeTerrainScopedGravityProcessor.class::isInstance);
    }
}
