package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** AIRCRAFT-001 v0.13 exact-stack Swivel-Bearing rudder child-capture acceptance. */
final class SkyforgeAircraftCompilerYawRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerYawRuntimeAcceptance.class.getName());

    private static final ResourceLocation SWIVEL_BEARING_ID = id("simulated:swivel_bearing");
    private static final int EXPECTED_RUDDER_PAYLOAD = 4;
    private static final int EXPECTED_RETAINED_PARENT_MAIN = 117;
    private static final double NEUTRAL_ANGLE_TOLERANCE_DEGREES = 1.0e-9;

    private SkyforgeAircraftCompilerYawRuntimeAcceptance() {}

    static void verify(
            ServerLevel parentLevel,
            BlockPos movedSwivelBearingPos,
            List<BlockPos> movedRudderPayloadPositions,
            List<BlockPos> movedRetainedParentMainPositions)
            throws ReflectiveOperationException {
        assertInt("declared rudder payload count", EXPECTED_RUDDER_PAYLOAD, movedRudderPayloadPositions.size());
        assertInt("declared retained parent-main count", EXPECTED_RETAINED_PARENT_MAIN, movedRetainedParentMainPositions.size());

        Set<BlockPos> rudder = new LinkedHashSet<>(movedRudderPayloadPositions);
        Set<BlockPos> retained = new LinkedHashSet<>(movedRetainedParentMainPositions);
        assertInt("rudder payload coordinates unique", EXPECTED_RUDDER_PAYLOAD, rudder.size());
        assertInt("retained parent-main coordinates unique", EXPECTED_RETAINED_PARENT_MAIN, retained.size());
        assertTrue("rudder payload disjoint from retained parent", java.util.Collections.disjoint(rudder, retained));
        assertTrue("Swivel Bearing belongs to retained parent", retained.contains(movedSwivelBearingPos));

        BlockEntity bearing = parentLevel.getBlockEntity(movedSwivelBearingPos);
        if (bearing == null || !bearing.getClass().getName().endsWith("SwivelBearingBlockEntity")) {
            fail("missing moved SwivelBearingBlockEntity at " + movedSwivelBearingPos);
        }
        ResourceLocation bearingId = BuiltInRegistries.BLOCK.getKey(parentLevel.getBlockState(movedSwivelBearingPos).getBlock());
        assertEquals("moved Swivel Bearing resource", SWIVEL_BEARING_ID, bearingId);
        BlockState bearingState = parentLevel.getBlockState(movedSwivelBearingPos);
        assertTrue("Swivel Bearing has FACING property", bearingState.hasProperty(BlockStateProperties.FACING));
        assertTrue("Swivel Bearing faces UP for yaw-axis rotation",
                bearingState.getValue(BlockStateProperties.FACING) == Direction.UP);

        boolean initiallyAssembled = (Boolean) publicMethod(bearing, "isAssembled").invoke(bearing);
        assertTrue("Swivel Bearing begins unassembled", !initiallyAssembled);
        double initialTargetAngle = doubleNumber(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertFinite("initial target angle", initialTargetAngle);
        assertNearZero("initial target angle", initialTargetAngle, NEUTRAL_ANGLE_TOLERANCE_DEGREES);
        Object initialException = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
        assertTrue("no pre-existing Swivel assembly exception", initialException == null);

        Map<BlockPos, BlockState> retainedBefore = new LinkedHashMap<>();
        for (BlockPos pos : retained) {
            if (pos.equals(movedSwivelBearingPos)) {
                continue;
            }
            BlockState state = parentLevel.getBlockState(pos);
            assertTrue("retained parent cell exists before rudder assembly " + pos, !state.isAir());
            retainedBefore.put(pos, state);
        }
        for (BlockPos pos : rudder) {
            assertTrue("rudder payload exists in primary Sable body before child assembly " + pos,
                    !parentLevel.getBlockState(pos).isAir());
        }

        publicMethod(bearing, "assemble").invoke(bearing);

        assertTrue("Swivel Bearing reports assembled child",
                (Boolean) publicMethod(bearing, "isAssembled").invoke(bearing));
        Object assemblyException = publicMethod(bearing, "getLastAssemblyException").invoke(bearing);
        assertTrue("Swivel child assembly exception remains clear", assemblyException == null);

        double neutralTargetAngle = doubleNumber(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertFinite("post-assembly target angle", neutralTargetAngle);
        assertNearZero("post-assembly neutral target angle", neutralTargetAngle, NEUTRAL_ANGLE_TOLERANCE_DEGREES);

        int consumed = 0;
        for (BlockPos pos : rudder) {
            assertTrue("rudder payload consumed from parent Sable lattice " + pos,
                    parentLevel.getBlockState(pos).isAir());
            consumed++;
        }
        assertInt("exact rudder payload cells consumed", EXPECTED_RUDDER_PAYLOAD, consumed);

        int retainedCount = 1; // Swivel Bearing itself, checked separately because ASSEMBLED changes its state.
        BlockState bearingAfter = parentLevel.getBlockState(movedSwivelBearingPos);
        assertEquals("Swivel Bearing remains on parent after child assembly",
                SWIVEL_BEARING_ID,
                BuiltInRegistries.BLOCK.getKey(bearingAfter.getBlock()));
        for (Map.Entry<BlockPos, BlockState> entry : retainedBefore.entrySet()) {
            BlockState after = parentLevel.getBlockState(entry.getKey());
            if (!after.equals(entry.getValue())) {
                fail("retained parent cell changed during rudder child assembly at " + entry.getKey()
                        + " before=" + entry.getValue() + " after=" + after);
            }
            retainedCount++;
        }
        assertInt("all parent-main cells retained", EXPECTED_RETAINED_PARENT_MAIN, retainedCount);

        Object rawDependencies = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(rawDependencies instanceof Iterable<?> dependencies)) {
            fail("Swivel Bearing did not expose Sable connection dependencies: " + rawDependencies);
        }
        List<Object> childDependencies = new ArrayList<>();
        for (Object dependency : dependencies) {
            if (dependency != null) {
                childDependencies.add(dependency);
            }
        }
        assertInt("exactly one attached rudder child sublevel dependency", 1, childDependencies.size());
        Object child = childDependencies.getFirst();
        assertTrue("rudder child is a ServerSubLevel", child.getClass().getName().endsWith("ServerSubLevel"));

        Object massTracker = publicMethod(child, "getMassTracker").invoke(child);
        assertTrue("rudder child exposes mass tracker", massTracker != null);
        double childMass = doubleNumber(publicMethod(massTracker, "getMass").invoke(massTracker));
        assertFinite("rudder child mass", childMass);
        assertTrue("rudder child mass positive", childMass > 0.0);

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_YAW_CHILD PASS"
                        + " rudderPayloadConsumed=" + consumed
                        + " retainedParentMain=" + retainedCount
                        + " dependencyCount=" + childDependencies.size()
                        + " neutralTargetDegrees=" + neutralTargetAngle
                        + " childMassKpg=" + childMass
                        + " bearingAssembled=true"
                        + " actuationVerified=false"
                        + " yawForceVerified=false");
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return id;
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static double doubleNumber(Object value) {
        if (!(value instanceof Number)) {
            fail("expected numeric value, got " + value);
        }
        return ((Number) value).doubleValue();
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNearZero(String label, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual) > tolerance) {
            fail(label + ": expected near zero actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertFinite(String label, double value) {
        if (!Double.isFinite(value)) {
            fail(label + ": expected finite actual=" + value);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_YAW_CHILD FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 yaw-child runtime acceptance failed: " + reason);
    }
}
