package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.Codec;
import java.util.Arrays;

/** Minecraft-owned feature-placement suitability classes for supplemental surfaces. */
enum MinecraftSurfaceSuitability {
    DRY_LAND("dry_land"),
    DRY_OPEN("dry_open"),
    SUBMERGED_WATER_FLOOR("submerged_water_floor"),
    OPEN_WATER_FLOOR("open_water_floor");

    static final Codec<MinecraftSurfaceSuitability> CODEC =
            Codec.STRING.xmap(MinecraftSurfaceSuitability::fromSerializedName, MinecraftSurfaceSuitability::serializedName);

    private final String serializedName;

    MinecraftSurfaceSuitability(String serializedName) {
        this.serializedName = serializedName;
    }

    String serializedName() {
        return serializedName;
    }

    private static MinecraftSurfaceSuitability fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown Skyforge Minecraft surface suitability: " + serializedName));
    }
}
