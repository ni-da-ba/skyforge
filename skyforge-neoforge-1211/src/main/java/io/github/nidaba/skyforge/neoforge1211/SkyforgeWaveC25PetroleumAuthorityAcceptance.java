package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * C25 black-box proof that Create: Diesel Generators' independent chunk-oil geography can be
 * disabled while its retained petroleum machinery and fluid assets remain loaded.
 *
 * <p>The fixture has no compile-time dependency on Diesel Generators. Reflection is limited to the
 * public runtime config/query surface needed to prove the retained-mod authority boundary.
 */
final class SkyforgeWaveC25PetroleumAuthorityAcceptance {
    static final String MODE_PROPERTY = "skyforge.dev.waveC25PetroleumAuthority";
    private static final String DIESEL_CONFIG =
            "com.jesz.createdieselgenerators.CDGConfig";
    private static final String OIL_DATA =
            "com.jesz.createdieselgenerators.world.OilChunksSavedData";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC25PetroleumAuthorityAcceptance.class.getName());

    private SkyforgeWaveC25PetroleumAuthorityAcceptance() {}

    static void installFromSystemProperty() {
        String raw = System.getProperty(MODE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Mode mode = Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> onServerStarted(event, mode));
    }

    private static void onServerStarted(ServerStartedEvent event, Mode mode) {
        requireLoaded("create");
        requireLoaded("createdieselgenerators");
        if (ModList.get().isLoaded("kubejs")) {
            fail("isolated C25 runtime unexpectedly loaded KubeJS and could bypass native chunk-oil config");
        }

        requireBlock("createdieselgenerators", "pumpjack_hole");
        requireBlock("createdieselgenerators", "distillation_tank");
        requireBlock("createdieselgenerators", "diesel_engine");
        requireFluid("createdieselgenerators", "crude_oil");

        boolean normalDisabled = configBoolean("DISABLE_NORMAL_OIL_CHUNKS");
        boolean highDisabled = configBoolean("DISABLE_HIGH_OIL_CHUNKS");

        switch (mode) {
            case BASELINE -> {
                if (normalDisabled || highDisabled) {
                    fail("baseline expected native oil classes enabled but observed normalDisabled="
                            + normalDisabled + " highDisabled=" + highDisabled);
                }
            }
            case SUPPRESSED -> {
                if (!normalDisabled || !highDisabled) {
                    fail("suppressed mode expected both native oil classes disabled but observed normalDisabled="
                            + normalDisabled + " highDisabled=" + highDisabled);
                }
                ServerLevel level = event.getServer().overworld();
                for (ChunkPos sample : new ChunkPos[] {
                        new ChunkPos(0, 0),
                        new ChunkPos(7, -11),
                        new ChunkPos(-13, 5)
                }) {
                    int amount = baseOilAmount(level, sample);
                    if (amount != 0) {
                        fail("suppressed native chunk-oil query returned " + amount + " at " + sample);
                    }
                }
            }
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C25 PASS mode="
                        + mode
                        + " normalOilDisabled="
                        + normalDisabled
                        + " highOilDisabled="
                        + highDisabled
                        + " crudeOil=true"
                        + " pumpjackHole=true"
                        + " distillationTank=true"
                        + " dieselEngine=true"
                        + " kubejs=false"
                        + " productionAdapterSelected=false");
    }

    private static boolean configBoolean(String fieldName) {
        try {
            Class<?> type = Class.forName(DIESEL_CONFIG);
            Field field = type.getField(fieldName);
            Object configValue = field.get(null);
            Method get = configValue.getClass().getMethod("get");
            Object value = get.invoke(configValue);
            if (!(value instanceof Boolean bool)) {
                throw new IllegalStateException(
                        "unexpected Diesel Generators config value for " + fieldName + ": " + value);
            }
            return bool;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "could not inspect Diesel Generators config field " + fieldName, exception);
        }
    }

    private static int baseOilAmount(ServerLevel level, ChunkPos chunkPos) {
        try {
            Class<?> type = Class.forName(OIL_DATA);
            Method method = type.getMethod("getBaseOilAmount", ServerLevel.class, ChunkPos.class);
            Object result = method.invoke(null, level, chunkPos);
            if (!(result instanceof Integer amount)) {
                throw new IllegalStateException(
                        "unexpected Diesel Generators base-oil result at " + chunkPos + ": " + result);
            }
            return amount;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "could not query Diesel Generators base oil at " + chunkPos, exception);
        }
    }

    private static Block requireBlock(String namespace, String path) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Block block = BuiltInRegistries.BLOCK.get(key);
        if (block == Blocks.AIR) {
            throw new IllegalStateException("missing retained block asset " + key);
        }
        return block;
    }

    private static Fluid requireFluid(String namespace, String path) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Fluid fluid = BuiltInRegistries.FLUID.get(key);
        if (fluid == Fluids.EMPTY) {
            throw new IllegalStateException("missing retained fluid asset " + key);
        }
        return fluid;
    }

    private static void requireLoaded(String modId) {
        if (!ModList.get().isLoaded(Objects.requireNonNull(modId, "modId"))) {
            throw new IllegalStateException("required C25 mod not loaded: " + modId);
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C25 FAIL " + reason);
        throw new IllegalStateException("Wave C25 petroleum-authority acceptance failed: " + reason);
    }

    private enum Mode {
        BASELINE,
        SUPPRESSED
    }
}
