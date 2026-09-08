package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Three-boot dedicated-server proof that issue #237's Portable Engine cutoff mode survives real
 * world save/reopen in both CUT and configured-RUN states while preserving upstream comparator
 * fuel information.
 */
final class SkyforgePortableEngineCutoffPersistenceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.portableEngineCutoffPersistence";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgePortableEngineCutoffPersistenceAcceptance.class.getName());
    private static final ResourceLocation ENGINE_ID = id("simulated:red_portable_engine");
    private static final BlockPos ENGINE_POS = new BlockPos(0, 96, 0);
    private static final BlockPos CUTOFF_POWER_POS = ENGINE_POS.above();
    private static final int CUT_BURN_TIME = 600;
    private static final int RUN_BURN_TIME = 599;
    private static final int RUN_AFTER_TICK_BURN_TIME = 598;
    private static final int SAVED_FUEL_COUNT = 1;
    private static final int CUT_COMPARATOR = 12;
    private static final int RUN_COMPARATOR = 11;

    private SkyforgePortableEngineCutoffPersistenceAcceptance() {}

    static void installFromSystemProperty() {
        String phase = System.getProperty(ENABLE_PROPERTY, "").trim();
        if (phase.isEmpty()) {
            return;
        }
        if (!phase.equals("prepare")
                && !phase.equals("verify-cut")
                && !phase.equals("verify-run")) {
            throw new IllegalArgumentException(
                    ENABLE_PROPERTY
                            + " must be 'prepare', 'verify-cut', or 'verify-run', got '"
                            + phase
                            + "'");
        }
        NeoForge.EVENT_BUS.addListener(
                SkyforgePortableEngineCutoffPersistenceAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        String phase = System.getProperty(ENABLE_PROPERTY, "").trim();
        ServerLevel level = event.getServer().overworld();
        try {
            level.getChunk(ENGINE_POS.getX() >> 4, ENGINE_POS.getZ() >> 4);
            switch (phase) {
                case "prepare" -> prepareCut(event, level);
                case "verify-cut" -> verifyCutAndSaveRun(event, level);
                case "verify-run" -> verifyRun(level);
                default -> fail("unexpected persistence phase " + phase);
            }
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException(
                    "Portable Engine cutoff persistence acceptance reflection failure",
                    cause);
        }
    }

    private static void prepareCut(ServerStartedEvent event, ServerLevel level)
            throws ReflectiveOperationException {
        Block engineBlock = requireEngineBlock();
        level.setBlock(ENGINE_POS, engineBlock.defaultBlockState(), 3);
        level.setBlock(CUTOFF_POWER_POS, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        BlockEntity blockEntity = requireCutoffBlockEntity(level);
        SkyforgePortableEngineCutoffAccess cutoff =
                (SkyforgePortableEngineCutoffAccess) blockEntity;
        cutoff.skyforge$setRedstoneCutoffEnabled(true);

        Method tick = publicMethod(blockEntity, "tick");
        Method setBurnTime = publicMethod(blockEntity, "setCurrentBurnTime", int.class);
        Method getBurnTime = publicMethod(blockEntity, "getCurrentBurnTime");
        Method getGeneratedSpeed = publicMethod(blockEntity, "getGeneratedSpeed");

        Object inventory = publicField(blockEntity, "inventory").get(blockEntity);
        Method setItem = publicMethod(inventory, "setItem", int.class, ItemStack.class);
        Method getItem = publicMethod(inventory, "getItem", int.class);

        setBurnTime.invoke(blockEntity, CUT_BURN_TIME);
        setItem.invoke(inventory, 0, new ItemStack(Items.COAL, SAVED_FUEL_COUNT));
        tick.invoke(blockEntity);

        assertTrue("prepared cutoff mode enabled", cutoff.skyforge$isRedstoneCutoffEnabled());
        assertTrue("prepared cutoff mode active", cutoff.skyforge$isRedstoneCutoffActive());
        assertInt("prepared burn timer", CUT_BURN_TIME, getBurnTime.invoke(blockEntity));
        assertInt(
                "prepared queued fuel count",
                SAVED_FUEL_COUNT,
                ((ItemStack) getItem.invoke(inventory, 0)).getCount());
        assertFloat("prepared output cut", 0.0f, getGeneratedSpeed.invoke(blockEntity));
        assertInt("prepared comparator", CUT_COMPARATOR, comparatorOutput(engineBlock, level));

        blockEntity.setChanged();
        assertTrue(
                "server reported CUT world save success",
                event.getServer().saveEverything(false, true, true));

        LOGGER.log(
                System.Logger.Level.INFO,
                "PORTABLE_ENGINE_CUTOFF_PERSISTENCE PREPARE PASS"
                        + " burn="
                        + CUT_BURN_TIME
                        + " fuel="
                        + SAVED_FUEL_COUNT
                        + " comparator="
                        + CUT_COMPARATOR
                        + " active=true");
    }

    private static void verifyCutAndSaveRun(ServerStartedEvent event, ServerLevel level)
            throws ReflectiveOperationException {
        Block engineBlock = requireEngineBlock();

        assertTrue(
                "persisted cutoff power block",
                level.getBlockState(CUTOFF_POWER_POS).is(Blocks.REDSTONE_BLOCK));

        BlockEntity blockEntity = requireCutoffBlockEntity(level);
        SkyforgePortableEngineCutoffAccess cutoff =
                (SkyforgePortableEngineCutoffAccess) blockEntity;

        Method tick = publicMethod(blockEntity, "tick");
        Method getBurnTime = publicMethod(blockEntity, "getCurrentBurnTime");
        Method getGeneratedSpeed = publicMethod(blockEntity, "getGeneratedSpeed");

        Object inventory = publicField(blockEntity, "inventory").get(blockEntity);
        Method getItem = publicMethod(inventory, "getItem", int.class);

        assertTrue("reloaded CUT mode enabled", cutoff.skyforge$isRedstoneCutoffEnabled());
        assertTrue("reloaded CUT mode active", cutoff.skyforge$isRedstoneCutoffActive());
        assertInt("reloaded CUT burn timer", CUT_BURN_TIME, getBurnTime.invoke(blockEntity));
        assertInt(
                "reloaded CUT queued fuel count",
                SAVED_FUEL_COUNT,
                ((ItemStack) getItem.invoke(inventory, 0)).getCount());
        assertFloat("reloaded CUT output remains zero", 0.0f, getGeneratedSpeed.invoke(blockEntity));
        assertInt(
                "reloaded CUT comparator",
                CUT_COMPARATOR,
                comparatorOutput(engineBlock, level));

        level.setBlock(CUTOFF_POWER_POS, Blocks.AIR.defaultBlockState(), 3);
        tick.invoke(blockEntity);

        assertTrue("RUN keeps cutoff mode configured", cutoff.skyforge$isRedstoneCutoffEnabled());
        assertFalse("RUN has no active cutoff signal", cutoff.skyforge$isRedstoneCutoffActive());
        assertInt("RUN burn timer", RUN_BURN_TIME, getBurnTime.invoke(blockEntity));
        assertInt(
                "RUN queued fuel count",
                SAVED_FUEL_COUNT,
                ((ItemStack) getItem.invoke(inventory, 0)).getCount());
        assertAbsFloat("RUN generator output", 32.0f, getGeneratedSpeed.invoke(blockEntity));
        assertInt("RUN comparator", RUN_COMPARATOR, comparatorOutput(engineBlock, level));

        blockEntity.setChanged();
        assertTrue(
                "server reported RUN world save success",
                event.getServer().saveEverything(false, true, true));

        LOGGER.log(
                System.Logger.Level.INFO,
                "PORTABLE_ENGINE_CUTOFF_PERSISTENCE CUT VERIFY PASS"
                        + " burn="
                        + CUT_BURN_TIME
                        + "->"
                        + RUN_BURN_TIME
                        + " fuel="
                        + SAVED_FUEL_COUNT
                        + " comparator="
                        + CUT_COMPARATOR
                        + "->"
                        + RUN_COMPARATOR
                        + " persistedMode=true runSaved=true");
    }

    private static void verifyRun(ServerLevel level) throws ReflectiveOperationException {
        Block engineBlock = requireEngineBlock();

        assertTrue(
                "persisted RUN signal absence",
                level.getBlockState(CUTOFF_POWER_POS).isAir());

        BlockEntity blockEntity = requireCutoffBlockEntity(level);
        SkyforgePortableEngineCutoffAccess cutoff =
                (SkyforgePortableEngineCutoffAccess) blockEntity;

        Method tick = publicMethod(blockEntity, "tick");
        Method getBurnTime = publicMethod(blockEntity, "getCurrentBurnTime");
        Method getGeneratedSpeed = publicMethod(blockEntity, "getGeneratedSpeed");

        Object inventory = publicField(blockEntity, "inventory").get(blockEntity);
        Method getItem = publicMethod(inventory, "getItem", int.class);

        assertTrue("reloaded RUN mode remains configured", cutoff.skyforge$isRedstoneCutoffEnabled());
        assertFalse("reloaded RUN mode remains inactive", cutoff.skyforge$isRedstoneCutoffActive());
        assertInt("reloaded RUN burn timer", RUN_BURN_TIME, getBurnTime.invoke(blockEntity));
        assertInt(
                "reloaded RUN queued fuel count",
                SAVED_FUEL_COUNT,
                ((ItemStack) getItem.invoke(inventory, 0)).getCount());
        assertAbsFloat(
                "reloaded RUN generated output",
                32.0f,
                getGeneratedSpeed.invoke(blockEntity));
        assertInt(
                "reloaded RUN comparator",
                RUN_COMPARATOR,
                comparatorOutput(engineBlock, level));

        tick.invoke(blockEntity);

        assertInt(
                "RUN continues decrement after reload",
                RUN_AFTER_TICK_BURN_TIME,
                getBurnTime.invoke(blockEntity));
        assertAbsFloat(
                "RUN continues normal output after reload",
                32.0f,
                getGeneratedSpeed.invoke(blockEntity));
        assertInt(
                "RUN comparator remains coherent after second tick",
                RUN_COMPARATOR,
                comparatorOutput(engineBlock, level));

        LOGGER.log(
                System.Logger.Level.INFO,
                "PORTABLE_ENGINE_CUTOFF_PERSISTENCE RUN VERIFY PASS"
                        + " burn="
                        + RUN_BURN_TIME
                        + "->"
                        + RUN_AFTER_TICK_BURN_TIME
                        + " fuel="
                        + SAVED_FUEL_COUNT
                        + " comparator="
                        + RUN_COMPARATOR
                        + " persistedMode=true active=false");
    }

    private static Block requireEngineBlock() {
        if (!BuiltInRegistries.BLOCK.containsKey(ENGINE_ID)) {
            fail("Portable Engine block is not registered: " + ENGINE_ID);
        }
        return BuiltInRegistries.BLOCK.get(ENGINE_ID);
    }

    private static BlockEntity requireCutoffBlockEntity(ServerLevel level) {
        BlockEntity blockEntity = level.getBlockEntity(ENGINE_POS);
        if (!(blockEntity instanceof SkyforgePortableEngineCutoffAccess)) {
            fail("Portable Engine BE did not receive Skyforge cutoff mixin: "
                    + (blockEntity == null ? "<null>" : blockEntity.getClass().getName()));
        }
        return blockEntity;
    }

    private static int comparatorOutput(Block engineBlock, ServerLevel level)
            throws ReflectiveOperationException {
        Method comparator = publicMethod(
                engineBlock,
                "getAnalogOutputSignal",
                net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.world.level.Level.class,
                BlockPos.class);
        return asInt(comparator.invoke(
                engineBlock,
                level.getBlockState(ENGINE_POS),
                level,
                ENGINE_POS));
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Field publicField(Object target, String name) throws NoSuchFieldException {
        return target.getClass().getField(name);
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("expected numeric value, got " + value);
    }

    private static float asFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        throw new IllegalStateException("expected numeric value, got " + value);
    }

    private static void assertInt(String label, int expected, Object actual) {
        int value = asInt(actual);
        if (value != expected) {
            fail(label + ": expected=" + expected + " actual=" + value);
        }
    }

    private static void assertFloat(String label, float expected, Object actual) {
        float value = asFloat(actual);
        if (Math.abs(value - expected) > 0.0001f) {
            fail(label + ": expected=" + expected + " actual=" + value);
        }
    }

    private static void assertAbsFloat(String label, float expectedMagnitude, Object actual) {
        float value = Math.abs(asFloat(actual));
        if (Math.abs(value - expectedMagnitude) > 0.0001f) {
            fail(label + ": expectedMagnitude=" + expectedMagnitude + " actual=" + value);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void assertFalse(String label, boolean value) {
        if (value) {
            fail(label + ": expected false");
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return resourceLocation;
    }

    private static void fail(String reason) {
        LOGGER.log(
                System.Logger.Level.ERROR,
                "PORTABLE_ENGINE_CUTOFF_PERSISTENCE FAIL " + reason);
        throw new IllegalStateException(
                "Portable Engine cutoff persistence acceptance failed: " + reason);
    }
}
