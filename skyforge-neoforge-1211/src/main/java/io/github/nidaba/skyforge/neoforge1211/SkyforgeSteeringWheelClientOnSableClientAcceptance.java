package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of PLATFORM-006 Steering Wheel qualification. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeSteeringWheelClientOnSableClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 120_000_000_000L;
    private static final int ACQUIRE_RETRY_LIMIT_TICKS = 40;
    private static final double CLIENT_SERVER_POSE_TOLERANCE_BLOCKS = 0.25;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static int stage;
    private static int stageTicks;
    private static boolean clientSubLevelReady;
    private static boolean clientGameplayReady;
    private static boolean clientServerPoseConverged;
    private static boolean clientSetupRepositioningUsed;
    private static boolean clientShapeQualifiedAim;
    private static boolean clientHoldAcquired;
    private static Vec3 qualifiedWheelPlotHit;
    private static InteractionResult wheelUseResult;

    private SkyforgeSteeringWheelClientOnSableClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot =
                SkyforgeSteeringWheelClientOnSableBridge.snapshot();
        long now = System.nanoTime();
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = now;
        }
        if (now - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            String screen = minecraft.screen == null ? "<null>" : minecraft.screen.getClass().getName();
            fail("actual-client Steering Wheel interaction did not complete within 120 seconds"
                    + " readiness={level=" + (minecraft.level != null)
                    + ",player=" + (player != null)
                    + ",gameMode=" + (minecraft.gameMode != null)
                    + ",bridge=" + (snapshot != null)
                    + ",serverPlayerPositioned="
                    + SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.playerPositioned()
                    + ",clientSubLevelReady=" + clientSubLevelReady
                    + ",clientGameplayReady=" + clientGameplayReady
                    + ",clientServerPoseConverged=" + clientServerPoseConverged
                    + ",screen=" + screen + "}");
            return;
        }

        if (minecraft.level == null || player == null || minecraft.gameMode == null || snapshot == null) {
            return;
        }
        if (!SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.playerPositioned()) {
            return;
        }

        try {
            stageTicks++;
            switch (stage) {
                case 0 -> acquireSteeringWheel(minecraft, player, snapshot);
                case 1 -> awaitActivePacket(minecraft, player, snapshot);
                case 2 -> awaitReleaseAndSettle(minecraft, player, snapshot);
                default -> fail("invalid PLATFORM-006 client stage " + stage);
            }
        } catch (ReflectiveOperationException failure) {
            fail("real Simulated client interaction reflection failed: " + failure);
        } catch (RuntimeException failure) {
            fail("actual-client Steering Wheel interaction failed: " + failure);
        }
    }

    private static void acquireSteeringWheel(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot)
            throws ReflectiveOperationException {
        BlockPos wheelPos = snapshot.steeringWheelPos();
        if (!clientSubLevelReady) {
            if (!sableSubLevelReady(minecraft.level, wheelPos)) {
                stageTicks = 0;
                return;
            }
            clientSubLevelReady = true;
            stageTicks = 0;
            return;
        }
        if (minecraft.screen != null) {
            stageTicks = 0;
            return;
        }
        clientGameplayReady = true;

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 clientGlobalWheelCenter = projectOutOfClientRenderPose(
                minecraft.level, wheelPos, Vec3.atCenterOf(wheelPos), partialTick);
        if (clientGlobalWheelCenter.distanceTo(snapshot.expectedGlobalWheelCenter())
                > CLIENT_SERVER_POSE_TOLERANCE_BLOCKS) {
            stageTicks = 0;
            return;
        }
        clientServerPoseConverged = true;

        if (!minecraft.level.getBlockState(wheelPos).getBlock().getClass().getName().endsWith("SteeringWheelBlock")) {
            stageTicks = 0;
            return;
        }
        Object wheelBlockEntity = minecraft.level.getBlockEntity(wheelPos);
        if (wheelBlockEntity == null
                || !wheelBlockEntity.getClass().getName().endsWith("SteeringWheelBlockEntity")) {
            stageTicks = 0;
            return;
        }
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            fail("PLATFORM-006 requires an empty main hand for production Steering Wheel interaction");
            return;
        }

        positionClientAtRenderedStand(minecraft, player, snapshot, partialTick);
        Vec3 wheelPlotHit = selectShapeQualifiedSteeringWheelAim(
                minecraft, player, wheelPos, wheelBlockEntity, partialTick);
        if (wheelPlotHit == null) {
            if (stageTicks >= ACQUIRE_RETRY_LIMIT_TICKS) {
                fail("no shape-qualified Steering Wheel aim point satisfied Simulated lookingAtWheel "
                        + steeringWheelPredicateDiagnostics(minecraft, player, wheelPos, wheelBlockEntity));
            }
            return;
        }
        qualifiedWheelPlotHit = wheelPlotHit;
        clientShapeQualifiedAim = true;
        lookAt(player, projectOutOfClientRenderPose(minecraft.level, wheelPos, wheelPlotHit, partialTick));
        BlockHitResult hit = new BlockHitResult(wheelPlotHit, Direction.UP, wheelPos, false);
        minecraft.hitResult = hit;
        wheelUseResult = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);

        if (!holdInteractionActive()) {
            if (stageTicks >= ACQUIRE_RETRY_LIMIT_TICKS) {
                fail("real MultiPlayerGameMode.useItemOn never acquired Simulated SteeringWheelHandler "
                        + steeringWheelPredicateDiagnostics(minecraft, player, wheelPos, wheelBlockEntity));
            }
            return;
        }

        clientHoldAcquired = true;
        invokeSimulatedMouseMove(snapshot.mouseYawDelta(), 0.0);
        advanceStage();
    }

    private static void awaitActivePacket(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot)
            throws ReflectiveOperationException {
        positionClientAtRenderedStand(
                minecraft, player, snapshot, minecraft.getTimer().getGameTimeDeltaPartialTick(true));
        if (SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activePacketObserved()
                && SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activeResponseSettled()) {
            invokeSimulatedUseRelease();
            if (holdInteractionActive()) {
                fail("Simulated use-release lifecycle left SteeringWheelHandler active");
                return;
            }
            advanceStage();
            return;
        }
        if (stageTicks > 160) {
            fail("server never observed real client Steering Wheel active command and completed response "
                    + steeringHandlerDiagnostics(minecraft, snapshot));
        }
    }

    private static void awaitReleaseAndSettle(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot) {
        positionClientAtRenderedStand(
                minecraft, player, snapshot, minecraft.getTimer().getGameTimeDeltaPartialTick(true));
        if (!SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.releasePacketObserved()
                || !SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.settledObserved()) {
            if (stageTicks > 160) {
                fail("server never observed released Steering Wheel settle after real client use-release");
            }
            return;
        }
        complete(minecraft, snapshot);
    }

    private static void complete(
            Minecraft minecraft,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", snapshot.bodyId());
        evidence.put("movedSteeringWheel", snapshot.steeringWheelPos());
        evidence.put("clientSableSubLevelReady", clientSubLevelReady);
        evidence.put("clientGameplayReady", clientGameplayReady);
        evidence.put("clientServerPoseConverged", clientServerPoseConverged);
        evidence.put("clientShapeQualifiedAim", clientShapeQualifiedAim);
        evidence.put("qualifiedWheelPlotHit", qualifiedWheelPlotHit);
        evidence.put("clientTestSetupRepositioning", clientSetupRepositioningUsed);
        evidence.put("steeringWheelUseResult", String.valueOf(wheelUseResult));
        evidence.put("steeringHoldAcquired", clientHoldAcquired);
        evidence.put("activePacketRoundTrip", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activePacketObserved());
        evidence.put("activeResponseSettled", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activeResponseSettled());
        evidence.put("releasePacketRoundTrip", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.releasePacketObserved());
        evidence.put("activeTargetDegrees", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activeTargetDegrees());
        evidence.put("activeEndpointSpeed", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.activeEndpointSpeed());
        evidence.put("settledEndpointSpeed", SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.settledEndpointSpeed());
        evidence.put("canonicalBodyResolutionPerPhase", true);
        evidence.put("blockEntityResolutionPerPoll", true);
        evidence.put("fixtureLivenessTicketReleased",
                SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.fixtureLivenessTicketReleased());
        evidence.put("playerSableTrackingQualified", false);
        evidence.put("flightQualified", false);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }


    private static Vec3 selectShapeQualifiedSteeringWheelAim(
            Minecraft minecraft,
            LocalPlayer player,
            BlockPos wheelPos,
            Object wheelBlockEntity,
            float partialTick)
            throws ReflectiveOperationException {
        BlockState state = minecraft.level.getBlockState(wheelPos);
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Class<?> shapes = Class.forName("dev.simulated_team.simulated.index.SimBlockShapes");
        Object wheelShaper = shapes.getField("STEERING_WHEEL_FLOOR").get(null);
        VoxelShape wheelShape = (VoxelShape) wheelShaper.getClass().getMethod("get", Direction.class)
                .invoke(wheelShaper, facing);
        Method lookingAtWheel = state.getBlock().getClass().getMethod(
                "lookingAtWheel", Player.class, BlockPos.class, float.class, BlockState.class);
        Object angleInput = wheelBlockEntity.getClass().getField("angleInput").get(wheelBlockEntity);
        Class<?> scrollValueBehaviour = Class.forName(
                "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour");
        Method testHit = scrollValueBehaviour.getMethod("testHit", Vec3.class);
        double[] fractions = {0.25, 0.5, 0.75};
        for (AABB box : wheelShape.toAabbs()) {
            for (double fx : fractions) {
                for (double fy : fractions) {
                    for (double fz : fractions) {
                        Vec3 candidate = new Vec3(
                                wheelPos.getX() + lerp(box.minX, box.maxX, fx),
                                wheelPos.getY() + lerp(box.minY, box.maxY, fy),
                                wheelPos.getZ() + lerp(box.minZ, box.maxZ, fz));
                        lookAt(player, projectOutOfClientRenderPose(
                                minecraft.level, wheelPos, candidate, partialTick));
                        minecraft.hitResult = new BlockHitResult(candidate, Direction.UP, wheelPos, false);
                        boolean wheelVisible = (boolean) lookingAtWheel.invoke(
                                null, player, wheelPos, partialTick, state);
                        boolean angleInputHit = (boolean) testHit.invoke(angleInput, candidate);
                        if (wheelVisible && !angleInputHit) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static void positionClientAtRenderedStand(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot,
            float partialTick) {
        Vec3 renderedStand = projectOutOfClientRenderPose(
                minecraft.level, snapshot.steeringWheelPos(), snapshot.standPlotPosition(), partialTick);
        player.setPos(renderedStand.x, renderedStand.y, renderedStand.z);
        player.setDeltaMovement(Vec3.ZERO);
        clientSetupRepositioningUsed = true;
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    private static boolean sableSubLevelReady(Level level, BlockPos plotPosition) {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Object helper = sable.getField("HELPER").get(null);
            Method method = helper.getClass().getMethod("getContaining", Level.class, double.class, double.class);
            return method.invoke(helper, level, plotPosition.getX() + 0.5, plotPosition.getZ() + 0.5) != null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not resolve PLATFORM-006 client Sable sublevel readiness", failure);
        }
    }

    private static Vec3 projectOutOfClientRenderPose(
            Level level, BlockPos plotAnchor, Vec3 plotPosition, float partialTick) {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Object helper = sable.getField("HELPER").get(null);
            Method containingMethod = helper.getClass().getMethod(
                    "getContaining", Level.class, double.class, double.class);
            Object subLevel = containingMethod.invoke(
                    helper, level, plotAnchor.getX() + 0.5, plotAnchor.getZ() + 0.5);
            if (subLevel == null || !subLevel.getClass().getName().endsWith("ClientSubLevel")) {
                throw new IllegalStateException("Sable client sublevel unavailable for render-pose projection: " + subLevel);
            }
            Object renderPose = subLevel.getClass().getMethod("renderPose", float.class).invoke(subLevel, partialTick);
            Object projected = renderPose.getClass().getMethod("transformPosition", Vec3.class)
                    .invoke(renderPose, plotPosition);
            if (!(projected instanceof Vec3 globalPosition)) {
                throw new IllegalStateException("Sable client renderPose transformPosition returned " + projected);
            }
            return globalPosition;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "could not project PLATFORM-006 plot position through client renderPose", failure);
        }
    }

    private static boolean holdInteractionActive() throws ReflectiveOperationException {
        Class<?> manager = Class.forName("dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager");
        Method method = manager.getMethod("isActive");
        return (boolean) method.invoke(null);
    }

    private static void invokeSimulatedMouseMove(double yaw, double pitch) throws ReflectiveOperationException {
        Class<?> events = Class.forName("dev.simulated_team.simulated.events.SimulatedCommonClientEvents");
        events.getMethod("onMouseMove", double.class, double.class).invoke(null, yaw, pitch);
    }

    private static void invokeSimulatedUseRelease() throws ReflectiveOperationException {
        Class<?> input = Class.forName("dev.simulated_team.simulated.util.click_interactions.InteractCallback$Input");
        Object useMouseInput = input.getMethod("mouse", int.class).invoke(null, 1);
        Class<?> events = Class.forName("dev.simulated_team.simulated.events.SimulatedCommonClientEvents");
        events.getMethod("onBeforeMouseInput", input, int.class, int.class)
                .invoke(null, useMouseInput, 0, 0);
    }

    private static String steeringWheelPredicateDiagnostics(
            Minecraft minecraft,
            LocalPlayer player,
            BlockPos wheelPos,
            Object wheelBlockEntity)
            throws ReflectiveOperationException {
        BlockState state = minecraft.level.getBlockState(wheelPos);
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Method lookingAtWheelMethod = state.getBlock().getClass().getMethod(
                "lookingAtWheel", Player.class, BlockPos.class, float.class, BlockState.class);
        boolean lookingAtWheel = (boolean) lookingAtWheelMethod.invoke(null, player, wheelPos, partialTick, state);
        boolean held = wheelBlockEntity.getClass().getField("held").getBoolean(wheelBlockEntity);
        Method materialValidMethod = wheelBlockEntity.getClass().getMethod("isMaterialValid", ItemStack.class);
        boolean materialValid = (boolean) materialValidMethod.invoke(
                wheelBlockEntity, player.getItemInHand(InteractionHand.MAIN_HAND));
        Object angleInput = wheelBlockEntity.getClass().getField("angleInput").get(wheelBlockEntity);
        Class<?> scrollValueBehaviour = Class.forName(
                "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour");
        Method testHitMethod = scrollValueBehaviour.getMethod("testHit", Vec3.class);
        Vec3 hitLocation = minecraft.hitResult == null ? Vec3.ZERO : minecraft.hitResult.getLocation();
        boolean angleInputHit = (boolean) testHitMethod.invoke(angleInput, hitLocation);
        return "quietUsePredicates={lookingAtWheel=" + lookingAtWheel
                + ",held=" + held
                + ",materialValid=" + materialValid
                + ",angleInputHit=" + angleInputHit
                + ",wheelUseResult=" + wheelUseResult
                + ",hitLocation=" + hitLocation
                + ",projectedCenter=" + projectOutOfClientRenderPose(
                        minecraft.level, wheelPos, Vec3.atCenterOf(wheelPos), partialTick)
                + ",expectedGlobalCenter=" + SkyforgeSteeringWheelClientOnSableBridge.snapshot().expectedGlobalWheelCenter()
                + ",clientServerPoseConverged=" + clientServerPoseConverged
                + ",eye=" + player.getEyePosition(partialTick)
                + ",view=" + player.getViewVector(partialTick)
                + ",partialTick=" + partialTick + "}";
    }

    private static String steeringHandlerDiagnostics(
            Minecraft minecraft,
            SkyforgeSteeringWheelClientOnSableBridge.Snapshot snapshot)
            throws ReflectiveOperationException {
        Class<?> handler = Class.forName(
                "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelHandler");
        Field updatedField = handler.getDeclaredField("updated");
        Field rawAngleField = handler.getDeclaredField("rawAngle");
        Field effectiveAngleField = handler.getDeclaredField("effectiveAngle");
        updatedField.setAccessible(true);
        rawAngleField.setAccessible(true);
        effectiveAngleField.setAccessible(true);
        Object wheel = minecraft.level.getBlockEntity(snapshot.steeringWheelPos());
        boolean clientWheelHeld = wheel != null && wheel.getClass().getField("held").getBoolean(wheel);
        float clientWheelTarget = wheel == null
                ? Float.NaN
                : wheel.getClass().getField("targetAngleToUpdate").getFloat(wheel);
        return "steeringHandler={active=" + holdInteractionActive()
                + ",updated=" + updatedField.getBoolean(null)
                + ",rawAngle=" + rawAngleField.getFloat(null)
                + ",effectiveAngle=" + effectiveAngleField.getFloat(null)
                + ",clientWheelHeld=" + clientWheelHeld
                + ",clientWheelTarget=" + clientWheelTarget
                + ",stageTicks=" + stageTicks
                + ",screen=" + (minecraft.screen == null ? "<null>" : minecraft.screen.getClass().getName())
                + "}";
    }

    private static void advanceStage() {
        stage++;
        stageTicks = 0;
    }

    private static void fail(String reason) {
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
    }
}
