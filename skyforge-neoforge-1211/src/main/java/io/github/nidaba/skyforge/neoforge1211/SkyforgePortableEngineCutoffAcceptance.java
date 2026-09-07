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
 * Development-only live proof for the optional Simulated Portable Engine cutoff.
 *
 * <p>The class deliberately references Simulated only through registry IDs/reflection. Production
 * Skyforge therefore remains loadable without the optional flight stack.
 */
final class SkyforgePortableEngineCutoffAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.portableEngineCutoffAcceptance";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgePortableEngineCutoffAcceptance.class.getName());
    private static final ResourceLocation ENGINE_ID =
            id("simulated:red_portable_engine");
    private static final BlockPos ENGINE_POS = new BlockPos(0, 96, 0);
    private static final BlockPos CUTOFF_POWER_POS = ENGINE_POS.above();

    private SkyforgePortableEngineCutoffAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgePortableEngineCutoffAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();

        try {
            level.getChunk(ENGINE_POS.getX() >> 4, ENGINE_POS.getZ() >> 4);

            if (!BuiltInRegistries.BLOCK.containsKey(ENGINE_ID)) {
                fail("Portable Engine block is not registered: " + ENGINE_ID);
            }

            Block engineBlock = BuiltInRegistries.BLOCK.get(ENGINE_ID);
            level.setBlock(ENGINE_POS, engineBlock.defaultBlockState(), 3);

            BlockEntity blockEntity = level.getBlockEntity(ENGINE_POS);
            if (!(blockEntity instanceof SkyforgePortableEngineCutoffAccess cutoff)) {
                fail("Portable Engine BE did not receive Skyforge cutoff mixin: "
                        + (blockEntity == null ? "<null>" : blockEntity.getClass().getName()));
                return;
            }

            Method tick = publicMethod(blockEntity, "tick");
            Method setBurnTime = publicMethod(blockEntity, "setCurrentBurnTime", int.class);
            Method getBurnTime = publicMethod(blockEntity, "getCurrentBurnTime");
            Method getGeneratedSpeed = publicMethod(blockEntity, "getGeneratedSpeed");

            Object inventory = publicField(blockEntity, "inventory").get(blockEntity);
            Method setItem = publicMethod(inventory, "setItem", int.class, ItemStack.class);
            Method getItem = publicMethod(inventory, "getItem", int.class);

            // 1. Backward compatibility: redstone must not affect an engine that has not opted in.
            cutoff.skyforge$setRedstoneCutoffEnabled(false);
            level.setBlock(CUTOFF_POWER_POS, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            setBurnTime.invoke(blockEntity, 100);
            tick.invoke(blockEntity);

            assertInt("default mode burns normally", 99, getBurnTime.invoke(blockEntity));
            assertAbsFloat("default mode generates normal power", 32.0f, getGeneratedSpeed.invoke(blockEntity));

            // 2. Opted-in cutoff freezes an active fuel timer and produces zero output.
            cutoff.skyforge$setRedstoneCutoffEnabled(true);
            setBurnTime.invoke(blockEntity, 100);
            for (int i = 0; i < 5; i++) {
                tick.invoke(blockEntity);
            }

            assertInt("cutoff preserves active burn timer", 100, getBurnTime.invoke(blockEntity));
            assertFloat("cutoff produces zero generator output", 0.0f, getGeneratedSpeed.invoke(blockEntity));

            // 3. An engine cut before ignition must not silently consume queued fuel.
            setBurnTime.invoke(blockEntity, 0);
            ItemStack queuedFuel = new ItemStack(Items.COAL, 2);
            setItem.invoke(inventory, 0, queuedFuel);
            for (int i = 0; i < 3; i++) {
                tick.invoke(blockEntity);
            }

            assertInt("cutoff preserves zero pre-ignition timer", 0, getBurnTime.invoke(blockEntity));
            ItemStack stillQueued = (ItemStack) getItem.invoke(inventory, 0);
            assertInt("cutoff does not consume queued fuel", 2, stillQueued.getCount());

            // 4. Removing the cutoff starts the same engine normally from its queued fuel.
            level.setBlock(CUTOFF_POWER_POS, Blocks.AIR.defaultBlockState(), 3);
            tick.invoke(blockEntity);

            int startedBurn = asInt(getBurnTime.invoke(blockEntity));
            if (startedBurn <= 0) {
                fail("restart did not begin queued fuel burn; burnTime=" + startedBurn);
            }
            ItemStack afterStart = (ItemStack) getItem.invoke(inventory, 0);
            assertInt("restart consumes exactly one queued fuel item", 1, afterStart.getCount());
            assertAbsFloat("restart restores normal generator output", 32.0f, getGeneratedSpeed.invoke(blockEntity));

            // 5. Re-cut a running engine, then resume from the exact preserved timer.
            level.setBlock(CUTOFF_POWER_POS, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            for (int i = 0; i < 5; i++) {
                tick.invoke(blockEntity);
            }
            assertInt("second cutoff preserves exact timer", startedBurn, getBurnTime.invoke(blockEntity));

            level.setBlock(CUTOFF_POWER_POS, Blocks.AIR.defaultBlockState(), 3);
            tick.invoke(blockEntity);
            assertInt("restart resumes countdown", startedBurn - 1, getBurnTime.invoke(blockEntity));

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "PORTABLE_ENGINE_CUTOFF_ACCEPTANCE PASS"
                            + " defaultBurn=99"
                            + " cutoffBurn=100"
                            + " queuedFuelPreserved=2"
                            + " restartBurn=" + startedBurn
                            + " resumedBurn=" + (startedBurn - 1));
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException(
                    "Portable Engine cutoff acceptance reflection failure", cause);
        } finally {
            level.setBlock(CUTOFF_POWER_POS, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ENGINE_POS, Blocks.AIR.defaultBlockState(), 3);
        }
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

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return id;
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "PORTABLE_ENGINE_CUTOFF_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException("Portable Engine cutoff acceptance failed: " + reason);
    }
}
