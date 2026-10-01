package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Placement modifier that filters supplemental surfaces by Minecraft-owned suitability. */
public final class SkyforgeSuitableSurfacePlacement extends PlacementModifier {
    public static final MapCodec<SkyforgeSuitableSurfacePlacement> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            MinecraftSurfaceSuitability.CODEC
                                    .fieldOf("suitability")
                                    .forGetter(SkyforgeSuitableSurfacePlacement::suitability))
                    .apply(instance, SkyforgeSuitableSurfacePlacement::new));

    private final MinecraftSurfaceSuitability suitability;

    SkyforgeSuitableSurfacePlacement(MinecraftSurfaceSuitability suitability) {
        this.suitability = Objects.requireNonNull(suitability, "suitability");
    }

    MinecraftSurfaceSuitability suitability() {
        return suitability;
    }

    @Override
    public Stream<BlockPos> getPositions(
            PlacementContext context,
            RandomSource random,
            BlockPos origin) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(origin, "origin");
        return SkyforgeNeoForge1211FeatureStage
                .suitablePositions(origin.getX(), origin.getZ(), suitability)
                .stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkyforgeNeoForge1211PlacementModifiers.SUITABLE_SURFACES.value();
    }
}
