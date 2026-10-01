package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of PLATFORM-012 natural Sable tracking and inherited translation qualification. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgePlayerTrackingOnSableClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 120_000_000_000L;
    private static final int MOUNT_RETRY_INTERVAL_TICKS = 4;
    private static final int MOUNT_CLIENT_DEADLINE_TICKS = 260;
    private static final int DISMOUNT_CLIENT_DEADLINE_TICKS = 180;
    private static final int CLEANUP_CLIENT_DEADLINE_TICKS = 100;
    private static final int TRACKING_CLIENT_DEADLINE_TICKS = 100;
    private static final int TRANSLATION_CLIENT_DEADLINE_TICKS = 140;
    private static final double CLIENT_SERVER_POSE_TOLERANCE_BLOCKS = 0.25;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static int stage;
    private static int stageTicks;
    private static boolean clientSubLevelReady;
    private static boolean clientGameplayReady;
    private static boolean clientServerPoseConverged;
    private static boolean clientSetupRepositioningUsed;
    private static boolean clientSeatMounted;
    private static boolean clientSeatDismounted;
    private static boolean clientComplete;
    private static InteractionResult seatUseResult;
    private static int seatUseAttempts;
    private static UUID clientSeatEntityId;
    private static UUID clientTrackingId;
    private static boolean clientTrackingAcquired;
    private static boolean clientTranslationBaselineSubmitted;
    private static boolean clientTranslationResultSubmitted;
    private static double clientTranslationStartX;
    private static double clientTranslationEndX;

    private SkyforgePlayerTrackingOnSableClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        boolean enabled = Boolean.getBoolean(SkyforgePlayerTrackingOnSableLifecycleAcceptance.ENABLE_PROPERTY);
        boolean clientMode = SkyforgeAutomatedAcceptanceHarness.clientMode();
        publishMountDiagnostic("gate=client-tick-entry enabled=" + enabled
                + " clientMode=" + clientMode + " clientComplete=" + clientComplete);
        if (!enabled || !clientMode || clientComplete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot = SkyforgePlayerTrackingOnSableBridge.snapshot();
        publishMountDiagnostic("gate=client-top-readiness level=" + (minecraft.level != null)
                + " player=" + (player != null)
                + " gameMode=" + (minecraft.gameMode != null)
                + " bridge=" + (snapshot != null)
                + " serverPlayerPositioned=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.playerPositioned());
        long now = System.nanoTime();
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = now;
        }
        if (now - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            releaseControls(minecraft);
            fail("actual-client Sable tracking lifecycle did not complete within 120 seconds"
                    + " readiness={level=" + (minecraft.level != null)
                    + ",player=" + (player != null)
                    + ",gameMode=" + (minecraft.gameMode != null)
                    + ",bridge=" + (snapshot != null)
                    + ",serverPlayerPositioned=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.playerPositioned()
                    + ",clientSubLevelReady=" + clientSubLevelReady
                    + ",clientGameplayReady=" + clientGameplayReady
                    + ",clientServerPoseConverged=" + clientServerPoseConverged
                    + ",stage=" + stage + ",stageTicks=" + stageTicks + "}");
            return;
        }
        if (minecraft.level == null || player == null || minecraft.gameMode == null || snapshot == null) {
            return;
        }
        if (!SkyforgePlayerTrackingOnSableLifecycleAcceptance.playerPositioned()) {
            publishMountDiagnostic("gate=server-player-positioned value=false");
            return;
        }

        try {
            stageTicks++;
            switch (stage) {
                case 0 -> awaitMount(minecraft, player, snapshot);
                case 1 -> awaitDismount(minecraft, player);
                case 2 -> awaitCleanup(minecraft, player, snapshot);
                case 3 -> awaitNaturalTracking(minecraft, player, snapshot);
                case 4 -> awaitInheritedTranslation(minecraft, player, snapshot);
                default -> fail("invalid PLATFORM-012 client stage " + stage);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            releaseControls(minecraft);
            fail("actual-client Sable tracking lifecycle failed: " + failure);
        }
    }

    private static void awaitMount(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot) {
        BlockPos seatPos = snapshot.seatPos();
        if (!clientSubLevelReady) {
            if (!sableSubLevelReady(minecraft.level, seatPos)) {
                publishMountDiagnostic("gate=sublevel-ready value=false seatPos=" + seatPos);
                stageTicks = 0;
                return;
            }
            clientSubLevelReady = true;
            publishMountDiagnostic("gate=sublevel-ready value=true seatPos=" + seatPos);
            stageTicks = 0;
            return;
        }
        if (minecraft.screen != null) {
            publishMountDiagnostic("gate=gameplay-screen screen=" + minecraft.screen.getClass().getName());
            stageTicks = 0;
            return;
        }
        clientGameplayReady = true;
        String clientSeatBlock = String.valueOf(minecraft.level.getBlockState(seatPos));
        if (!minecraft.level.getBlockState(seatPos).getBlock().getClass().getName().endsWith("SeatBlock")) {
            publishMountDiagnostic("gate=seat-block value=false block=" + clientSeatBlock + " seatPos=" + seatPos);
            stageTicks = 0;
            return;
        }
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            fail("PLATFORM-012 requires an empty main hand for ordinary Create seat interaction");
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 renderedSeatCenter = projectOutOfClientRenderPose(
                minecraft.level, seatPos, Vec3.atCenterOf(seatPos), partialTick);
        double poseError = renderedSeatCenter.distanceTo(snapshot.expectedGlobalSeatCenter());
        if (poseError > CLIENT_SERVER_POSE_TOLERANCE_BLOCKS) {
            publishMountDiagnostic("gate=pose-convergence value=false poseError=" + poseError
                    + " renderedSeatCenter=" + renderedSeatCenter
                    + " expectedGlobalSeatCenter=" + snapshot.expectedGlobalSeatCenter()
                    + " clientSeatBlock=" + clientSeatBlock);
            stageTicks = 0;
            return;
        }
        clientServerPoseConverged = true;
        publishMountDiagnostic("gate=use-ready poseError=" + poseError
                + " clientSeatBlock=" + clientSeatBlock
                + " attempts=" + seatUseAttempts
                + " lastUseResult=" + seatUseResult);

        UUID serverSeatId = SkyforgePlayerTrackingOnSableLifecycleAcceptance.seatEntityId();
        Entity vehicle = player.getVehicle();
        if (SkyforgePlayerTrackingOnSableLifecycleAcceptance.seatMountObserved()) {
            if (vehicle == null || !player.isPassenger()) {
                if (stageTicks > MOUNT_CLIENT_DEADLINE_TICKS) {
                    fail("server observed Create SeatEntity mount but LocalPlayer never synchronized passenger state");
                }
                return;
            }
            if (!vehicle.getClass().getName().endsWith("SeatEntity")) {
                fail("LocalPlayer synchronized unexpected vehicle after Create seat use: " + vehicle.getClass().getName());
                return;
            }
            if (serverSeatId == null || !serverSeatId.equals(vehicle.getUUID())) {
                fail("client/server Create SeatEntity UUID mismatch: server=" + serverSeatId
                        + " client=" + vehicle.getUUID());
                return;
            }
            clientSeatEntityId = vehicle.getUUID();
            clientSeatMounted = true;
            minecraft.options.keyShift.setDown(true);
            advanceStage();
            return;
        }

        if (player.isPassenger()) {
            fail("LocalPlayer became passenger before server accepted PLATFORM-012 SeatEntity mount");
            return;
        }

        if (stageTicks == 1 || stageTicks % MOUNT_RETRY_INTERVAL_TICKS == 0) {
            positionClientAtRenderedStand(minecraft, player, snapshot, partialTick);
            lookAt(player, renderedSeatCenter);
            Vec3 seatPlotCenter = Vec3.atCenterOf(seatPos);
            BlockHitResult hit = new BlockHitResult(seatPlotCenter, Direction.UP, seatPos, false);
            minecraft.hitResult = hit;
            seatUseAttempts++;
            seatUseResult = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            publishMountDiagnostic("gate=seat-use-attempt attempts=" + seatUseAttempts
                    + " result=" + seatUseResult
                    + " poseError=" + poseError
                    + " playerPos=" + player.position()
                    + " seatPlotCenter=" + seatPlotCenter
                    + " renderedSeatCenter=" + renderedSeatCenter);
        }

        if (stageTicks > MOUNT_CLIENT_DEADLINE_TICKS) {
            fail("ordinary MultiPlayerGameMode.useItemOn did not reach client/server Create SeatEntity mount agreement"
                    + " lastUseResult=" + seatUseResult);
        }
    }

    private static void awaitDismount(Minecraft minecraft, LocalPlayer player) {
        minecraft.options.keyShift.setDown(true);
        boolean serverDismounted = SkyforgePlayerTrackingOnSableLifecycleAcceptance.seatDismountObserved();
        if (serverDismounted && !player.isPassenger()) {
            clientSeatDismounted = true;
            releaseCrouch(minecraft);
            advanceStage();
            return;
        }
        if (stageTicks > DISMOUNT_CLIENT_DEADLINE_TICKS) {
            releaseCrouch(minecraft);
            fail("ordinary client crouch input did not produce client/server Create seat dismount agreement"
                    + " serverDismounted=" + serverDismounted
                    + " clientPassenger=" + player.isPassenger());
        }
    }

    private static void awaitCleanup(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot) {
        if (player.isPassenger()) {
            fail("LocalPlayer regained passenger state after agreed Create seat dismount");
            return;
        }
        if (SkyforgePlayerTrackingOnSableLifecycleAcceptance.seatEntityCleanupObserved()) {
            releaseCrouch(minecraft);
            advanceStage();
            return;
        }
        if (stageTicks > CLEANUP_CLIENT_DEADLINE_TICKS) {
            fail("server did not discard empty Create SeatEntity after agreed dismount");
        }
    }

    private static void awaitNaturalTracking(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot)
            throws ReflectiveOperationException {
        if (player.isPassenger()) {
            fail("PLATFORM-012 tracking acquisition must occur after ordinary seat dismount");
            return;
        }
        minecraft.options.keyShift.setDown(false);
        if (stageTicks == 2 || stageTicks == 12 || stageTicks == 22 || stageTicks == 32 || stageTicks == 42) {
            minecraft.options.keyJump.setDown(true);
        } else {
            minecraft.options.keyJump.setDown(false);
        }

        Object trackingSubLevel = clientTrackingSubLevel(player);
        clientTrackingId = subLevelUniqueId(trackingSubLevel);
        Object expectedSubLevel = clientContainingSubLevel(minecraft.level, snapshot.seatPos());
        UUID expectedId = subLevelUniqueId(expectedSubLevel);
        if (clientTrackingId != null && clientTrackingId.equals(expectedId) && clientTrackingId.equals(snapshot.bodyId())) {
            clientTrackingAcquired = true;
            SkyforgePlayerTrackingOnSableLifecycleAcceptance.observeClientTracking(clientTrackingId);
            if (SkyforgePlayerTrackingOnSableLifecycleAcceptance.serverTrackingAcquired()
                    && SkyforgePlayerTrackingOnSableLifecycleAcceptance.translationBaselineRequested()) {
                minecraft.options.keyJump.setDown(false);
                clientTranslationStartX = player.getX();
                SkyforgePlayerTrackingOnSableLifecycleAcceptance.submitClientTranslationBaseline(clientTranslationStartX);
                clientTranslationBaselineSubmitted = true;
                advanceStage();
                return;
            }
        }

        if (stageTicks > TRACKING_CLIENT_DEADLINE_TICKS) {
            minecraft.options.keyJump.setDown(false);
            fail("actual client did not naturally acquire persistent Sable tracking through collision/movement packets"
                    + " expectedBodyId=" + snapshot.bodyId()
                    + " expectedClientSubLevelId=" + expectedId
                    + " clientTrackingId=" + clientTrackingId
                    + " serverTrackingAcquired=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.serverTrackingAcquired()
                    + " clientPosition=" + player.position()
                    + " onGround=" + player.onGround());
        }
    }

    private static void awaitInheritedTranslation(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot)
            throws ReflectiveOperationException {
        releaseControls(minecraft);
        if (player.isPassenger()) {
            fail("PLATFORM-012 translation measurement regained passenger state");
            return;
        }
        UUID currentTrackingId = subLevelUniqueId(clientTrackingSubLevel(player));
        if (!snapshot.bodyId().equals(currentTrackingId)) {
            fail("actual client left persistent Sable tracking during parent translation"
                    + " expected=" + snapshot.bodyId() + " observed=" + currentTrackingId);
            return;
        }
        clientTrackingId = currentTrackingId;
        SkyforgePlayerTrackingOnSableLifecycleAcceptance.observeClientTracking(currentTrackingId);

        if (SkyforgePlayerTrackingOnSableLifecycleAcceptance.translationServerQualified()
                && !clientTranslationResultSubmitted) {
            double parentDeltaX = SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredParentDeltaX();
            double clientDeltaX = player.getX() - clientTranslationStartX;
            double clientError = Math.abs(clientDeltaX - parentDeltaX);
            double tolerance = SkyforgePlayerTrackingOnSableLifecycleAcceptance.translationToleranceBlocks();
            if (Math.signum(clientDeltaX) == Math.signum(parentDeltaX) && clientError <= tolerance) {
                clientTranslationEndX = player.getX();
                SkyforgePlayerTrackingOnSableLifecycleAcceptance.submitClientTranslationResult(clientTranslationEndX);
                clientTranslationResultSubmitted = true;
            }
            return;
        }
        if (SkyforgePlayerTrackingOnSableLifecycleAcceptance.trackingLifecycleQualified()) {
            complete(minecraft, snapshot);
            return;
        }
        if (stageTicks > TRANSLATION_CLIENT_DEADLINE_TICKS) {
            fail("server did not complete bounded inherited-parent translation"
                    + " parentDeltaX=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredParentDeltaX()
                    + " serverPlayerDeltaX=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredServerPlayerDeltaX()
                    + " clientPlayerDeltaX=" + SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredClientPlayerDeltaX()
                    + " clientTrackingId=" + clientTrackingId);
        }
    }

    private static Object clientTrackingSubLevel(LocalPlayer player) throws ReflectiveOperationException {
        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
        Object helper = sableClass.getField("HELPER").get(null);
        Method method = helper.getClass().getMethod("getTrackingSubLevel", Entity.class);
        return method.invoke(helper, player);
    }

    private static Object clientContainingSubLevel(Level level, BlockPos plotPosition)
            throws ReflectiveOperationException {
        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
        Object helper = sableClass.getField("HELPER").get(null);
        Method method = helper.getClass().getMethod("getContaining", Level.class, double.class, double.class);
        return method.invoke(helper, level, plotPosition.getX() + 0.5, plotPosition.getZ() + 0.5);
    }

    private static UUID subLevelUniqueId(Object subLevel) throws ReflectiveOperationException {
        if (subLevel == null) {
            return null;
        }
        Object value = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);
        if (value == null) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static void complete(
            Minecraft minecraft,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot) {
        if (clientComplete) {
            return;
        }
        releaseControls(minecraft);
        clientComplete = true;
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", snapshot.bodyId());
        evidence.put("movedSeat", snapshot.seatPos());
        evidence.put("clientSableSubLevelReady", clientSubLevelReady);
        evidence.put("clientGameplayReady", clientGameplayReady);
        evidence.put("clientServerPoseConverged", clientServerPoseConverged);
        evidence.put("clientTestSetupRepositioning", clientSetupRepositioningUsed);
        evidence.put("seatUseResult", String.valueOf(seatUseResult));
        evidence.put("seatMountClientServerAgreement", clientSeatMounted);
        evidence.put("seatEntityId", clientSeatEntityId);
        evidence.put("seatDismountClientServerAgreement", clientSeatDismounted);
        evidence.put("seatEntityCleanupObserved", SkyforgePlayerTrackingOnSableLifecycleAcceptance.seatEntityCleanupObserved());
        evidence.put("samePersistentSableUuid", true);
        evidence.put("fixtureLivenessTicketReleased", SkyforgePlayerTrackingOnSableLifecycleAcceptance.fixtureLivenessTicketReleased());
        evidence.put("naturalTrackingAcquired", clientTrackingAcquired);
        evidence.put("clientTrackingId", clientTrackingId);
        evidence.put("serverTrackingAcquired", SkyforgePlayerTrackingOnSableLifecycleAcceptance.serverTrackingAcquired());
        evidence.put("clientTranslationBaselineSubmitted", clientTranslationBaselineSubmitted);
        evidence.put("clientTranslationResultSubmitted", clientTranslationResultSubmitted);
        evidence.put("clientTranslationStartX", clientTranslationStartX);
        evidence.put("clientTranslationEndX", clientTranslationEndX);
        evidence.put("parentDeltaX", SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredParentDeltaX());
        evidence.put("serverPlayerDeltaX", SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredServerPlayerDeltaX());
        evidence.put("clientPlayerDeltaX", SkyforgePlayerTrackingOnSableLifecycleAcceptance.measuredClientPlayerDeltaX());
        evidence.put("harnessTrackingSetterInvoked", false);
        evidence.put("harnessPlayerMutationDuringMeasurement", false);
        evidence.put("playerSableTrackingQualified", true);
        evidence.put("inheritedParentTranslationQualified", true);
        evidence.put("flightQualified", false);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void positionClientAtRenderedStand(
            Minecraft minecraft,
            LocalPlayer player,
            SkyforgePlayerTrackingOnSableBridge.Snapshot snapshot,
            float partialTick) {
        Vec3 renderedStand = projectOutOfClientRenderPose(
                minecraft.level, snapshot.seatPos(), snapshot.standPlotPosition(), partialTick);
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
            throw new IllegalStateException("could not resolve PLATFORM-012 client Sable sublevel readiness", failure);
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
                throw new IllegalStateException("Sable ClientSubLevel unavailable for seat render-pose projection: " + subLevel);
            }
            Object renderPose = subLevel.getClass().getMethod("renderPose", float.class).invoke(subLevel, partialTick);
            Object projected = renderPose.getClass().getMethod("transformPosition", Vec3.class)
                    .invoke(renderPose, plotPosition);
            if (!(projected instanceof Vec3 globalPosition)) {
                throw new IllegalStateException("Sable client renderPose transformPosition returned " + projected);
            }
            return globalPosition;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not project PLATFORM-012 plot position through client renderPose", failure);
        }
    }

    private static void publishMountDiagnostic(String diagnostic) {
        SkyforgePlayerTrackingOnSableBridge.publishMountDiagnostic(diagnostic);
    }

    private static void advanceStage() {
        stage++;
        stageTicks = 0;
    }

    private static void releaseCrouch(Minecraft minecraft) {
        minecraft.options.keyShift.setDown(false);
    }

    private static void releaseControls(Minecraft minecraft) {
        minecraft.options.keyShift.setDown(false);
        minecraft.options.keyJump.setDown(false);
    }

    private static void fail(String reason) {
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
    }
}
