package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client coexistence proof for a real Create train and the WBY distant Sable render path. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1TrainSanityClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;
    private static final int TRAIN_STABLE_TICKS_REQUIRED = 12;
    private static final int DISTANT_STABLE_TICKS_REQUIRED = 20;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final double MAX_SABLE_TRANSFORM_ERROR_BLOCKS = 8.0;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static int trainStableTicks;
    private static int distantStableTicks;
    private static boolean trainRendererObserved;
    private static boolean trainRailwayRecordObserved;
    private static boolean ssrdRenderObserved;
    private static boolean complete;
    private static double trainHorizontalDistanceBlocks = Double.NaN;
    private static double sableHorizontalDistanceBlocks = Double.NaN;
    private static double sableTransformErrorBlocks = Double.NaN;
    private static String trainRendererClass = "";

    private SkyforgeWbyWave1TrainSanityClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1TrainSanityLifecycleAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY train-sanity actual-client proof did not qualify within 180 seconds"
                    + "; trainStableTicks=" + trainStableTicks
                    + "; distantStableTicks=" + distantStableTicks
                    + "; trainRendererObserved=" + trainRendererObserved
                    + "; trainRailwayRecordObserved=" + trainRailwayRecordObserved
                    + "; ssrdRenderObserved=" + ssrdRenderObserved);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1TrainSanityLifecycleAcceptance.Snapshot trainSnapshot =
                SkyforgeWbyWave1TrainSanityLifecycleAcceptance.snapshot();
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.Snapshot sableSnapshot =
                SkyforgeWbyWave1VisibilityLifecycleAcceptance.snapshot();
        if (trainSnapshot == null || sableSnapshot == null) {
            return;
        }

        try {
            requireClientMods();

            Entity carriage = findCarriage(minecraft, trainSnapshot.trainId());
            if (carriage == null) {
                if (trainStableTicks > 0 || distantStableTicks > 0) {
                    fail("Create carriage disappeared during WBY train-sanity proof: trainId="
                            + trainSnapshot.trainId());
                }
                return;
            }
            if (!trainSnapshot.carriageEntityId().equals(carriage.getUUID())) {
                fail("Create carriage UUID changed across server/client tracking: expected="
                        + trainSnapshot.carriageEntityId() + ", actual=" + carriage.getUUID());
                return;
            }

            trainRailwayRecordObserved |= clientTrainRecordPresent(minecraft.level, trainSnapshot.trainId());

            EntityRenderer<? super Entity> renderer =
                    minecraft.getEntityRenderDispatcher().getRenderer(carriage);
            trainRendererClass = renderer.getClass().getName();
            trainRendererObserved |= trainRendererClass.endsWith("CarriageContraptionEntityRenderer");

            trainHorizontalDistanceBlocks = Math.hypot(
                    carriage.getX() - player.getX(),
                    carriage.getZ() - player.getZ());

            boolean validForRender =
                    carriage.getClass().getField("validForRender").getBoolean(carriage);
            boolean firstPositionUpdate =
                    carriage.getClass().getField("firstPositionUpdate").getBoolean(carriage);
            boolean trainQualified = carriage.isAlive()
                    && validForRender
                    && !firstPositionUpdate
                    && trainRailwayRecordObserved
                    && trainRendererObserved
                    && trainHorizontalDistanceBlocks < 64.0;

            if (trainStableTicks < TRAIN_STABLE_TICKS_REQUIRED) {
                lookAt(player, carriage.position().add(0.0, 1.0, 0.0));
                if (!trainQualified) {
                    trainStableTicks = 0;
                    return;
                }
                trainStableTicks++;
                return;
            }

            if (!trainQualified) {
                fail("Create train lost valid client render state while distant Sable was observed");
                return;
            }

            Object sableBody = clientSubLevel(minecraft.level, sableSnapshot.bodyId());
            if (sableBody == null) {
                distantStableTicks = 0;
                return;
            }
            boolean finalized = (Boolean) publicMethod(sableBody, "isFinalized").invoke(sableBody);
            if (!finalized) {
                distantStableTicks = 0;
                return;
            }

            Vec3 sableRenderPosition = renderPosition(sableBody);
            sableTransformErrorBlocks = sableRenderPosition.distanceTo(sableSnapshot.expectedBodyCenter());
            if (sableTransformErrorBlocks > MAX_SABLE_TRANSFORM_ERROR_BLOCKS) {
                fail("distant Sable transform drifted during train coexistence: errorBlocks="
                        + sableTransformErrorBlocks);
                return;
            }
            lookAt(player, sableRenderPosition);

            sableHorizontalDistanceBlocks = Math.hypot(
                    sableRenderPosition.x - player.getX(),
                    sableRenderPosition.z - player.getZ());
            boolean beyondVanilla = sableHorizontalDistanceBlocks
                    > VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0;

            Object renderData = publicMethod(sableBody, "getRenderData").invoke(sableBody);
            boolean renderDataPresent = renderData != null;

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            ssrdRenderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);

            boolean distantQualified = beyondVanilla
                    && renderDataPresent
                    && ssrdRenderObserved
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS;

            if (!distantQualified) {
                distantStableTicks = 0;
                return;
            }

            distantStableTicks++;
            if (distantStableTicks >= DISTANT_STABLE_TICKS_REQUIRED) {
                complete(minecraft, trainSnapshot, sableSnapshot, distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 train-sanity client proof failed: " + failure);
        }
    }

    private static Entity findCarriage(Minecraft minecraft, UUID expectedTrainId)
            throws ReflectiveOperationException {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!entity.getClass().getName()
                    .equals("com.simibubi.create.content.trains.entity.CarriageContraptionEntity")) {
                continue;
            }
            Object trainId = entity.getClass().getField("trainId").get(entity);
            if (expectedTrainId.equals(trainId)) {
                return entity;
            }
        }
        return null;
    }

    private static boolean clientTrainRecordPresent(ClientLevel level, UUID expectedTrainId)
            throws ReflectiveOperationException {
        Object railways = Class.forName("com.simibubi.create.Create").getField("RAILWAYS").get(null);
        Object sided = compatibleOneArgMethod(railways, "sided", level).invoke(railways, level);
        Object trainsValue = sided.getClass().getField("trains").get(sided);
        if (!(trainsValue instanceof Map<?, ?> trains)) {
            throw new IllegalStateException("Create client railway trains did not resolve to a Map");
        }
        return trains.containsKey(expectedTrainId);
    }

    private static Object clientSubLevel(ClientLevel level, UUID bodyId)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return null;
        }
        return publicMethod(container, "getSubLevel", UUID.class).invoke(container, bodyId);
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                ((Number) publicMethod(position, "x").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "y").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "z").invoke(position)).doubleValue());
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY train-sanity client mod not loaded: " + modId);
            }
        }
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

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Method compatibleOneArgMethod(Object target, String name, Object argument)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(compatible arg)");
    }

    private static void complete(
            Minecraft minecraft,
            SkyforgeWbyWave1TrainSanityLifecycleAcceptance.Snapshot trainSnapshot,
            SkyforgeWbyWave1VisibilityLifecycleAcceptance.Snapshot sableSnapshot,
            DistanceEvidence distance) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1TrainSanityLifecycleAcceptance.markClientComplete();
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("trainId", trainSnapshot.trainId());
        evidence.put("carriageEntityId", trainSnapshot.carriageEntityId());
        evidence.put("trainRailwayRecordObserved", trainRailwayRecordObserved);
        evidence.put("trainRendererObserved", trainRendererObserved);
        evidence.put("trainRendererClass", trainRendererClass);
        evidence.put("trainStableTicks", trainStableTicks);
        evidence.put("trainHorizontalDistanceBlocks", trainHorizontalDistanceBlocks);
        evidence.put("distantSableBodyId", sableSnapshot.bodyId());
        evidence.put("distantSableStableTicks", distantStableTicks);
        evidence.put("sableHorizontalDistanceBlocks", sableHorizontalDistanceBlocks);
        evidence.put("sableTransformErrorBlocks", sableTransformErrorBlocks);
        evidence.put("sableTransformMatchesExpected",
                sableTransformErrorBlocks <= MAX_SABLE_TRANSFORM_ERROR_BLOCKS);
        evidence.put("ssrdSublevelRenderObserved", ssrdRenderObserved);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("trainAndDistantSableCoexisted", true);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1TrainSanityLifecycleAcceptance.markClientComplete();
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record DistanceEvidence(int chunks, String source) {}
}
