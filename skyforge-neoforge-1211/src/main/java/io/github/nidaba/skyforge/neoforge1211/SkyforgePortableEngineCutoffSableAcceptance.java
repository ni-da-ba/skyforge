package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Headless issue #237 proof on a real Simulated-assembled Sable sublevel.
 *
 * <p>External flight-stack types are referenced only by registry id/reflection so packaged
 * Skyforge remains loadable when that optional stack is absent.
 */
final class SkyforgePortableEngineCutoffSableAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.portableEngineCutoffSableAcceptance";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgePortableEngineCutoffSableAcceptance.class.getName());

    private static final ResourceLocation ENGINE_ID = id("simulated:red_portable_engine");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");

    private static final BlockPos ENGINE_A = new BlockPos(0, 96, 0);
    private static final BlockPos SHAFT = new BlockPos(1, 96, 0);
    private static final BlockPos ENGINE_B = new BlockPos(2, 96, 0);

    private static final BlockPos SLIME_A = ENGINE_A.below();
    private static final BlockPos SLIME_CENTER = SHAFT.below();
    private static final BlockPos SLIME_B = ENGINE_B.below();

    private static final BlockPos POWER_A = ENGINE_A.south();
    private static final BlockPos POWER_B = ENGINE_B.south();

    private static final int START_BURN_A = 1000;
    private static final int START_BURN_B = 1200;

    private SkyforgePortableEngineCutoffSableAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(
                SkyforgePortableEngineCutoffSableAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();

        try {
            level.getChunk(0, 0);

            Block engineBlock = requireBlock(ENGINE_ID);
            Block shaftBlock = requireBlock(SHAFT_ID);

            BlockState engineAState = engineBlock.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
            BlockState engineBState = engineBlock.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
            BlockState shaftState = shaftBlock.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            BlockState slimeState = Blocks.SLIME_BLOCK.defaultBlockState();

            assertTrue(
                    "slime floor can retain engine A through ordinary NeoForge stickiness",
                    slimeState.canStickTo(engineAState) && engineAState.canStickTo(slimeState));
            assertTrue(
                    "slime floor can retain shared shaft through ordinary NeoForge stickiness",
                    slimeState.canStickTo(shaftState) && shaftState.canStickTo(slimeState));
            assertTrue(
                    "slime floor can retain engine B through ordinary NeoForge stickiness",
                    slimeState.canStickTo(engineBState) && engineBState.canStickTo(slimeState));

            level.setBlock(SLIME_A, slimeState, 3);
            level.setBlock(SLIME_CENTER, slimeState, 3);
            level.setBlock(SLIME_B, slimeState, 3);
            level.setBlock(ENGINE_A, engineAState, 3);
            level.setBlock(SHAFT, shaftState, 3);
            level.setBlock(ENGINE_B, engineBState, 3);
            level.setBlock(POWER_A, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            level.setBlock(POWER_B, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

            BlockEntity sourceA = requireCutoffEngine(level, ENGINE_A);
            BlockEntity sourceB = requireCutoffEngine(level, ENGINE_B);
            SkyforgePortableEngineCutoffAccess sourceCutoffA =
                    (SkyforgePortableEngineCutoffAccess) sourceA;
            SkyforgePortableEngineCutoffAccess sourceCutoffB =
                    (SkyforgePortableEngineCutoffAccess) sourceB;
            sourceCutoffA.skyforge$setRedstoneCutoffEnabled(true);
            sourceCutoffB.skyforge$setRedstoneCutoffEnabled(true);

            Method setBurnA = publicMethod(sourceA, "setCurrentBurnTime", int.class);
            Method setBurnB = publicMethod(sourceB, "setCurrentBurnTime", int.class);
            setBurnA.invoke(sourceA, START_BURN_A);
            setBurnB.invoke(sourceB, START_BURN_B);

            Object assembly = assembleIntoRealSableSubLevel(level);
            Object subLevel = publicMethod(assembly, "subLevel").invoke(assembly);
            BlockPos offset = (BlockPos) publicMethod(assembly, "offset").invoke(assembly);
            assertNotNull("Simulated returned a Sable sublevel", subLevel);
            assertTrue(
                    "assembly returned a server Sable sublevel",
                    subLevel.getClass().getName().endsWith("ServerSubLevel"));

            BlockPos movedA = ENGINE_A.offset(offset);
            BlockPos movedShaft = SHAFT.offset(offset);
            BlockPos movedB = ENGINE_B.offset(offset);
            BlockPos movedPowerA = POWER_A.offset(offset);
            BlockPos movedPowerB = POWER_B.offset(offset);

            assertTrue("source engine A moved out of world position", level.getBlockState(ENGINE_A).isAir());
            assertTrue("source engine B moved out of world position", level.getBlockState(ENGINE_B).isAir());
            assertTrue("relocated engine A retained identity", level.getBlockState(movedA).is(engineBlock));
            assertTrue("relocated shaft retained identity", level.getBlockState(movedShaft).is(shaftBlock));
            assertTrue("relocated engine B retained identity", level.getBlockState(movedB).is(engineBlock));
            assertTrue(
                    "relocated power A retained identity",
                    level.getBlockState(movedPowerA).is(Blocks.REDSTONE_BLOCK));
            assertTrue(
                    "relocated power B retained identity",
                    level.getBlockState(movedPowerB).is(Blocks.REDSTONE_BLOCK));

            assertSameSubLevel(level, subLevel, movedA, "engine A");
            assertSameSubLevel(level, subLevel, movedShaft, "shared shaft");
            assertSameSubLevel(level, subLevel, movedB, "engine B");
            assertSameSubLevel(level, subLevel, movedPowerA, "power A");
            assertSameSubLevel(level, subLevel, movedPowerB, "power B");

            BlockEntity engineA = requireCutoffEngine(level, movedA);
            BlockEntity engineB = requireCutoffEngine(level, movedB);
            SkyforgePortableEngineCutoffAccess cutoffA =
                    (SkyforgePortableEngineCutoffAccess) engineA;
            SkyforgePortableEngineCutoffAccess cutoffB =
                    (SkyforgePortableEngineCutoffAccess) engineB;

            Method tickA = publicMethod(engineA, "tick");
            Method tickB = publicMethod(engineB, "tick");
            Method getBurnA = publicMethod(engineA, "getCurrentBurnTime");
            Method getBurnB = publicMethod(engineB, "getCurrentBurnTime");
            Method getGeneratedSpeedA = publicMethod(engineA, "getGeneratedSpeed");
            Method getGeneratedSpeedB = publicMethod(engineB, "getGeneratedSpeed");
            Method isVirtualA = publicMethod(engineA, "isVirtual");
            Method isVirtualB = publicMethod(engineB, "isVirtual");

            assertTrue("engine A cutoff mode survived assembly", cutoffA.skyforge$isRedstoneCutoffEnabled());
            assertTrue("engine B cutoff mode survived assembly", cutoffB.skyforge$isRedstoneCutoffEnabled());
            assertInt("engine A burn survived assembly", START_BURN_A, getBurnA.invoke(engineA));
            assertInt("engine B burn survived assembly", START_BURN_B, getBurnB.invoke(engineB));
            assertTrue("engine A sees relocated CUT signal", cutoffA.skyforge$isRedstoneCutoffActive());
            assertTrue("engine B sees relocated CUT signal", cutoffB.skyforge$isRedstoneCutoffActive());

            boolean virtualA = (Boolean) isVirtualA.invoke(engineA);
            boolean virtualB = (Boolean) isVirtualB.invoke(engineB);

            // Initial together CUT on the actual relocated block entities.
            for (int i = 0; i < 3; i++) {
                tickA.invoke(engineA);
                tickB.invoke(engineB);
            }
            assertInt("together CUT preserves engine A", START_BURN_A, getBurnA.invoke(engineA));
            assertInt("together CUT preserves engine B", START_BURN_B, getBurnB.invoke(engineB));
            assertFloat("together CUT zeroes engine A output", 0.0f, getGeneratedSpeedA.invoke(engineA));
            assertFloat("together CUT zeroes engine B output", 0.0f, getGeneratedSpeedB.invoke(engineB));

            // Together RUN: both counters must resume from the exact preserved values.
            level.setBlock(movedPowerA, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(movedPowerB, Blocks.AIR.defaultBlockState(), 3);
            tickA.invoke(engineA);
            tickB.invoke(engineB);
            int runA = START_BURN_A - 1;
            int runB = START_BURN_B - 1;
            assertInt("together RUN decrements engine A", runA, getBurnA.invoke(engineA));
            assertInt("together RUN decrements engine B", runB, getBurnB.invoke(engineB));
            assertAbsFloat("together RUN restores engine A 32 RPM", 32.0f, getGeneratedSpeedA.invoke(engineA));
            assertAbsFloat("together RUN restores engine B 32 RPM", 32.0f, getGeneratedSpeedB.invoke(engineB));

            // Independent A CUT while B continues.
            level.setBlock(movedPowerA, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            tickA.invoke(engineA);
            tickB.invoke(engineB);
            assertInt("independent A CUT freezes A", runA, getBurnA.invoke(engineA));
            assertInt("independent A CUT leaves B running", runB - 1, getBurnB.invoke(engineB));
            assertFloat("independent A CUT zeroes A output", 0.0f, getGeneratedSpeedA.invoke(engineA));
            assertAbsFloat("independent A CUT keeps B output", 32.0f, getGeneratedSpeedB.invoke(engineB));

            // Restart A without disturbing B's preserved current state.
            level.setBlock(movedPowerA, Blocks.AIR.defaultBlockState(), 3);
            tickA.invoke(engineA);
            int afterARestart = runA - 1;
            int bBeforeIndependentB = runB - 1;
            assertInt("independent A restart resumes exact counter", afterARestart, getBurnA.invoke(engineA));
            assertInt("A restart does not mutate unticked B", bBeforeIndependentB, getBurnB.invoke(engineB));
            assertAbsFloat("independent A restart restores output", 32.0f, getGeneratedSpeedA.invoke(engineA));

            // Independent B CUT while A continues.
            level.setBlock(movedPowerB, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            tickA.invoke(engineA);
            tickB.invoke(engineB);
            int aBeforeIndependentBRestart = afterARestart - 1;
            assertInt("independent B CUT leaves A running", aBeforeIndependentBRestart, getBurnA.invoke(engineA));
            assertInt("independent B CUT freezes B", bBeforeIndependentB, getBurnB.invoke(engineB));
            assertAbsFloat("independent B CUT keeps A output", 32.0f, getGeneratedSpeedA.invoke(engineA));
            assertFloat("independent B CUT zeroes B output", 0.0f, getGeneratedSpeedB.invoke(engineB));

            // Restart B without disturbing A's preserved current state.
            level.setBlock(movedPowerB, Blocks.AIR.defaultBlockState(), 3);
            tickB.invoke(engineB);
            int afterBRestart = bBeforeIndependentB - 1;
            assertInt("B restart does not mutate unticked A", aBeforeIndependentBRestart, getBurnA.invoke(engineA));
            assertInt("independent B restart resumes exact counter", afterBRestart, getBurnB.invoke(engineB));
            assertAbsFloat("independent B restart restores output", 32.0f, getGeneratedSpeedB.invoke(engineB));

            // Repeat a final together CUT/restart to prove stable two-engine toggling.
            level.setBlock(movedPowerA, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            level.setBlock(movedPowerB, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            for (int i = 0; i < 3; i++) {
                tickA.invoke(engineA);
                tickB.invoke(engineB);
            }
            assertInt("repeat together CUT preserves A", aBeforeIndependentBRestart, getBurnA.invoke(engineA));
            assertInt("repeat together CUT preserves B", afterBRestart, getBurnB.invoke(engineB));
            assertFloat("repeat together CUT zeroes A", 0.0f, getGeneratedSpeedA.invoke(engineA));
            assertFloat("repeat together CUT zeroes B", 0.0f, getGeneratedSpeedB.invoke(engineB));

            level.setBlock(movedPowerA, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(movedPowerB, Blocks.AIR.defaultBlockState(), 3);
            tickA.invoke(engineA);
            tickB.invoke(engineB);
            int finalA = aBeforeIndependentBRestart - 1;
            int finalB = afterBRestart - 1;
            assertInt("final together restart resumes A", finalA, getBurnA.invoke(engineA));
            assertInt("final together restart resumes B", finalB, getBurnB.invoke(engineB));
            assertAbsFloat("final together restart restores A", 32.0f, getGeneratedSpeedA.invoke(engineA));
            assertAbsFloat("final together restart restores B", 32.0f, getGeneratedSpeedB.invoke(engineB));

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "PORTABLE_ENGINE_CUTOFF_SABLE_ACCEPTANCE PASS"
                            + " subLevel="
                            + subLevel.getClass().getSimpleName()
                            + " virtualA="
                            + virtualA
                            + " virtualB="
                            + virtualB
                            + " offset="
                            + offset
                            + " startA="
                            + START_BURN_A
                            + " startB="
                            + START_BURN_B
                            + " finalA="
                            + finalA
                            + " finalB="
                            + finalB
                            + " sharedShaft=true"
                            + " independentA=true"
                            + " independentB=true"
                            + " together=true");
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException(
                    "Portable Engine Sable acceptance reflection failure",
                    cause);
        }
    }

    private static Object assembleIntoRealSableSubLevel(ServerLevel level)
            throws ReflectiveOperationException {
        Class<?> helperClass =
                Class.forName("dev.simulated_team.simulated.util.SimAssemblyHelper");
        Method assemble = helperClass.getMethod(
                "assembleFromSingleBlock",
                Level.class,
                BlockPos.class,
                BlockPos.class,
                boolean.class,
                boolean.class);
        Object assembly = assemble.invoke(
                null,
                level,
                SLIME_CENTER,
                SLIME_CENTER,
                true,
                true);
        assertNotNull("Simulated assembly result", assembly);
        return assembly;
    }

    private static void assertSameSubLevel(
            ServerLevel level,
            Object expected,
            BlockPos pos,
            String label)
            throws ReflectiveOperationException {
        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
        Object helper = sableClass.getField("HELPER").get(null);
        Method getContaining =
                helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);
        Object actual = getContaining.invoke(helper, level, pos);
        if (actual != expected) {
            fail(label
                    + " not in returned Sable sublevel: expected="
                    + expected
                    + " actual="
                    + actual
                    + " pos="
                    + pos);
        }
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            fail("required block is not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockEntity requireCutoffEngine(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SkyforgePortableEngineCutoffAccess)) {
            fail("Portable Engine BE missing cutoff mixin at "
                    + pos
                    + ": "
                    + (blockEntity == null ? "<null>" : blockEntity.getClass().getName()));
        }
        return blockEntity;
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
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

    private static void assertNotNull(String label, Object value) {
        if (value == null) {
            fail(label + ": expected non-null");
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
                "PORTABLE_ENGINE_CUTOFF_SABLE_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException(
                "Portable Engine Sable acceptance failed: " + reason);
    }
}
