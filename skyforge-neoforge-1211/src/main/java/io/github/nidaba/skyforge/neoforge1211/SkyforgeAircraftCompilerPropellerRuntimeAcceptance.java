package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Exact-stack AIRCRAFT-001 nested Propeller Bearing acceptance. This is deliberately separate
 * from primary Sable transfer: the nine hub/sail cells first enter the Sable sublevel, then the
 * moved Aeronautics bearing must re-form them as its controlled Create child contraption.
 */
final class SkyforgeAircraftCompilerPropellerRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPropellerRuntimeAcceptance.class.getName());
    private static final int EXPECTED_CHILD_BLOCKS = 9;
    private static final float EXPECTED_SAIL_POWER = 8.0f;

    private SkyforgeAircraftCompilerPropellerRuntimeAcceptance() {}

    static void verify(ServerLevel parentLevel, BlockPos movedBearingPos, List<BlockPos> movedPayloadPositions)
            throws ReflectiveOperationException {
        assertInt("propeller payload coordinate count", EXPECTED_CHILD_BLOCKS, movedPayloadPositions.size());

        BlockEntity bearing = parentLevel.getBlockEntity(movedBearingPos);
        if (bearing == null || !bearing.getClass().getName().endsWith("PropellerBearingBlockEntity")) {
            fail("moved Propeller Bearing block entity missing or wrong type at " + movedBearingPos
                    + " actual=" + (bearing == null ? "null" : bearing.getClass().getName()));
        }
        ResourceLocation bearingId = BuiltInRegistries.BLOCK.getKey(parentLevel.getBlockState(movedBearingPos).getBlock());
        assertEquals("moved propeller bearing resource", "aeronautics:propeller_bearing", bearingId.toString());

        Object preChild = publicMethod(bearing, "getMovedContraption").invoke(bearing);
        assertTrue("propeller child absent before bearing assembly", preChild == null);

        publicMethod(bearing, "assemble").invoke(bearing);

        Object lastException = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
        assertTrue("propeller bearing assembly exception absent", lastException == null);
        boolean running = (Boolean) publicMethod(bearing, "isRunning").invoke(bearing);
        assertTrue("propeller bearing running after assembly", running);

        Object child = publicMethod(bearing, "getMovedContraption").invoke(bearing);
        assertTrue("propeller controlled contraption created", child != null);
        assertTrue("propeller controlled contraption is an Entity", child instanceof Entity);
        assertTrue("propeller controlled contraption alive", ((Entity) child).isAlive());

        Object contraption = publicMethod(child, "getContraption").invoke(child);
        assertTrue("propeller child contraption object exists", contraption != null);
        Object rawBlocks = publicMethod(contraption, "getBlocks").invoke(contraption);
        if (!(rawBlocks instanceof Map<?, ?> blocks)) {
            fail("propeller contraption getBlocks() did not return a Map: " + rawBlocks);
        }
        assertInt("exact propeller child block count", EXPECTED_CHILD_BLOCKS, blocks.size());

        Field sailPowerField = bearing.getClass().getField("totalSailPower");
        float sailPower = sailPowerField.getFloat(bearing);
        assertNear("realized propeller sail power", EXPECTED_SAIL_POWER, sailPower, 0.0001f);

        int removedPayload = 0;
        for (BlockPos movedPayload : movedPayloadPositions) {
            assertTrue("propeller payload removed from primary Sable lattice " + movedPayload,
                    parentLevel.getBlockState(movedPayload).isAir());
            removedPayload++;
        }
        assertInt("all propeller payload cells consumed by child contraption", EXPECTED_CHILD_BLOCKS, removedPayload);

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_PROPELLER PASS"
                        + " childBlocks=" + blocks.size()
                        + " sailPower=" + sailPower
                        + " running=" + running
                        + " childAlive=" + ((Entity) child).isAlive()
                        + " assemblyException=false"
                        + " payloadRemoved=" + removedPayload
                        + " rpmVerified=false"
                        + " thrustVerified=false");
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(String label, float expected, float actual, float tolerance) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            fail(label + ": expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_PROPELLER FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 propeller runtime acceptance failed: " + reason);
    }
}
