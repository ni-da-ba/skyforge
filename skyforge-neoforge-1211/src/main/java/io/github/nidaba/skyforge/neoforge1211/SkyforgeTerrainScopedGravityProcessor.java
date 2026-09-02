package io.github.nidaba.skyforge.neoforge1211;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Vanilla terrain-matching gravity redirected through one explicit terrain domain.
 *
 * <p>The superclass still supplies vanilla block/state transformation semantics. During an active
 * Skyforge decoration scope, only the projected Y is replaced, and only after the native template
 * anchor resolves to either the independent base world or one exact deterministic Skyforge volume.
 * No global heightmap is used to choose between those domains and no search crosses from one terrain
 * body into another. Missing or ambiguous ownership fails open to the untouched vanilla result.
 */
final class SkyforgeTerrainScopedGravityProcessor extends GravityProcessor {
    static final SkyforgeTerrainScopedGravityProcessor INSTANCE = new SkyforgeTerrainScopedGravityProcessor();

    private SkyforgeTerrainScopedGravityProcessor() {
        super(Heightmap.Types.WORLD_SURFACE_WG, -1);
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos placementOffset,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlaceSettings settings) {
        StructureTemplate.StructureBlockInfo vanilla = super.processBlock(
                level,
                placementOffset,
                pivot,
                originalBlockInfo,
                currentBlockInfo,
                settings);
        if (vanilla == null
                || !SkyforgeTerrainProjectionStage.active()
                || !SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            return vanilla;
        }

        var domain = SkyforgeNeoForge1211SurfaceStage.resolveTerrainDomain(
                placementOffset.getX(),
                placementOffset.getY(),
                placementOffset.getZ());
        if (domain.isEmpty()) {
            return vanilla;
        }

        int worldX = vanilla.pos().getX();
        int worldZ = vanilla.pos().getZ();
        OptionalInt scopedTop = MinecraftTerrainProjectionResolver.resolveTop(
                domain.orElseThrow(),
                () -> SkyforgeTerrainProjectionStage.baseWorldFirstFreeHeight(worldX, worldZ),
                volumeId -> SkyforgeNeoForge1211SurfaceStage.skyforgeFirstFreeHeight(volumeId, worldX, worldZ));
        if (scopedTop.isEmpty()) {
            return vanilla;
        }

        int vanillaTopY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ);
        if (scopedTop.orElseThrow() == vanillaTopY) {
            return vanilla;
        }

        int correctedY = Math.addExact(
                Math.subtractExact(scopedTop.orElseThrow(), 1),
                originalBlockInfo.pos().getY());
        SkyforgeNeoForge1211TerrainProjectionDevRuntime.recordCorrection(
                worldX,
                worldZ,
                placementOffset.getY(),
                vanillaTopY,
                scopedTop.orElseThrow());
        return new StructureTemplate.StructureBlockInfo(
                new BlockPos(worldX, correctedY, worldZ),
                vanilla.state(),
                vanilla.nbt());
    }
}
