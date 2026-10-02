package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0084 Stage-2B headless runtime acceptance.
 *
 * <p>This fixture keeps AAL optional and uses only its exact released public API. Skyforge owns
 * canonical identities; AAL receives deterministic derived route/stop/vehicle references and
 * opaque station UUIDs persisted in a Skyforge-owned binding sidecar. Prepare proves direct
 * generated-route injection plus real playback start/stop. Reload reopens the same world and
 * proves AAL's station NBT restored the route and the canonical-to-native bindings before
 * starting/stopping playback again.</p>
 */
final class SkyforgeSfImp0084AalRuntimeAcceptance {
    static final String PHASE_PROPERTY = "skyforge.dev.sfImp0084AalStage2";

    private static final String AAL_MOD_ID = "create_aeronautics_automated_logistics";
    private static final ResourceLocation AAL_STATION =
            ResourceLocation.fromNamespaceAndPath(AAL_MOD_ID, "airship_station");
    private static final Path BINDING_FILE = Path.of("sf-imp-0084-aal.bindings");

    private static final BlockPos ORIGIN_STATION_POS = new BlockPos(0, 120, 0);
    private static final BlockPos DESTINATION_STATION_POS = new BlockPos(10, 120, 0);
    private static final Vec3 VEHICLE_START = new Vec3(1.5D, 121.0D, 0.5D);

    private static final UUID SKYFORGE_ROUTE_ID =
            UUID.fromString("7f4274ef-3b69-5f27-ae5e-8e91528f843d");
    private static final UUID SKYFORGE_VEHICLE_ID =
            UUID.fromString("9109cbee-a2e7-52fe-b741-49e463f70edb");
    private static final UUID SKYFORGE_ORIGIN_STOP_ID =
            UUID.fromString("7be66265-55b5-54ca-b5d0-4d47a3c6c020");
    private static final UUID SKYFORGE_DESTINATION_STOP_ID =
            UUID.fromString("c6061136-119a-5718-b53a-677b21d34474");
    private static final UUID SKYFORGE_ORIGIN_STATION_ID =
            UUID.fromString("ee44d4df-f49b-5c4d-944f-a4d546b7e1df");
    private static final UUID SKYFORGE_DESTINATION_STATION_ID =
            UUID.fromString("57a2884a-bf6e-55be-8dbb-b3dd2b2af41d");

    private static final long START_DELAY_TICKS = 2L;
    private static final long RELOAD_DEADLINE_TICKS = 100L;

    private static String phase;
    private static ServerLevel level;
    private static long startedAtTick;
    private static boolean complete;

    private record Binding(
            UUID originNativeStationId,
            UUID destinationNativeStationId,
            UUID nativeVehicleId) {}

    private SkyforgeSfImp0084AalRuntimeAcceptance() {}

