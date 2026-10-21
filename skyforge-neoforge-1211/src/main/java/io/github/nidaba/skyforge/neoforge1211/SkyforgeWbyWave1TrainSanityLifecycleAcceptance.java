package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * WBY-INT-0002 train coexistence fixture.
 *
 * <p>Builds a minimal real Create train through Create's StationBlockEntity runtime API while
 * the ordinary WBY distant-Sable fixture remains active. Create stays behind reflection because
 * it is an optional runtime integration dependency, not a compile-time dependency of this source
 * set.
 */
final class SkyforgeWbyWave1TrainSanityLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1TrainSanity";

    private static final ResourceLocation TRACK_ID =
            ResourceLocation.fromNamespaceAndPath("create", "track");
    private static final ResourceLocation STATION_ID =
            ResourceLocation.fromNamespaceAndPath("create", "track_station");
    private static final ResourceLocation BOGEY_ID =
            ResourceLocation.fromNamespaceAndPath("create", "small_bogey");
    private static final ResourceLocation CONTROLS_ID =
            ResourceLocation.fromNamespaceAndPath("create", "controls");

    private static final long SETUP_TIMEOUT_TICKS = 180L;
    private static ServerLevel level;
    private static Object station;
    private static BlockPos stationPos;
    private static BlockPos targetTrackPos;
    private static BlockPos bogeyPos;
    private static UUID trainId;
    private static UUID carriageEntityId;
    private static Set<UUID> trainIdsBefore = Set.of();
    private static long firstPlayerTick = Long.MIN_VALUE;
    private static long lastAssemblyAttemptTick = Long.MIN_VALUE;
    private static boolean fixturePlaced;
    private static boolean fixtureReady;
    private static boolean clientComplete;

    record Snapshot(UUID trainId, UUID carriageEntityId, BlockPos targetTrackPos) {}

    private SkyforgeWbyWave1TrainSanityLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1TrainSanityLifecycleAcceptance::onServerTickPost);
    }

    static boolean fixtureReady() {
        return fixtureReady;
    }

    static Snapshot snapshot() {
        return fixtureReady && trainId != null
                ? new Snapshot(trainId, carriageEntityId, targetTrackPos)
                : null;
    }

    static void markClientComplete() {
        clientComplete = true;
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (clientComplete || fixtureReady) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
        }
        if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        if (firstPlayerTick == Long.MIN_VALUE) {
            firstPlayerTick = now;
        }
        if (now - firstPlayerTick > SETUP_TIMEOUT_TICKS) {
            fail(event, "Create train fixture did not assemble within " + SETUP_TIMEOUT_TICKS
                    + " ticks; station=" + stationPos
                    + "; edgePoint=" + safeEdgePoint()
                    + "; trainIds=" + safeTrainIds()
                    + "; lastException=" + stationException());
            return;
        }

        try {
            requireRuntimePreconditions();
            if (!fixturePlaced) {
                placeFixture(event);
                return;
            }

            if (station == null) {
                BlockEntity blockEntity = level.getBlockEntity(stationPos);
                if (blockEntity == null
                        || !blockEntity.getClass().getName()
                                .equals("com.simibubi.create.content.trains.station.StationBlockEntity")) {
                    return;
                }
                station = blockEntity;
                configureStationTarget(station);
            }

            Object edgePoint = stationEdgePoint();
            Object edge = publicMethod(edgePoint, "getEdgePoint").invoke(edgePoint);
            if (edge == null) {
                publicMethod(edgePoint, "createEdgePoint").invoke(edgePoint);
                edge = publicMethod(edgePoint, "getEdgePoint").invoke(edgePoint);
                if (edge == null) {
                    return;
                }
            }

            boolean assembling = (Boolean) publicMethod(station, "isAssembling").invoke(station);
            if (!assembling) {
                var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
                Object entered = compatibleOneArgMethod(station, "enterAssemblyMode", viewer)
                        .invoke(station, viewer);
                if (!(entered instanceof Boolean ok) || !ok) {
                    return;
                }
            }

            Set<UUID> created = new LinkedHashSet<>(trainIds());
            created.removeAll(trainIdsBefore);
            if (created.isEmpty() && (lastAssemblyAttemptTick == Long.MIN_VALUE
                    || now - lastAssemblyAttemptTick >= 5L)) {
                lastAssemblyAttemptTick = now;
                UUID owner = event.getServer().getPlayerList().getPlayers().getFirst().getUUID();
                publicMethod(station, "assemble", UUID.class).invoke(station, owner);
                created = new LinkedHashSet<>(trainIds());
                created.removeAll(trainIdsBefore);
            }

            if (created.size() > 1) {
                fail(event, "train sanity fixture created multiple trains: " + created);
                return;
            }
            if (created.size() != 1) {
                return;
            }

            trainId = created.iterator().next();
            Entity carriage = findCarriageEntity(trainId);
            if (carriage == null) {
                return;
            }
            carriageEntityId = carriage.getUUID();
            fixtureReady = true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 train fixture failed: " + failure);
        }
    }

    private static void placeFixture(ServerTickEvent.Post event) throws ReflectiveOperationException {
        var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
        BlockPos viewerPos = viewer.blockPosition();
        int y = viewerPos.getY() + 4;
        int z = viewerPos.getZ();
        int startX = viewerPos.getX() + 8;

        targetTrackPos = new BlockPos(startX, y, z);
        stationPos = targetTrackPos.offset(0, 0, -2);
        bogeyPos = targetTrackPos.offset(1, 1, 0);

        for (int x = startX - 3; x <= startX + 26; x++) {
            for (int yy = y; yy <= y + 4; yy++) {
                for (int zz = z - 3; zz <= z + 3; zz++) {
                    level.setBlock(new BlockPos(x, yy, zz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            for (int zz = z - 3; zz <= z + 3; zz++) {
                level.setBlock(new BlockPos(x, y - 1, zz), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockState trackState = withProperty(
                withProperty(requireBlock(TRACK_ID).defaultBlockState(), "shape", "xo"),
                "turn",
                "false");
        for (int x = startX - 2; x <= startX + 24; x++) {
            if (!level.setBlock(new BlockPos(x, y, z), trackState, 3)) {
                throw new IllegalStateException("failed to place Create straight track at x=" + x);
            }
        }

        if (!level.setBlock(stationPos, requireBlock(STATION_ID).defaultBlockState(), 3)) {
            throw new IllegalStateException("failed to place Create train station");
        }

        if (!level.setBlock(bogeyPos, requireBlock(BOGEY_ID).defaultBlockState(), 3)) {
            throw new IllegalStateException("failed to place Create small bogey");
        }

        BlockState controlsState = requireBlock(CONTROLS_ID).defaultBlockState();
        controlsState = withProperty(controlsState, "facing", "east");
        controlsState = withProperty(controlsState, "open", "false");
        controlsState = withProperty(controlsState, "virtual", "false");
        BlockPos controlsPos = bogeyPos.above();
        if (!level.setBlock(controlsPos, controlsState, 3)) {
            throw new IllegalStateException("failed to place Create train controls");
        }
        addFixtureGlue(bogeyPos, controlsPos);

        trainIdsBefore = new LinkedHashSet<>(trainIds());
        fixturePlaced = true;
    }

    private static void configureStationTarget(Object stationBlockEntity)
            throws ReflectiveOperationException {
        Object edgePoint = stationBlockEntity.getClass().getField("edgePoint").get(stationBlockEntity);
        CompoundTag tag = new CompoundTag();
        tag.put("TargetTrack", NbtUtils.writeBlockPos(targetTrackPos.subtract(stationPos)));
        tag.putBoolean("TargetDirection", true);
        tag.putBoolean("Ortho", true);
        Method read = methodByNameAndArity(edgePoint, "read", 3);
        read.invoke(edgePoint, tag, level.registryAccess(), false);
    }

    private static Object stationEdgePoint() throws ReflectiveOperationException {
        return station.getClass().getField("edgePoint").get(station);
    }

    private static Set<UUID> trainIds() throws ReflectiveOperationException {
        Object railways = Class.forName("com.simibubi.create.Create").getField("RAILWAYS").get(null);
        Object trainsValue = railways.getClass().getField("trains").get(railways);
        if (!(trainsValue instanceof Map<?, ?> trains)) {
            throw new IllegalStateException("Create RAILWAYS.trains did not resolve to a Map");
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (Object key : trains.keySet()) {
            if (key instanceof UUID uuid) {
                ids.add(uuid);
            }
        }
        return ids;
    }

    private static Entity findCarriageEntity(UUID expectedTrainId) throws ReflectiveOperationException {
        for (Entity entity : level.getAllEntities()) {
            if (!entity.getClass().getName()
                    .equals("com.simibubi.create.content.trains.entity.CarriageContraptionEntity")) {
                continue;
            }
            Object id = entity.getClass().getField("trainId").get(entity);
            if (expectedTrainId.equals(id) && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add train fixture Super Glue");
        }
    }

    private static void requireRuntimePreconditions() {
        for (String modId : java.util.List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY train-sanity server mod not loaded: " + modId);
            }
        }
        requireBlock(TRACK_ID);
        requireBlock(STATION_ID);
        requireBlock(BOGEY_ID);
        requireBlock(CONTROLS_ID);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required train-sanity block not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return withParsedProperty(state, property, name, value);
            }
        }
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static Object safeEdgePoint() {
        try {
            if (station == null) {
                return null;
            }
            Object edgePoint = stationEdgePoint();
            return publicMethod(edgePoint, "getEdgePoint").invoke(edgePoint);
        } catch (ReflectiveOperationException failure) {
            return "<unavailable:" + failure + ">";
        }
    }

    private static Object stationException() {
        if (station == null) {
            return null;
        }
        try {
            Field field = station.getClass().getDeclaredField("lastException");
            field.setAccessible(true);
            return field.get(station);
        } catch (ReflectiveOperationException ignored) {
            return "<unavailable>";
        }
    }

    private static Object safeTrainIds() {
        try {
            return trainIds();
        } catch (ReflectiveOperationException failure) {
            return "<unavailable:" + failure + ">";
        }
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Method methodByNameAndArity(Object target, String name, int arity)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "/" + arity);
    }

    private static Method compatibleOneArgMethod(Object target, String name, Object argument)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            if (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(compatible arg)");
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
