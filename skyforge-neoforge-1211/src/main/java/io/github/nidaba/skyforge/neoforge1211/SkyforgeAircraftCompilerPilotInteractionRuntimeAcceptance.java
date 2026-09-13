package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * AIRCRAFT-001 v0.19 headless evidence boundary.
 *
 * <p>This verifier proves only that the compiled Create seat and Simulated Steering Wheel survive
 * the complete Sable recapture at their expected moved coordinates. It intentionally does not
 * simulate a connected client, player seat interaction, Sable player tracking, or network packet
 * delivery.</p>
 */
final class SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.class.getName());

    private SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance() {}

    static void verifyAssembledCockpitPresence(
            ServerLevel level,
            BlockPos movedPilotSeatPos,
            BlockPos movedSteeringWheelPos,
            String expectedPilotSeatResource,
            String expectedSteeringWheelResource,
            String expectedWheelFacing,
            String expectedWheelOnFloor,
            String expectedWheelWaterlogged) {
        ResourceLocation seatId = requireId(expectedPilotSeatResource);
        ResourceLocation wheelId = requireId(expectedSteeringWheelResource);
        assertEquals("v0.19 exact pilot seat resource", seatId,
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedPilotSeatPos).getBlock()));
        assertEquals("v0.19 exact Steering Wheel resource", wheelId,
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(movedSteeringWheelPos).getBlock()));

        BlockState seatState = level.getBlockState(movedPilotSeatPos);
        BlockState wheelState = level.getBlockState(movedSteeringWheelPos);
        assertTrue("v0.19 pilot seat survived Sable recapture", !seatState.isAir());
        assertTrue("v0.19 Steering Wheel survived Sable recapture", !wheelState.isAir());
        assertTrue("v0.19 pilot seat runtime class is Create SeatBlock",
                seatState.getBlock().getClass().getName().endsWith("SeatBlock"));
        assertEquals("v0.19 Steering Wheel facing", expectedWheelFacing, readProperty(wheelState, "facing"));
        assertEquals("v0.19 Steering Wheel on_floor", expectedWheelOnFloor, readProperty(wheelState, "on_floor"));
        assertEquals("v0.19 Steering Wheel waterlogged", expectedWheelWaterlogged,
                readProperty(wheelState, "waterlogged"));

        BlockEntity wheelEntity = level.getBlockEntity(movedSteeringWheelPos);
        assertTrue("v0.19 Steering Wheel block entity survives Sable recapture",
                wheelEntity != null && wheelEntity.getClass().getName().endsWith("SteeringWheelBlockEntity"));

        int manhattan = Math.abs(movedPilotSeatPos.getX() - movedSteeringWheelPos.getX())
                + Math.abs(movedPilotSeatPos.getY() - movedSteeringWheelPos.getY())
                + Math.abs(movedPilotSeatPos.getZ() - movedSteeringWheelPos.getZ());
        assertTrue("v0.19 moved seat and Steering Wheel remain adjacent", manhattan == 1);
        assertTrue("v0.19 moved seat and Steering Wheel remain at same elevation",
                movedPilotSeatPos.getY() == movedSteeringWheelPos.getY());

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_PILOT_INTERACTION_PRESENCE PASS"
                        + " pilotSeat=" + movedPilotSeatPos
                        + " steeringWheel=" + movedSteeringWheelPos
                        + " seatResource=" + seatId
                        + " wheelResource=" + wheelId
                        + " assembledCockpitPresenceVerified=true"
                        + " playerSeatInteractionVerified=false"
                        + " playerSableTrackingVerified=false"
                        + " realClientHoldInteractionVerified=false"
                        + " steeringPacketRoundTripVerified=false"
                        + " flightQualified=false");
    }

    private static ResourceLocation requireId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            fail("invalid expected resource id " + value);
        }
        return id;
    }

    private static String readProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return readPropertyValue(state, property);
            }
        }
        fail("missing blockstate property " + name + " on " + state);
        throw new AssertionError();
    }

    private static <T extends Comparable<T>> String readPropertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label);
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_PILOT_INTERACTION_PRESENCE FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 v0.19 assembled cockpit presence failed: " + reason);
    }
}
