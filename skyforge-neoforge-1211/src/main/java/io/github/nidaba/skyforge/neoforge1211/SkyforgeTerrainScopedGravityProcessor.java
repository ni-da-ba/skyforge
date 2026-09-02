package io.github.nidaba.skyforge.neoforge1211;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Vanilla terrain-matching gravity with one conservative Skyforge ownership correction.
 *
 * <p>The superclass always runs first. Outside an active Skyforge decoration scope its result is
 * returned unchanged. During Skyforge structure placement, a projected top is replaced only when
 * the generic vertical resolver proves that the selected top belongs to an unrelated Skyforge
 * volume above the template's own placement anchor and finds a lower valid heightmap surface.
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

        Heightmap.Types heightmap = level instanceof ServerLevel
                ? Heightmap.Types.WORLD_SURFACE
                : Heightmap.Types.WORLD_SURFACE_WG;
        int worldX = vanilla.pos().getX();
        int worldZ = vanilla.pos().getZ();
        int vanillaTopY = level.getHeight(heightmap, worldX, worldZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        OptionalInt scopedTop = MinecraftTerrainProjectionResolver.resolveTop(
                placementOffset.getY(),
                vanillaTopY,
                level.getMinBuildHeight(),
                worldY -> {
                    cursor.set(worldX, worldY, worldZ);
                    return heightmap.isOpaque().test(level.getBlockState(cursor));
                },
                worldY -> SkyforgeNeoForge1211SurfaceStage.claimingVolumeIds(worldX, worldY, worldZ),
                volumeId -> SkyforgeNeoForge1211SurfaceStage.undersideSurfaceHeight(volumeId, worldX, worldZ));
        if (scopedTop.isEmpty() || scopedTop.orElseThrow() == vanillaTopY) {
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
