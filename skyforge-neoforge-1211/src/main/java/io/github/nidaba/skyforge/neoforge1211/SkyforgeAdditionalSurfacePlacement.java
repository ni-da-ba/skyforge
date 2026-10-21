package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Placement modifier that replaces one heightmap answer with Skyforge's additional surfaces. */
public final class SkyforgeAdditionalSurfacePlacement extends PlacementModifier {
    static final SkyforgeAdditionalSurfacePlacement INSTANCE = new SkyforgeAdditionalSurfacePlacement();
    public static final MapCodec<SkyforgeAdditionalSurfacePlacement> CODEC = MapCodec.unit(INSTANCE);

    private SkyforgeAdditionalSurfacePlacement() {}

    @Override
    public Stream<BlockPos> getPositions(
            PlacementContext context,
            RandomSource random,
            BlockPos origin) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(origin, "origin");
        return SkyforgeNeoForge1211FeatureStage.additionalPositions(origin.getX(), origin.getZ()).stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkyforgeNeoForge1211PlacementModifiers.ADDITIONAL_SURFACES.value();
    }
}