    static void installFromSystemProperty() {
        phase = System.getProperty(PHASE_PROPERTY, "").trim();
        if (phase.isEmpty()) {
            return;
        }
        if (!phase.equals("prepare") && !phase.equals("reload")) {
            throw new IllegalArgumentException(PHASE_PROPERTY + " must be prepare or reload, got " + phase);
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSfImp0084AalRuntimeAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeSfImp0084AalRuntimeAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        startedAtTick = level.getGameTime();
        try {
            requireMods();
            level.getChunkAt(ORIGIN_STATION_POS);
            level.getChunkAt(DESTINATION_STATION_POS);
        } catch (RuntimeException failure) {
            fail(event.getServer(), "SF-IMP-0084 AAL runtime startup failed: " + failure);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || level.getGameTime() - startedAtTick < START_DELAY_TICKS) {
            return;
        }

        try {
            if (phase.equals("prepare")) {
                runPrepare(event.getServer());
                return;
            }
            if (runReload(event.getServer())) {
                return;
            }
            if (level.getGameTime() - startedAtTick > RELOAD_DEADLINE_TICKS) {
                fail(event.getServer(), "reloaded AAL entity controller did not become available");
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException failure) {
            fail(event.getServer(), "SF-IMP-0084 AAL " + phase + " failed: " + failure);
        }
    }

    private static void runPrepare(MinecraftServer server)
            throws ReflectiveOperationException, IOException {
        BlockEntity origin = placeStation(ORIGIN_STATION_POS, "Skyforge Origin");
        BlockEntity destination = placeStation(DESTINATION_STATION_POS, "Skyforge Destination");

        UUID originNativeId = stationId(origin);
        UUID destinationNativeId = stationId(destination);
        require(!originNativeId.equals(SKYFORGE_ORIGIN_STATION_ID),
                "AAL origin station UUID collapsed into canonical Skyforge station identity");
        require(!destinationNativeId.equals(SKYFORGE_DESTINATION_STATION_ID),
                "AAL destination station UUID collapsed into canonical Skyforge station identity");

        UUID nativeVehicleId = derivedAalId("vehicle", SKYFORGE_VEHICLE_ID);
        ArmorStand vehicle = new ArmorStand(level, VEHICLE_START.x, VEHICLE_START.y, VEHICLE_START.z);
        vehicle.setUUID(nativeVehicleId);
        vehicle.setNoGravity(true);
        require(level.addFreshEntity(vehicle), "could not spawn Stage-2B entity vehicle controller specimen");

        Binding binding = new Binding(originNativeId, destinationNativeId, nativeVehicleId);
        writeBinding(binding);

        Object route = generatedRoute(binding);
        finishRecording(origin, route);
        finishRecording(origin, generatedRoute(binding));
        require(routeId(recordedRoute(origin)).equals(derivedAalId("route", SKYFORGE_ROUTE_ID)),
                "idempotent generated-route reinjection changed the canonical binding");

        Object controllerRef = linkedController(route);
        publicMethod(origin, "linkController", controllerRef.getClass()).invoke(origin, controllerRef);

        PlaybackEvidence playback = startStopPlayback(origin, route);
        require(playback.started(), "AAL playback did not start in prepare");
        require(playback.stopped(), "AAL playback did not stop cleanly in prepare");

        LinkedHashMap<String, Object> evidence = baseEvidence(binding, route);
        evidence.put("phase", "prepare");
        evidence.put("generatedRouteInjected", true);
        evidence.put("idempotentReinjection", true);
        evidence.put("playbackStarted", playback.started());
        evidence.put("playbackStopped", playback.stopped());
        evidence.put("stationPersistenceArmed", true);
        complete(server, evidence);
    }

    private static boolean runReload(MinecraftServer server)
            throws ReflectiveOperationException, IOException {
        Binding binding = readBinding();

        BlockEntity origin = stationAt(ORIGIN_STATION_POS);
        BlockEntity destination = stationAt(DESTINATION_STATION_POS);
        if (origin == null || destination == null) {
            return false;
        }

        require(stationId(origin).equals(binding.originNativeStationId()),
                "origin AAL station UUID changed across restart");
        require(stationId(destination).equals(binding.destinationNativeStationId()),
                "destination AAL station UUID changed across restart");

        Object route = recordedRoute(origin);
        if (route == null) {
            return false;
        }

        require(routeId(route).equals(derivedAalId("route", SKYFORGE_ROUTE_ID)),
                "persisted AAL route id no longer matches deterministic derived reference");
        require(!routeId(route).equals(SKYFORGE_ROUTE_ID),
                "persisted AAL route id collapsed into canonical Skyforge identity");
        require(routeStopStationIds(route).equals(List.of(
                        binding.originNativeStationId(),
                        binding.destinationNativeStationId())),
                "persisted route lost exact native station bindings");

        if (level.getEntity(binding.nativeVehicleId()) == null) {
            return false;
        }

        PlaybackEvidence playback = startStopPlayback(origin, route);
        require(playback.started(), "AAL playback did not restart after world reload");
        require(playback.stopped(), "AAL playback did not stop cleanly after world reload");

        LinkedHashMap<String, Object> evidence = baseEvidence(binding, route);
        evidence.put("phase", "reload");
        evidence.put("freshProcessReload", true);
        evidence.put("routeRestoredFromStationNbt", true);
        evidence.put("sameOriginNativeStationId", true);
        evidence.put("sameDestinationNativeStationId", true);
        evidence.put("sameDerivedRouteId", true);
        evidence.put("sameNativeVehicleId", true);
        evidence.put("playbackRestarted", playback.started());
        evidence.put("playbackRestopped", playback.stopped());
        complete(server, evidence);
        return true;
    }

    private static BlockEntity placeStation(BlockPos pos, String name)
            throws ReflectiveOperationException {
        Block block = requireBlock(AAL_STATION);
        require(level.setBlockAndUpdate(pos, block.defaultBlockState()), "could not place AAL station at " + pos);
        BlockEntity station = stationAt(pos);
        require(station != null, "AAL station block entity missing at " + pos);
        publicMethod(station, "setStationName", String.class).invoke(station, name);
        return station;
    }

    private static BlockEntity stationAt(BlockPos pos) {
        level.getChunkAt(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null
                || !blockEntity.getClass().getName().equals(
                        "net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity")) {
            return null;
        }
        return blockEntity;
    }

    private static UUID stationId(BlockEntity station) throws ReflectiveOperationException {
        return (UUID) publicMethod(station, "stationId").invoke(station);
    }

    private static void finishRecording(BlockEntity station, Object route)
            throws ReflectiveOperationException {
        publicMethod(station, "finishRecording", route.getClass()).invoke(station, route);
    }

    private static Object recordedRoute(BlockEntity station)
            throws ReflectiveOperationException {
        Object value = publicMethod(station, "recordedRoute").invoke(station);
        if (!(value instanceof Optional<?> optional)) {
            throw new IllegalStateException("AAL recordedRoute() did not return Optional");
        }
        return optional.orElse(null);
    }

    private static Object generatedRoute(Binding binding)
            throws ReflectiveOperationException {
        Class<?> routeIdClass = aalClass("route.RouteId");
        Class<?> routePointClass = aalClass("route.RoutePoint");
        Class<?> routeStopClass = aalClass("route.RouteStop");
        Class<?> waitConditionClass = aalClass("route.WaitCondition");
        Class<?> controllerRefClass = aalClass("vehicle.VehicleControllerRef");
        Class<?> entityControllerClass = aalClass("vehicle.EntityVehicleController");
        Class<?> playbackModeClass = aalClass("route.PlaybackMode");
        Class<?> routeStatusClass = aalClass("route.RouteStatus");
        Class<?> transportModeClass = aalClass("route.TransportMode");
        Class<?> routeClass = aalClass("route.Route");

        Object routeId = routeIdClass.getConstructor(UUID.class)
                .newInstance(derivedAalId("route", SKYFORGE_ROUTE_ID));

        ResourceLocation controllerType =
                (ResourceLocation) entityControllerClass.getField("TYPE").get(null);
        Object controllerRef = controllerRefClass
                .getConstructor(ResourceLocation.class, ResourceKey.class, Optional.class, Optional.class)
                .newInstance(
                        controllerType,
                        Level.OVERWORLD,
                        Optional.of(binding.nativeVehicleId()),
                        Optional.empty());

        Method withoutYaw = routePointClass.getMethod(
                "withoutYaw", Vec3.class, long.class, ResourceKey.class);
        Method withYaw = routePointClass.getMethod(
                "withYaw", Vec3.class, float.class, long.class, ResourceKey.class);
        List<Object> points = List.of(
                withoutYaw.invoke(null, VEHICLE_START, 0L, Level.OVERWORLD),
                withYaw.invoke(null, new Vec3(5.5D, 121.0D, 0.5D), 0.0F, 40L, Level.OVERWORLD),
                withYaw.invoke(null, new Vec3(9.5D, 121.0D, 0.5D), 0.0F, 80L, Level.OVERWORLD));

        Object waitNone = waitConditionClass.getMethod("none").invoke(null);
        Constructor<?> stopConstructor = routeStopClass.getConstructor(
                UUID.class,
                String.class,
                int.class,
                waitConditionClass,
                Optional.class,
                Optional.class,
                List.class);
        Object originStop = stopConstructor.newInstance(
                derivedAalId("stop", SKYFORGE_ORIGIN_STOP_ID),
                "skyforge-origin",
                0,
                waitNone,
                Optional.empty(),
                Optional.of(binding.originNativeStationId()),
                List.of());
        Object destinationStop = stopConstructor.newInstance(
                derivedAalId("stop", SKYFORGE_DESTINATION_STOP_ID),
                "skyforge-destination",
                2,
                waitNone,
                Optional.empty(),
                Optional.of(binding.destinationNativeStationId()),
                List.of());

        @SuppressWarnings({"rawtypes", "unchecked"})
        Object playbackMode = Enum.valueOf((Class<? extends Enum>) playbackModeClass, "ONE_WAY");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object routeStatus = Enum.valueOf((Class<? extends Enum>) routeStatusClass, "RECORDED");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object transportMode = Enum.valueOf((Class<? extends Enum>) transportModeClass, "AIRSHIP");

        return routeClass.getConstructor(
                        routeIdClass,
                        String.class,
                        transportModeClass,
                        ResourceKey.class,
                        List.class,
                        controllerRefClass,
                        playbackModeClass,
                        routeStatusClass,
                        List.class,
                        Optional.class)
                .newInstance(
                        routeId,
                        "skyforge-sf-imp-0084-generated-route",
                        transportMode,
                        Level.OVERWORLD,
                        points,
                        controllerRef,
                        playbackMode,
                        routeStatus,
                        List.of(originStop, destinationStop),
                        Optional.empty());
    }

    private static PlaybackEvidence startStopPlayback(BlockEntity station, Object route)
            throws ReflectiveOperationException {
        Class<?> servicesClass = aalClass("service.AutomatedLogisticsServices");
        Class<?> routeIdClass = aalClass("route.RouteId");
        Class<?> failureReasonClass = aalClass("route.FailureReason");
        Object playback = servicesClass.getField("PLAYBACK").get(null);

        Object result = publicMethod(
                        playback,
                        "startPlayback",
                        ServerLevel.class,
                        BlockPos.class,
                        route.getClass())
                .invoke(playback, level, station.getBlockPos(), route);
        Optional<?> value = (Optional<?>) publicMethod(result, "value").invoke(result);
        Optional<?> failure = (Optional<?>) publicMethod(result, "failure").invoke(result);
        require(value.isPresent() && failure.isEmpty(),
                "AAL playback start failed: " + failure.orElse(null));

        Object routeId = value.get();
        require(((UUID) publicMethod(routeId, "value").invoke(routeId)).equals(routeId(route)),
                "playback returned the wrong route id");
        boolean running = (Boolean) publicMethod(playback, "isRunning", routeIdClass)
                .invoke(playback, routeId);
        require(running, "AAL playback did not report running after successful start");

        @SuppressWarnings({"rawtypes", "unchecked"})
        Object none = Enum.valueOf((Class<? extends Enum>) failureReasonClass, "NONE");
        publicMethod(playback, "stopPlayback", ServerLevel.class, routeIdClass, failureReasonClass)
                .invoke(playback, level, routeId, none);
        boolean stopped = !(Boolean) publicMethod(playback, "isRunning", routeIdClass)
                .invoke(playback, routeId);
        return new PlaybackEvidence(running, stopped);
    }

    private record PlaybackEvidence(boolean started, boolean stopped) {}

    private static Object linkedController(Object route) throws ReflectiveOperationException {
        return publicMethod(route, "linkedController").invoke(route);
    }

    private static UUID routeId(Object route) throws ReflectiveOperationException {
        Object routeId = publicMethod(route, "id").invoke(route);
        return (UUID) publicMethod(routeId, "value").invoke(routeId);
    }

    private static List<UUID> routeStopStationIds(Object route)
            throws ReflectiveOperationException {
        Object stopsValue = publicMethod(route, "stops").invoke(route);
        if (!(stopsValue instanceof List<?> stops)) {
            throw new IllegalStateException("AAL Route.stops() did not return List");
        }
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
        for (Object stop : stops) {
            Optional<?> station = (Optional<?>) publicMethod(stop, "stationId").invoke(stop);
            require(station.isPresent() && station.get() instanceof UUID,
                    "generated route stop lost station binding");
            ids.add((UUID) station.get());
        }
        return List.copyOf(ids);
    }

    private static LinkedHashMap<String, Object> baseEvidence(Binding binding, Object route)
            throws ReflectiveOperationException {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("aalVersion", "0.6.2");
        evidence.put("aalLoaded", ModList.get().isLoaded(AAL_MOD_ID));
        evidence.put("canonicalSkyforgeRouteId", SKYFORGE_ROUTE_ID);
        evidence.put("derivedAalRouteId", routeId(route));
        evidence.put("identitySeparated", !SKYFORGE_ROUTE_ID.equals(routeId(route)));
        evidence.put("canonicalOriginStationId", SKYFORGE_ORIGIN_STATION_ID);
        evidence.put("originNativeStationId", binding.originNativeStationId());
        evidence.put("canonicalDestinationStationId", SKYFORGE_DESTINATION_STATION_ID);
        evidence.put("destinationNativeStationId", binding.destinationNativeStationId());
        evidence.put("derivedNativeVehicleId", binding.nativeVehicleId());
        evidence.put("routeStopStationIds", routeStopStationIds(route));
        return evidence;
    }

    private static Class<?> aalClass(String suffix) throws ClassNotFoundException {
        return Class.forName("net.sprocketgames.create_aeronautics_automated_logistics." + suffix);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required AAL block not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static UUID derivedAalId(String kind, UUID canonicalSkyforgeId) {
        String namespace = "skyforge/aal/" + kind + "/" + canonicalSkyforgeId;
        return UUID.nameUUIDFromBytes(namespace.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireMods() {
        for (String modId : List.of("create", "sable", "aeronautics", AAL_MOD_ID)) {
            require(ModList.get().isLoaded(modId), "required SF-IMP-0084 runtime mod not loaded: " + modId);
        }
    }

    private static void writeBinding(Binding binding) throws IOException {
        Files.writeString(
                BINDING_FILE,
                SKYFORGE_ORIGIN_STATION_ID + "=" + binding.originNativeStationId() + "\n"
                        + SKYFORGE_DESTINATION_STATION_ID + "=" + binding.destinationNativeStationId() + "\n"
                        + SKYFORGE_VEHICLE_ID + "=" + binding.nativeVehicleId() + "\n",
                StandardCharsets.UTF_8);
    }

    private static Binding readBinding() throws IOException {
        List<String> lines = Files.readAllLines(BINDING_FILE, StandardCharsets.UTF_8);
        require(lines.size() == 3, "SF-IMP-0084 binding sidecar expected exactly 3 lines");
        return new Binding(
                mappedNativeId(lines.get(0), SKYFORGE_ORIGIN_STATION_ID),
                mappedNativeId(lines.get(1), SKYFORGE_DESTINATION_STATION_ID),
                mappedNativeId(lines.get(2), SKYFORGE_VEHICLE_ID));
    }

    private static UUID mappedNativeId(String line, UUID canonical) {
        String prefix = canonical + "=";
        require(line.startsWith(prefix), "binding sidecar lost canonical identity " + canonical);
        return UUID.fromString(line.substring(prefix.length()).trim());
    }

    private static void complete(MinecraftServer server, LinkedHashMap<String, Object> evidence) {
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(server, evidence);
    }

    private static void fail(MinecraftServer server, String reason) {
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }
}
